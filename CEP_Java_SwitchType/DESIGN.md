# SwitchType: design

Written before the code and kept up to date. Every decision that the brief left open is
recorded here (section 10) so that it can be defended in the viva.

## 1. Architecture in one picture

```
             +-------------------- one engine --------------------+
             |  ScanEngine (pure state machine, no threads/clock)  |
             |   ScanStrategy  <- LinearScan / RowColumnScan /      |
             |                    GroupBinaryScan                   |
             |   Key[][] grid  <- KeyboardLayout (Alpha/QWERTY/Freq)|
             +--------------------------+--------------------------+
                                        |
               +------------------------+-------------------------+
               |                                                  |
   real-time driver (live)                          virtual-time driver (sim)
   ScannerThread: wait(timeout)/notifyAll           SimulationDriver: integer ms clock,
   System.nanoTime, priority 8                      no sleeping, seeded Random
   -> SwingUtilities.invokeLater                    -> SimulatedUser presses
   -> SessionController (EDT)                       -> Metrics, ExperimentRunner
               |                                                  |
               +------------- TextComposer + WordPredictor -------+
                        (shared text/prediction logic as well)
```

The live app and the experiments run **the same** `ScanEngine`, strategies, layouts,
`TextComposer` and predictors. Only the driver differs. `LiveSimEquivalenceTest` checks it.

## 2. Packages and classes

| Package | Classes | Concern |
|---|---|---|
| `model` | `Key`, `KeyAction`, `ScanStyle`, `LayoutType`, `PredictionMode`, `SwitchChoice`, `Settings`, `UserProfile`, `Outcome`, `Config` | plain data, enums, constants |
| `scan` | `ScanEngine`, `ScanStrategy` (abstract), `LinearScan`, `RowColumnScan`, `GroupBinaryScan`, `KeyboardLayout` (abstract), `AlphabeticalLayout`, `QwertyLayout`, `FrequencyLayout`, `SettingsKeyboard`, `AdaptiveController`, `ScanFactory` | the scanning engine |
| `predict` | `WordPredictor` (interface), `NoPredictor`, `UnigramPredictor`, `BigramPredictor`, `LanguageModel`, `CharFrequency`, `CorpusLoader`, `TextComposer` | language model and text |
| `sim` | `UserModel` (interface), `SimulatedUser`, `UserParams`, `TypingTask`, `PhraseBank`, `SimulationDriver`, `Metrics`, `RunResult`, `ExperimentRunner`, `Experiments` (console main) | simulation and experiments |
| `live` | `ScannerThread`, `ScanListener`, `TimingStats`, `SessionController`, `DbWorker`, `Recommender` | real-time operation |
| `db` | `DbConfig`, `Database`, `SqlScript`, `SchemaInstaller`, `ProfileRepository`, `MySqlProfileRepository`, `FileProfileRepository`, `SessionRepository`, `MySqlSessionRepository`, `LocalSessionRepository`, `ExperimentDao`, `SessionSummary` | JDBC, fallbacks |
| `io` | `SafeObjectInput`, `ModelStore`, `CsvWriter` | files and serialization |
| `exception` | `SwitchTypeException` (base), `InvalidSettingsException`, `CorpusFormatException`, `ProfileStoreException` | Unit II own exceptions |
| `ui` | `MainFrame`, `KeyboardView`, `KeyboardPanel`, `CalibrationPanel`, `HistoryPanel`, `HistoryChart`, `ProfilesPanel`, `DataPanel`, `SettingsDialog`, `DbSettingsDialog`, `Theme`, `AudioCue`, `ScreenshotTour` | Swing |

## 3. Keyboard grid

The grid is a jagged `Key[][]` built by `KeyboardLayout.buildGrid(boolean predictionRow)`:

1. prediction row (4 slots), only when prediction is on; scanned first;
2. the layout's text rows (26 letters, space, `.`, `,`, `?`, newline = 31 keys);
3. edit row: `Backspace`, `Undo word`, `Pause`, `Settings` (same in every layout so
   corrections cost the same structure everywhere);
4. digits row `1..9 0` (rarely used, so last).

Text rows per layout:

* **Alphabetical**: 8 columns, `space a b c d e f g / h..o / p..w / x y z . , ? newline`.
* **QWERTY**: `q..p / a..l / z..m , . ? / space newline` (familiar, space at the bottom).
* **Frequency**: the 31 characters sorted by corpus frequency and placed on a diagonal
  (cell order by `row+col`, then row) so frequent characters are cheap in row-column scan.

The settings keyboard (`SettingsKeyboard`) is a second grid of control keys
(slower, faster, next style, next layout, next prediction, adaptive on/off, contrast,
start/stop practice, back). It is scanned by the same engine, so the whole app works with
the switch alone (WCAG 2.1.1).

## 4. Scan strategies and analytic step counts

Time is counted in *steps* (highlight moves). The highlight starts on the first item right
after a reset; the user presses while the target is highlighted.

