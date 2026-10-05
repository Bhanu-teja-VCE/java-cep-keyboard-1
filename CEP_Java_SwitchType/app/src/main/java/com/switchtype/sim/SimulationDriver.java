package com.switchtype.sim;

import com.switchtype.model.Key;
import com.switchtype.model.KeyAction;
import com.switchtype.model.Outcome;
import com.switchtype.model.PredictionMode;
import com.switchtype.model.Settings;
import com.switchtype.predict.CharFrequency;
import com.switchtype.predict.TextComposer;
import com.switchtype.predict.WordPredictor;
import com.switchtype.scan.AdaptiveController;
import com.switchtype.scan.KeyboardLayout;
import com.switchtype.scan.ScanEngine;
import com.switchtype.scan.ScanFactory;

import java.util.List;

/**
 * The virtual-time driver. It runs the SAME ScanEngine as the live app, but instead of
 * waiting it jumps a virtual clock from event to event (next step, planned press, end of
 * thinking). Nothing sleeps, so thousands of sessions take seconds, and with the same seed
 * the result is identical.
 */
public class SimulationDriver {

    /** A phrase that takes longer than this is abandoned and counted as unfinished. */
    public static final long MAX_PHRASE_MS = 10 * 60 * 1000L;
    private static final long NONE = Long.MAX_VALUE;

    private final ScanEngine engine;
    private final Key[][] grid;
    private final PredictionMode predictionMode;
    private final WordPredictor predictor;
    private final UserModel user;
    private final AdaptiveController adaptive;
    private final Key backspace;
    private final Key undoWord;
    private final Key[] predictionKeys;
    private final RunResult result = new RunResult();

    private long now;
    private long nextStepAt;
    private long pressAt = NONE;
    private long readyAt;

    public SimulationDriver(Settings settings, CharFrequency frequency, WordPredictor predictor, UserModel user) {
        this.engine = ScanFactory.engine(settings, frequency);
        this.grid = engine.getStrategy().getGrid();
        this.predictionMode = settings.getPrediction();
        this.predictor = predictor;
        this.user = user;
        this.adaptive = settings.isAdaptive() ? new AdaptiveController(settings.getIntervalMs()) : null;
        this.backspace = KeyboardLayout.find(grid, KeyAction.BACKSPACE, 0);
        this.undoWord = KeyboardLayout.find(grid, KeyAction.UNDO_WORD, 0);
        this.predictionKeys = new Key[com.switchtype.model.Config.PREDICTION_SLOTS];
        for (int i = 0; i < predictionKeys.length; i++) {
            predictionKeys[i] = KeyboardLayout.find(grid, KeyAction.PREDICTION, i);
        }
        result.intervalTrace.add(new long[]{0, settings.getIntervalMs()});
    }

    /** Types every phrase once, one after the other. */
    public RunResult typePhrases(List<String> phrases) {
        for (String phrase : phrases) {
            typePhrase(phrase);
        }
        return result;
    }

    /**
     * Types phrases in a loop until the virtual clock passes durationMs.
     * If restEveryMs > 0, a break of restMs follows the first phrase that ends after each
     * restEveryMs of work; the break resets fatigue and counts as session time.
     */
    public RunResult typeForDuration(List<String> phrases, long durationMs, long restEveryMs, long restMs) {
        long lastRest = 0;
        int i = 0;
        while (now < durationMs) {
            typePhrase(phrases.get(i % phrases.size()));
            i++;
            if (restEveryMs > 0 && now - lastRest >= restEveryMs && now < durationMs) {
                now += restMs;
                user.rest();
                lastRest = now;
            }
        }
        return result;
    }

    /** Types one phrase with the simulated user; see DESIGN.md section 7 for the rules. */
    private void typePhrase(String target) {
        TextComposer composer = new TextComposer(predictor, false);
        String goal = target;
        long start = now;
        engine.start();
        nextStepAt = now + engine.nextDelayMs();
        pressAt = NONE;
        readyAt = now + user.thinkTimeMs();
        Key intended = intent(composer, goal);
        considerPress(intended);                     // the first item is highlighted already

        while (intended != null && now - start < MAX_PHRASE_MS) {
            if (pressAt != NONE && pressAt < nextStepAt) {
                // the press lands on whatever is highlighted now
                now = pressAt;
                pressAt = NONE;
                Outcome outcome = engine.onPress();
                user.countPress();
                result.presses++;
                if (outcome.isSelection()) {
                    goal = handleSelection(outcome.getKey(), intended, composer, goal);
                    intended = intent(composer, goal);
                }
                nextStepAt = now + engine.nextDelayMs();
                considerPress(intended);
            } else if (readyAt > now && readyAt < nextStepAt) {
                now = readyAt;                       // the person has finished thinking
                considerPress(intended);
            } else {
                now = nextStepAt;
                boolean gaveUp = engine.onStep();
                if (!engine.isPaused()) {
                    result.steps++;
                }
                if (gaveUp && adaptive != null) {
                    setInterval(adaptive.onGiveUp());
                }
                int dwell = engine.nextDelayMs();
                nextStepAt = now + dwell;
                if (pressAt == NONE && !engine.isPaused() && user.slipsThisStep()) {
                    pressAt = now + user.slipOffsetMs(dwell);
                }
                considerPress(intended);
            }
        }

        String finalText = composer.getText();
        int distance = Metrics.levenshtein(finalText, target);
        result.phrases++;
        if (intended != null) {
            result.unfinished++;
        }
        result.timeMs += now - start;
        result.targetChars += target.length();
        result.editDistance += distance;
        result.maxLengthSum += Math.max(finalText.length(), target.length());
        int correct = Math.max(0, target.length() - distance);
        result.correctChars += correct;
        result.phraseLog.add(new long[]{now, correct});
    }

