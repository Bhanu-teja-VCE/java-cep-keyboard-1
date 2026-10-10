<div align="center">

# SwitchType: Assistive Single-Switch Scanning Keyboard & Simulation Framework

[![Java](https://img.shields.io/badge/Java-8%20%2F%2021-orange.svg)](https://www.oracle.com/java/)
[![Maven](https://img.shields.io/badge/Build-Maven-blue.svg)](https://maven.apache.org/)
[![Tests](https://img.shields.io/badge/Tests-48%20Passed-brightgreen.svg)]()
[![Status](https://img.shields.io/badge/Status-Complete%20%26%20Verified-success.svg)]()

### 🎓 Complex Engineering Problem (CEP) — Java Programming

[![📖 Read Engineering Blog](https://img.shields.io/badge/📖%20Read%20Engineering%20Blog-Interactive%20Technical%20Deep%20Dive-blue?style=for-the-badge&logo=read-the-docs)](blog.html)
<br><br>

👉 **[Click Here to Open the Full Interactive Engineering Blog Post (`blog.html`)](blog.html)** 👈  
*(Comprehensive breakdown of all system components, Java concepts implementation matrix, and clinical experiments for the course evaluator / lecturer)*

</div>

---

## 🌟 Executive Summary

Traditional computer interaction assumes high motor dexterity (mouse and multi-key keyboards). For individuals with severe neuromuscular disorders (e.g., ALS, cerebral palsy, spinal cord injuries), the primary input mechanism is often a **single binary switch** (a physical pushbutton, puff-and-sip switch, or electromyographic sensor).

**SwitchType** solves this complex engineering problem by delivering:
1. **Mathematical Scan Strategies**: Linear, Row-Column, and Frequency-weighted Group-Binary (Huffman-partitioned) scanning.
2. **Predictive Language Modeling**: Built-in Unigram and Bigram natural language predictors delivering **~41% keystroke savings**.
3. **Adaptive Dwell Speed**: Real-time closed-loop tuning of scan speed based on user success streaks and error detection.
4. **Decoupled Concurrency Architecture**: A dedicated `ScannerThread` (Priority 8) and `DbWorker` (Priority 4) communicating with the Swing Event Dispatch Thread (EDT) via immutable snapshots to guarantee zero lag and UI safety.
5. **Dual Data Persistence**: Full MySQL 8 relational schema with stored procedures, paired with an automatic zero-config offline fallback (`FileProfileRepository` and CSV logging).
6. **9 Empirical Simulation Experiments**: Modeling 5 clinical user profiles (*Quick, Typical, Slow, Tremor, Fatigue-prone*) to identify optimal Pareto frontiers.

---

## 🚀 Quick Start: How to Run ("Host") the App

The project is **fully compiled, tested, and packaged into a self-contained Fat JAR**.

### Option 1: Run Pre-Built Fat JAR (Fastest & Simplest)
Double-click `CEP_Java_SwitchType/app/target/SwitchType.jar` in Windows Explorer, or run in terminal:
```powershell
java -jar "CEP_Java_SwitchType/app/target/SwitchType.jar"
```
*(Runs directly on any system with Java installed. MySQL is completely optional; if offline, it seamlessly uses local storage.)*

### Option 2: Run Using Maven
From the `CEP_Java_SwitchType/app` directory:
```powershell
mvn exec:java
```

### Option 3: Run from IntelliJ IDEA / Eclipse
1. Open IntelliJ IDEA and choose **Open**, selecting `CEP_Java_SwitchType/app/pom.xml`.
2. Locate `com.switchtype.Main` in `src/main/java`.
3. Right-click and choose **Run 'Main.main()'**.

### Option 4: Run the Experiment Benchmark Suite
To run simulations or reproduce experimental metrics:
```powershell
# Run validation tests (Experiment 8)
java -cp "CEP_Java_SwitchType/app/target/SwitchType.jar" com.switchtype.sim.Experiments 8

# Run all 9 experiments (writes CSVs to experiments/)
java -cp "CEP_Java_SwitchType/app/target/SwitchType.jar" com.switchtype.sim.Experiments CEP_Java_SwitchType/experiments all
```

---

## 🏗️ System Architecture & Java Course Mapping

| Java Course Topic / Unit | Applied Component in SwitchType | Concrete Implementation |
|---|---|---|
| **OOP: Abstraction & Interfaces** | `ScanStrategy`, `WordPredictor`, `UserModel`, `ProfileRepository` | Decouples UI and algorithms from concrete scanning and storage implementations. |
| **OOP: Encapsulation & Immutability** | `Key`, `Outcome`, `KeyAction`, `Settings` | Private fields, defensive copying across thread boundaries, immutable outcome snapshots. |
| **OOP: Inheritance & Polymorphism** | `LinearScan`, `RowColumnScan`, `GroupBinaryScan` extending `ScanStrategy` | Dynamic method dispatch during scan cycles allowing runtime strategy swapping. |
| **Design Patterns** | State Machine, Strategy, Factory, Observer, Producer-Consumer | `ScanEngine` (state machine), `ScanFactory`, `ScanListener` (events), `DbWorker` (queue). |
| **Exception Handling** | Custom Domain Hierarchy | Base `SwitchTypeException`, `InvalidSettingsException`, `CorpusFormatException`, `ProfileStoreException`. |
| **Multithreading & Concurrency** | Dedicated Priorities & EDT Safety | `ScannerThread` (Priority 8, `wait/notifyAll` timing loop), `DbWorker` (Priority 4, producer-consumer queue), Swing EDT event confinement. |
| **Collections & Data Structures** | Huffman Trees, Jagged Arrays, Frequency Maps | Jagged 2D grid (`Key[][]`), Character frequency tables (`CharFrequency`), Unigram/Bigram dictionaries (`LanguageModel`). |
| **File I/O & Serialization** | Safe Deserialization & CSV Pipelines | Object serialization with class allow-list (`SafeObjectInput`), CSV import/export (`CsvWriter`, `ModelStore`). |
| **JDBC & Relational Databases** | Stored Procedures & Transactions | `Database.java`, `MySqlProfileRepository`, `MySqlSessionRepository`, schema installation, `CallableStatement` calls. |
| **GUI & Accessibility (Swing)** | WCAG 2.1.1 Compliant Interface | Single-switch accessible settings (`SettingsKeyboard`), High-contrast mode, dynamic font/key scaling, auditory cues (`AudioCue`). |

---

## 🎯 Step-by-Step Viva & Presentation Walkthrough

Follow this 5-minute demonstration script when presenting to your teacher / evaluator:

### Step 1: Introduce the Real-World Problem (30 seconds)
> *"Mam, our Complex Engineering Problem addresses assistive accessibility. Individuals with quadriplegia or ALS cannot use regular keyboards or mice. They rely on an accessible single switch (like a head switch or puff switch). Our project, SwitchType, maximizes typing speed (Words Per Minute) while minimizing cognitive fatigue and error rates."*

### Step 2: Show the Live Keyboard & Switch Operation (1.5 minutes)
1. Launch the app (`java -jar CEP_Java_SwitchType/app/target/SwitchType.jar`).
2. Point out the top status bar showing: Profile, Scan Strategy, Layout, Dwell Interval, and Database status.
3. Tap the **Spacebar** (the configured single switch) to demonstrate typing:
   - In **Row-Column Scanning**, press 1 selects the row; press 2 selects the letter.
   - Point out the **Prediction Row** at the top. After pressing a letter, 4 word completions appear in high-visibility blue buttons.
   - Select a predicted word with the switch to show that it finishes the word and adds a space automatically, saving multiple switch presses.

### Step 3: Switch Layouts & Strategies (1 minute)
1. Open **Settings** (or select the `settings` key on the grid using the switch!).
2. Switch from **Alphabetical** to **Frequency Layout**:
   - Explain: *"In frequency layout, the most common English characters (E, T, A, O, I, N) are placed at the top-left diagonals where row-column scan reaches them in the fewest possible steps."*
3. Switch Strategy to **Group-Binary Scan**:
   - Explain: *"This uses a Huffman tree based on letter probabilities. The keyboard is split into halves iteratively, achieving \(O(\log N)\) selection depth."*

### Step 4: Demonstrate Calibration Wizard & Adaptive Timing (1 minute)
1. Click the **Calibrate** tab.
2. Hit **Start Calibration**. The app presents 5 reaction prompts. Press the switch when the box changes color.
3. The wizard calculates your mean reaction time and standard deviation, checks statistical thresholds, and automatically configures the optimal dwell interval and profile.
4. Explain the **Adaptive Scan Engine**:
   - *"If a user is typing smoothly (5 consecutive correct selections), the system dynamically speeds up dwell time by 10%. If an error or timeout occurs, it backs off by 20% to prevent cascading mistakes."*

### Step 5: Highlight Concurrency & Database Robustness (1 minute)
1. Open the **History** and **Data & Privacy** tabs.
2. Explain:
   - *"All database and file operations run on a dedicated background worker (`DbWorker`, Priority 4), completely decoupled from the Swing UI to guarantee that the UI never freezes."*
   - *"The scanning clock is driven by `ScannerThread` at Priority 8 using `wait(timeout)` loops rather than imprecise `sleep()`. Immutable state snapshots are handed to Swing via `SwingUtilities.invokeLater`."*
   - *"The system works seamlessly offline with CSV and serialized file backups, but can effortlessly connect to MySQL 8 with stored procedures and transactions when connected."*

---

## 💡 Top Viva Questions & Confident Answers

**Q1: Why did you use `wait(timeout)` and `notifyAll()` instead of `Thread.sleep()` in the scanner?**  
> **Answer:** `Thread.sleep()` holds the thread inert for the entire duration and cannot be promptly awakened when the user presses the switch or pauses. `wait(timeout)` inside a synchronized monitor loop allows immediate reaction to user switch presses while still keeping accurate interval timing without busy-waiting.

**Q2: How is thread safety ensured between the Scanner and the Swing GUI?**  
> **Answer:** We adhere to strict lock hierarchies and thread confinement. The `ScanEngine` is guarded by the `ScannerThread` monitor. When sending state updates to Swing, the scanner passes **immutable copies** (`List<Key>` snapshots) via `SwingUtilities.invokeLater`. Heavy I/O is dispatched to `DbWorker` so the Event Dispatch Thread (EDT) is never blocked.

**Q3: How does the word prediction model work?**  
> **Answer:** We implemented a language model with Unigram (single word frequencies) and Bigram (conditional word transitions \(P(W_n \mid W_{n-1})\)) trained on a comprehensive linguistic corpus. Test phrases were strictly held out to ensure unbiased evaluation. Bigram prediction delivers over 40% keystroke savings.

**Q4: What happens if MySQL is down during the evaluation?**  
> **Answer:** SwitchType is designed with an autonomous **Offline Fallback Architecture**. `AppContext` initializes `FileProfileRepository` and `LocalSessionRepository` by default. If MySQL is unreachable, all profiles and session histories persist locally to `data/` without any crashes or unhandled exceptions.

**Q5: What are the experimental findings of your project?**  
> **Answer:** We evaluated 5 simulated clinical profiles over 9 experiments. We discovered that **Frequency Layout with Row-Column scanning** achieves the ideal Pareto optimal frontier between typing speed (25-46 CPM) and low error rate (<1%). For users with severe tremors or high jitter, **Group-Binary scanning** provides superior stability.

---

## 📁 Repository Directory Structure

```
├── README.md                   # Main course documentation & presentation guide
├── blog.html / index.html      # Interactive technical deep dive blog post
├── CEP_Java_SwitchType/
│   ├── app/
│   │   ├── pom.xml             # Maven build configuration (Java 8/21, shaded Fat JAR)
│   │   ├── db.properties       # MySQL connection settings
│   │   ├── src/main/java/      # 79 Java source files across 8 packages
│   │   │   └── com/switchtype/
│   │   │       ├── model/      # Entities, Enums, Settings, Profile
│   │   │       ├── scan/       # ScanEngine, Strategies, Layouts, AdaptiveController
│   │   │       ├── predict/    # LanguageModel, Unigram, Bigram, TextComposer
│   │   │       ├── live/       # ScannerThread, DbWorker, TimingStats, SessionController
│   │   │       ├── sim/        # SimulatedUser, ExperimentRunner, SimulationDriver
│   │   │       ├── db/         # JDBC Repositories, SchemaInstaller, Fallbacks
│   │   │       ├── io/         # SafeObjectInput, ModelStore, CsvWriter
│   │   │       ├── exception/  # Custom exception hierarchy
│   │   │       └── ui/         # Swing GUI (MainFrame, KeyboardView, Calibration, Theme)
│   │   ├── src/test/java/      # 13 JUnit 5 test classes (48 unit/integration tests)
│   │   ├── src/main/resources/ # SQL scripts, text corpus, default profiles
│   │   └── target/SwitchType.jar # Pre-packaged executable Shaded Fat JAR (4.71 MB)
│   ├── experiments/            # Benchmark outputs: CSV data & Pareto frontier charts
│   │   ├── exp1_interval_sweep.png
│   │   ├── exp2_pareto.png
│   │   ├── exp3_styles_layouts.png
│   │   ├── exp4_prediction.png
│   │   ├── exp5_adaptive_trace.png
│   │   ├── exp6_fatigue.png
│   │   └── results_tables.md   # Markdown tables of experimental results
│   ├── screenshots/            # Application UI screenshots
│   │   └── 01-keyboard-row-column.png
│   └── DESIGN.md               # Technical architectural design record
```

---
*Developed for the Complex Engineering Problem (CEP) Coursework.*