| Strategy | What a step does | Steps to select key | Presses |
|---|---|---|---|
| Linear | next key in row-major order | `i` = position in order | 1 |
| Row-column | next row; after a press, next key in row | `r + c` | 2 |
| Group-binary | show group A, then group B, then A ... | number of "B" (right) branches on the path | depth |

Row-column and group-binary give up after `rowCycles` full cycles without a press and
return to the top. Group-binary builds a Huffman tree from key weights (character
frequencies from the corpus; fixed assumed weights for non-text keys, section 10).

Dwell time of an item = `interval`, or `interval * (1 + extra/100)` for the first item of
a row/group/order. Analytic expected steps per character for a text = the mean of the
per-key formula over the characters of the text; the zero-noise simulation must match it
within 1 % (it matches exactly because both are integer counts).

## 5. Threads, priorities, shared data, lock order

| Thread | Priority | Job |
|---|---|---|
| Event dispatch thread (Swing) | 6 (default) | painting, key events, `TextComposer`, `SessionController` (EDT-confined) |
| `ScannerThread` | 8 (`NORM_PRIORITY + 3`) | owns the `ScanEngine`, times the steps |
| `DbWorker` | 4 (`NORM_PRIORITY - 1`) | all database and file I/O, producer-consumer queue |
| Experiment busy-load threads (exp 9 only) | 5 | create CPU load |

Priority choice: the scanner is the only time-critical thread (a late step makes the user
miss the key), but it sleeps almost all the time, so a high priority costs nothing. It is
not `MAX_PRIORITY` so the EDT and OS input handling are never starved. Experiment 9
measures whether priority makes a difference (on desktop JVMs it often does not).

Shared data:

* `ScannerThread` monitor guards: engine, `pressPending`, `settingsChanged`, `running`.
  Swing calls `press()`, `setInterval()`, `changeStrategy()`, `shutdown()`
  (synchronized + `notifyAll`). The scanner waits with `wait(timeout)` in a `while` loop
  that re-checks every condition.
* `DbWorker` monitor guards its `LinkedList` task queue (`submit` = `notifyAll`).
* The scanner passes **immutable snapshots** (`List<Key>` copies, `Outcome`) to the EDT with
  `invokeLater`; it never touches Swing objects.

**Lock order**: a thread holds at most one of these monitors at a time. Never call
`DbWorker.submit` while holding the scanner lock (the scanner never calls it; only the EDT
does). No database or file I/O runs on the EDT; it is all submitted to `DbWorker`.
No `sleep` is used for correctness anywhere (only `wait(timeout)` for timing).

Shutdown: `ScannerThread.shutdown()` sets `running=false`, `notifyAll`, `interrupt()`, then
`join`. `DbWorker` drains its queue then stops (same pattern).

## 6. Database (MySQL 8, optional)

Tables: `user_profile`, `typing_session` (FK `profile_id` ON DELETE CASCADE),
`selection_event` (FK `session_id` ON DELETE CASCADE, opt-in only), `experiment_run`,
`experiment_result` (FK `run_id` ON DELETE CASCADE), `recommended_settings`.
ENUM columns for style, layout, prediction, switch, session mode, profile type.

Procedures (called with `CallableStatement`):

* `sp_start_session(IN profile, IN mode, OUT id)`
* `sp_finish_session(IN id, IN counts..., OUT cpm, OUT error_rate, OUT presses_per_char)`
  computes the metrics inside MySQL
* `sp_recommend_settings(IN reaction_mean, IN reaction_sd)` returns the row of the closest
  user type (the calibration wizard calls it)
* `sp_purge_events(IN retention_days, OUT deleted)`
* `sp_delete_user_data(IN profile)` deletes events, sessions and the profile in one transaction

Offline fallback: `FileProfileRepository` (serialized profiles with an allow-list) and
`LocalSessionRepository` (summaries in `data/sessions.csv`). `SchemaInstaller` runs the
bundled script on first connect. Experiment results are batch-inserted in one transaction.

## 7. Simulated user (all parameters are assumptions, not measurements)

For each press opportunity:

* fatigue factor `f = 1 + fatiguePct/100 * pressesSinceRest / 100`
* reaction `r ~ Normal(mean * f, sd)`, redrawn until `r >= 150 ms`
* delay `d = max(0, r * (1 - compensation) + j)`, `j ~ Normal(0, jitter)`
* when the highlight **arrives** on an item that contains the intended key at time `a`
  (and the user is ready), the user misses this arrival with probability `miss * f`,
  otherwise presses at `a + d`. **The press lands on whatever is highlighted at `a + d`.**
  If `d` is longer than the dwell, it lands on a later item: a wrong selection.
* at every step, an accidental press with probability `spurious * f` at a uniform time in
  that dwell.
* think time `think` ms before engaging at the start of each word and after a noticed error.
* a wrong selection is noticed with probability `detect`. Noticed: the user corrects with
  Backspace (or Undo word when 3+ wrong characters), which must be scanned too. Not noticed:
  the user carries on, the error stays in the text.
