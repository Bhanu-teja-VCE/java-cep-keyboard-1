package com.switchtype.live;

import com.switchtype.db.SessionSummary;
import com.switchtype.model.Config;
import com.switchtype.model.Key;
import com.switchtype.model.KeyAction;
import com.switchtype.model.LayoutType;
import com.switchtype.model.Outcome;
import com.switchtype.model.PredictionMode;
import com.switchtype.model.ScanStyle;
import com.switchtype.model.Settings;
import com.switchtype.predict.BigramPredictor;
import com.switchtype.predict.CharFrequency;
import com.switchtype.predict.LanguageModel;
import com.switchtype.predict.NoPredictor;
import com.switchtype.predict.TextComposer;
import com.switchtype.predict.UnigramPredictor;
import com.switchtype.predict.WordPredictor;
import com.switchtype.scan.AdaptiveController;
import com.switchtype.scan.ScanEngine;
import com.switchtype.scan.ScanFactory;
import com.switchtype.scan.SettingsKeyboard;
import com.switchtype.sim.Metrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Runs a live typing session. Confined to the Swing event dispatch thread: the scanner
 * thread reaches it only through invokeLater (ScanListener), and the UI calls it directly.
 * It owns the text, the predictions, the adaptive controller and the practice mode.
 */
public class SessionController implements ScanListener {

    /** What the controller needs from the screen. */
    public interface View {
        void refresh();

        void message(String text);

        /** Stores a finished session (the view hands it to the background worker). */
        void saveSession(SessionSummary summary, List<String[]> events);

        /** The settings changed from the scanned settings keyboard. */
        void settingsChanged(Settings settings);
    }

    private final CharFrequency frequency;
    private final LinkedList<String> practiceQueue;
    private final List<String> practicePhrases;
    private View view;
    private LanguageModel model;
    private Settings settings;
    private ScannerThread scanner;
    private TextComposer composer;
    private AdaptiveController adaptive;
    private Key[][] typingGrid;
    private Key[][] currentGrid;
    private boolean inSettings;
    private List<Key> highlighted = Collections.emptyList();
    private boolean switchHeld;
    private int profileId;

    // counts of the current session (free typing) or current practice phrase
    private long startedAt;
    private int presses;
    private int selections;
    private int corrections;
    private final List<String[]> events = new ArrayList<String[]>();
    private long lastRestAt = System.currentTimeMillis();

    private String practiceTarget;
    private String lastPracticeResult;

    public SessionController(CharFrequency frequency, LanguageModel model, List<String> practicePhrases) {
        this.frequency = frequency;
        this.model = model;
        this.practicePhrases = practicePhrases;
        this.practiceQueue = new LinkedList<String>(practicePhrases);
    }

    /** Starts scanning with these settings. */
    public void start(Settings initial, int profile, View v) {
        this.view = v;
        this.profileId = profile;
        this.settings = initial.copy();
        this.composer = new TextComposer(predictorFor(settings.getPrediction()), true);
        this.typingGrid = ScanFactory.grid(settings, frequency);
        this.currentGrid = typingGrid;
        this.adaptive = new AdaptiveController(settings.getIntervalMs());
        ScanEngine engine = new ScanEngine(ScanFactory.strategy(settings.getStyle(), typingGrid,
                settings.getRowCycles(), frequency), settings.getIntervalMs(), settings.getFirstExtraPct());
        this.scanner = new ScannerThread(engine, this, true);
        resetCounts();
        scanner.start();
    }

    private WordPredictor predictorFor(PredictionMode mode) {
        switch (mode) {
            case UNIGRAM:
                return new UnigramPredictor(model);
            case BIGRAM:
                return new BigramPredictor(model);
            default:
                return new NoPredictor();
        }
    }

    // ------------------------------------------------------------- the switch

    /** The switch went down. Holding it does not repeat: only the first down counts. */
    public void switchDown() {
        if (switchHeld || scanner == null) {
            return;
        }
        switchHeld = true;
        presses++;
        scanner.press();
    }

    public void switchUp() {
        switchHeld = false;
    }

    // ------------------------------------------------------------- ScanListener (on the EDT)

    @Override
    public void highlightChanged(List<Key> keys) {
        highlighted = keys;
        view.refresh();
    }

    @Override
    public void pressed(Outcome outcome) {
        if (outcome.isSelection()) {
            handleKey(outcome.getKey());
        } else if (outcome.getKind() == Outcome.Kind.RESUMED) {
            view.message("Scanning again.");
        }
        view.refresh();
    }

