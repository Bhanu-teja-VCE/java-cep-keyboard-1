package com.switchtype.model;

/** Constants shared by the engine, the simulator and the UI (Unit I: static final). */
public final class Config {

    public static final int MIN_INTERVAL_MS = 200;
    public static final int MAX_INTERVAL_MS = 2000;
    public static final int DEFAULT_INTERVAL_MS = 1000;
    public static final int MAX_FIRST_EXTRA_PCT = 100;
    public static final int MAX_ROW_CYCLES = 5;

    /** Number of prediction keys in the prediction row. */
    public static final int PREDICTION_SLOTS = 4;

    /** Adaptive speed: correct selections in a row before speeding up. */
    public static final int ADAPT_STREAK = 5;
    public static final double ADAPT_FASTER = 0.9;
    public static final double ADAPT_SLOWER = 1.2;

    /** WCAG 2.5.5: keys are at least 44 x 44 px at the default size. */
    public static final int DEFAULT_KEY_SIZE = 56;
    public static final int MIN_KEY_SIZE = 44;
    public static final int MAX_KEY_SIZE = 96;
    public static final int DEFAULT_FONT_SIZE = 20;

    /** Reaction times below this are treated as impossible (they would be anticipation). */
    public static final int MIN_REACTION_MS = 150;

    private Config() {
    }
}