* intent: if a prediction slot shows the rest of the current word, the user selects it
  (assumed always noticed), otherwise the next character.

Profiles (file `experiments/profiles.properties`, copy in resources):

| Profile | Reaction mean/sd | Jitter | Miss | Spurious | Comp. | Fatigue/100 | Think* | Detect* |
|---|---|---|---|---|---|---|---|---|
| Quick | 350/60 | 40 | 1% | 0.2% | 0.6 | 1% | 600 | 0.95 |
| Typical | 500/100 | 60 | 2% | 0.5% | 0.5 | 2% | 1000 | 0.90 |
| Slow | 800/180 | 100 | 4% | 0.5% | 0.4 | 3% | 1500 | 0.85 |
| Tremor | 600/120 | 220 | 5% | 3% | 0.4 | 3% | 1000 | 0.85 |
| Fatigue-prone | 600/100 | 80 | 2% | 0.5% | 0.5 | 8% | 1000 | 0.90 |

\* think time and detect probability were not given in the brief; added as assumptions.

## 8. Metrics

* `CPM = max(0, |target| - L(final, target)) / minutes`, `WPM = CPM / 5`, where `L` is the
  Levenshtein distance and minutes is from the first step to the last selection.
* uncorrected error rate `= L(final, target) / max(|final|, |target|)`
* total error rate `= wrong selections / all selections` (a wrong selection is one whose
  effect differs from what the user intended, corrected or not; corrections themselves are
  intended selections)
* presses per character `= all switch presses / |target|`; steps per character likewise
* keystroke savings `= 1 - selections / |target|` for a perfect (zero-noise) user, compared
  between prediction modes
* fatigue effect = CPM in the last quarter / CPM in the first quarter of a 30 min session

## 9. Experiment plan

Typing task: 60 original phrases, split deterministically (even index = test, odd = train).
Every experiment types the 30 **test** phrases. The language model is trained on the corpus
(about 300 original sentences) plus the 30 **train** phrases; learning is off during
experiments. A test checks that no test phrase occurs in the training text.

Seeds `1..5` for every configuration (same seeds across compared options). Fatigue is off in
experiments 1, 2, 3, 4, 5 and 7 so they measure one effect at a time; it is the subject of 6.

1. interval sweep 200..2000 step 100, 5 profiles x 3 styles x 3 layouts, prediction off
2. best setting per profile with total error rate <= 5 % (from 1) + Pareto front
3. styles x layouts at each profile's best interval (from 1)
4. prediction off/unigram/bigram at best setting; keystroke savings, top-1/3/5 hit rates
5. adaptive (start 1000 ms, and start too fast / too slow) vs best fixed vs bad fixed
6. 30 min sessions with fatigue, CPM per minute, with and without a rest policy
7. sensitivity: each parameter x0.8 and x1.2, then all together; which conclusions survive
8. model validation: zero-noise simulation vs analytic formula; sanity checks
9. real-time accuracy of `ScannerThread`: 60 s at 200/500/1000 ms, idle and under load,
   priorities 1/5/10; mean and p99 of step lateness

Outputs: `experiments/*.csv`, charts by `experiments/make_charts.py` (matplotlib).

## 10. Decisions made without asking

* Package root `com.switchtype`; Java 8 source; built and tested with JDK 21.
* Prediction row has **4** slots (brief: 3 to 5). Empty slots are still scanned (fixed
  structure); selecting one does nothing and counts as a wrong selection.
* Edit row after the text rows, digits last, same for every layout.
* Text is lower case only (the switch user does not need a shift key for the task).
* Group-binary weights for non-text keys (assumptions): backspace 2 %, undo word 1 %,
  each prediction slot 3 %, every other key 0.2 % of total character weight.
* "Correct selection" for adaptive speed: a selection is judged when the **next** one
  arrives. If the next selection is Backspace or Undo word, the previous one was an error
  (slow down x1.2, streak reset). Otherwise it was correct (streak +1; at 5: speed up x0.9).
  A row/group time-out (the user let all cycles pass) also counts as an error signal.
  Corrections themselves are never judged. Bounds 200..2000 ms.
* Rest policy (experiment 6): 2 minutes break after every 10 minutes, assumed to reset
  fatigue fully (assumption).
* Pressing while paused resumes. Selecting Pause pauses.
* Screenshots: Windows has no Xvfb; the tour paints the real frame into an image with
  `printAll` and drives it with `java.awt.Robot` / key events.
* No remote is configured for this folder, so "push" is not possible; commits are local.

## 11. Test plan

Unit tests for every strategy (each key's steps vs formula), layout, predictor, the
composer, adaptive controller, settings validation, serialization allow-list, SQL parser,
metrics; simulation validation (zero noise within 1 %, long interval -> no errors, short
interval -> many errors, seed reproducibility, perfect user in every combination);
`ScannerThreadTest` (multithreaded); WCAG contrast, target size and no-flashing tests;
`MySqlIntegrationTest` against `switchtype_test` (skips when MySQL is down).