    /**
     * The highlight has just arrived somewhere (or the person just became ready): if the
     * wanted key is inside the highlighted item, plan a press after the person's delay.
     */
    private void considerPress(Key intended) {
        if (intended == null || pressAt != NONE || now < readyAt) {
            return;
        }
        if (engine.isPaused() || engine.isHighlighted(intended)) {
            int delay = user.pressDelayMs();
            if (delay >= 0) {
                pressAt = now + delay;
            }
        }
    }

    /**
     * Applies a selected key and decides what the person believes about the text now.
     *
     * @return the (possibly updated) goal text the person is working towards
     */
    private String handleSelection(Key key, Key intended, TextComposer composer, String goal) {
        String before = composer.getText();
        boolean wasOnTrack = goal.startsWith(before);
        composer.apply(key);
        result.selections++;
        if (adaptive != null) {
            setInterval(adaptive.onSelection(key));
        }
        String after = composer.getText();
        boolean helpful = after.length() > before.length() && goal.startsWith(after);
        if (key == intended || (wasOnTrack && helpful)) {
            if (helpful && after.endsWith(" ")) {
                readyAt = now + user.thinkTimeMs();  // a new word starts
            }
            return goal;
        }
        result.wrongSelections++;
        if (after.equals(before)) {
            return goal;                             // nothing typed (empty slot, settings ...)
        }
        if (!wasOnTrack || user.noticesError()) {
            readyAt = now + user.thinkTimeMs();      // noticed: think, then correct
            return goal;
        }
        // not noticed: the person believes the intended text was typed and carries on
        return after + goal.substring(unitEnd(intended, before, goal));
    }

    /** Index in goal just after the text the intended key would have produced. */
    private static int unitEnd(Key intended, String before, String goal) {
        if (intended.getAction() == KeyAction.PREDICTION) {
            int i = before.length();
            while (i < goal.length() && isWordChar(goal.charAt(i))) {
                i++;
            }
            return Math.min(goal.length(), i + 1);   // the word and its space
        }
        return before.length() + 1;
    }

    /** The key the person wants next, or null when the phrase is complete. */
    private Key intent(TextComposer composer, String goal) {
        String typed = composer.getText();
        if (goal.startsWith(typed)) {
            if (typed.length() == goal.length()) {
                return null;
            }
            Key prediction = usefulPrediction(composer, typed, goal);
            if (prediction != null) {
                return prediction;
            }
            char c = goal.charAt(typed.length());
            Key k = KeyboardLayout.findChar(grid, c);
            if (k == null) {
                throw new IllegalStateException("The keyboard cannot type '" + c + "'");
            }
            return k;
        }
        int common = 0;
        while (common < typed.length() && common < goal.length() && typed.charAt(common) == goal.charAt(common)) {
            common++;
        }
        return (typed.length() - common >= 3) ? undoWord : backspace;
    }

    /**
     * A prediction key showing the rest of the current word, if the word is followed by a
     * space in the goal (a prediction always adds a space).
     */
    private Key usefulPrediction(TextComposer composer, String typed, String goal) {
        if (predictionMode == PredictionMode.OFF) {
            return null;
        }
        String prefix = composer.currentPrefix();
        int end = typed.length();
        while (end < goal.length() && isWordChar(goal.charAt(end))) {
            end++;
        }
        String word = goal.substring(typed.length() - prefix.length(), end);
        if (end >= goal.length() || goal.charAt(end) != ' ' || word.length() <= prefix.length()) {
            return null;
        }
        for (int slot = 0; slot < predictionKeys.length; slot++) {
            if (word.equals(composer.getPrediction(slot))) {
                return predictionKeys[slot];
            }
        }
        return null;
    }

    private static boolean isWordChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
    }

    private void setInterval(int ms) {
        if (ms != engine.getIntervalMs()) {
            engine.setIntervalMs(ms);
            result.intervalTrace.add(new long[]{now, ms});
        }
    }

    public RunResult getResult() {
        return result;
    }

    /** The engine this driver steps (tests check it is the same class the live app uses). */
    public ScanEngine getEngine() {
        return engine;
    }
}