    @Override
    public void gaveUp() {
        if (settings.isAdaptive() && !inSettings) {
            scanner.setIntervalMs(adaptive.onGiveUp());
        }
    }

    private void handleKey(Key key) {
        if (inSettings) {
            handleSettingsKey(key);
            return;
        }
        selections++;
        if (key.getAction().isCorrection()) {
            corrections++;
        }
        if (settings.isEventLogging()) {
            events.add(new String[]{String.valueOf(System.currentTimeMillis() - startedAt), key.toString()});
        }
        if (settings.isAdaptive()) {
            scanner.setIntervalMs(adaptive.onSelection(key));
            settings.setIntervalMs(scanner.getIntervalMs());
        }
        switch (key.getAction()) {
            case PAUSE:
                lastRestAt = System.currentTimeMillis();
                view.message("Paused. Press the switch to continue.");
                break;
            case SETTINGS:
                showGrid(true);
                view.message("Settings. Choose 'back' to return to the keyboard.");
                break;
            default:
                composer.apply(key);
                if (practiceTarget != null && composer.getText().equals(practiceTarget)) {
                    finishPractice();
                }
        }
    }

    private void handleSettingsKey(Key key) {
        Settings s = settings.copy();
        switch (key.getAction()) {
            case BACK:
                showGrid(false);
                view.message("");
                return;
            case SLOWER:
                s.setIntervalMs(Settings.clampInterval(s.getIntervalMs() * Config.ADAPT_SLOWER));
                break;
            case FASTER:
                s.setIntervalMs(Settings.clampInterval(s.getIntervalMs() * Config.ADAPT_FASTER));
                break;
            case NEXT_STYLE:
                s.setStyle(ScanStyle.values()[(s.getStyle().ordinal() + 1) % ScanStyle.values().length]);
                break;
            case NEXT_LAYOUT:
                s.setLayout(LayoutType.values()[(s.getLayout().ordinal() + 1) % LayoutType.values().length]);
                break;
            case NEXT_PREDICTION:
                s.setPrediction(PredictionMode.values()[(s.getPrediction().ordinal() + 1) % PredictionMode.values().length]);
                break;
            case TOGGLE_ADAPTIVE:
                s.setAdaptive(!s.isAdaptive());
                break;
            case TOGGLE_CONTRAST:
                s.setHighContrast(!s.isHighContrast());
                break;
            case TOGGLE_PRACTICE:
                if (practiceTarget == null) {
                    startPractice();
                } else {
                    stopPractice();
                }
                showGrid(false);
                return;
            default:
                return;
        }
        applySettings(s);
        view.settingsChanged(settings.copy());
        showGrid(true);                                // stay in settings after a change
    }

    private void showGrid(boolean settingsGrid) {
        inSettings = settingsGrid;
        currentGrid = settingsGrid ? SettingsKeyboard.grid() : typingGrid;
        ScanStyle style = settingsGrid && settings.getStyle() == ScanStyle.GROUP_BINARY ? ScanStyle.ROW_COLUMN : settings.getStyle();
        scanner.setStrategy(ScanFactory.strategy(style, currentGrid, settings.getRowCycles(), frequency));
        view.refresh();
    }

    /** Applies new settings immediately, without a restart. */
    public void applySettings(Settings s) {
        boolean newPredictor = s.getPrediction() != settings.getPrediction();
        boolean newGrid = newPredictor || s.getLayout() != settings.getLayout() || s.getStyle() != settings.getStyle()
                || s.getRowCycles() != settings.getRowCycles();
        settings = s.copy();
        if (newPredictor) {
            composer.setPredictor(predictorFor(settings.getPrediction()));
        }
        if (newGrid) {
            typingGrid = ScanFactory.grid(settings, frequency);
            if (!inSettings) {
                currentGrid = typingGrid;
                scanner.setStrategy(ScanFactory.strategy(settings.getStyle(), typingGrid, settings.getRowCycles(), frequency));
            }
        }
        adaptive = new AdaptiveController(settings.getIntervalMs());
        scanner.setIntervalMs(settings.getIntervalMs());
        scanner.setFirstExtraPct(settings.getFirstExtraPct());
        view.refresh();
    }

    /** Uses a different vocabulary (after reset or import). */
    public void setModel(LanguageModel newModel) {
        this.model = newModel;
        composer.setPredictor(predictorFor(settings.getPrediction()));
        view.refresh();
    }

    // ------------------------------------------------------------- practice mode

    public void startPractice() {
        saveFreeSession();
        if (practiceQueue.isEmpty()) {
            practiceQueue.addAll(practicePhrases);
        }
        practiceTarget = practiceQueue.removeFirst();
        composer.clear();
        resetCounts();
        lastPracticeResult = null;
        view.message("Practice: type the phrase shown above the text.");
    }

    public void stopPractice() {
        practiceTarget = null;
        composer.clear();
        resetCounts();
        view.message("Practice finished.");
    }

    private void finishPractice() {
        String typed = composer.getText();
        long duration = System.currentTimeMillis() - startedAt;
        int correct = Metrics.correctChars(typed, practiceTarget);
        SessionSummary s = new SessionSummary(profileId, "PRACTICE", startedAt);
        s.setCounts(practiceTarget.length(), correct, presses, selections, corrections, duration);
        lastPracticeResult = String.format(java.util.Locale.ROOT,
                "Done: %.1f characters per minute, %d errors left, %d corrections, %.2f presses per character.",
                s.cpm(), Metrics.levenshtein(typed, practiceTarget), corrections, s.pressesPerChar());
        view.saveSession(s, new ArrayList<String[]>(events));
        startPractice();
        view.message(lastPracticeResult + " Next phrase is ready.");
    }

    /** Saves the free-typing session so far (summary only), if anything was typed. */
    public void saveFreeSession() {
        if (practiceTarget == null && selections > 0) {
            int chars = composer.length();
            SessionSummary s = new SessionSummary(profileId, "FREE", startedAt);
            s.setCounts(chars, chars, presses, selections, corrections, System.currentTimeMillis() - startedAt);
            view.saveSession(s, new ArrayList<String[]>(events));
        }
        resetCounts();
    }

    private void resetCounts() {
        startedAt = System.currentTimeMillis();
        presses = 0;
        selections = 0;
        corrections = 0;
        events.clear();
    }

    /** Stops the scanner thread (on exit or when the profile changes). */
    public void shutdown() throws InterruptedException {
        saveFreeSession();
        if (scanner != null) {
            scanner.shutdown();
        }
    }

    // ------------------------------------------------------------- read by the view

    /** Labels for the keys whose text changes (predictions and settings keys). */
    public Map<Key, String> dynamicLabels() {
        Map<Key, String> labels = new HashMap<Key, String>();
        for (Key[] row : currentGrid) {
            for (Key k : row) {
                String label = dynamicLabel(k);
                if (label != null) {
                    labels.put(k, label);
                }
            }
        }
        return labels;
    }

    private String dynamicLabel(Key k) {
        switch (k.getAction()) {
            case PREDICTION:
                String word = composer.getPrediction(k.getSlot());
                return word == null ? "" : word;
            case SLOWER:
            case FASTER:
                return k.getLabel() + " (" + settings.getIntervalMs() + " ms)";
            case NEXT_STYLE:
                return "style: " + settings.getStyle();
            case NEXT_LAYOUT:
                return "layout: " + settings.getLayout();
            case NEXT_PREDICTION:
                return "prediction: " + settings.getPrediction();
            case TOGGLE_ADAPTIVE:
                return "adaptive: " + (settings.isAdaptive() ? "on" : "off");
            case TOGGLE_CONTRAST:
                return "contrast: " + (settings.isHighContrast() ? "high" : "normal");
            case TOGGLE_PRACTICE:
                return practiceTarget == null ? "start practice" : "stop practice";
            default:
                return null;
        }
    }

    public Key[][] getGrid() {
        return currentGrid;
    }

    public List<Key> getHighlighted() {
        return highlighted;
    }

    public boolean isPaused() {
        return highlighted.isEmpty();
    }

    public String getText() {
        return composer.getText();
    }

    public String getPracticeTarget() {
        return practiceTarget;
    }

    public Settings getSettings() {
        return settings.copy();
    }

    public int getCurrentIntervalMs() {
        return scanner == null ? settings.getIntervalMs() : scanner.getIntervalMs();
    }

    public boolean isInSettings() {
        return inSettings;
    }

    /** Minutes since the last pause (for the rest reminder). */
    public long minutesSinceRest() {
        return (System.currentTimeMillis() - lastRestAt) / 60000;
    }

    public void restTaken() {
        lastRestAt = System.currentTimeMillis();
    }

    public LanguageModel getModel() {
        return model;
    }
}
