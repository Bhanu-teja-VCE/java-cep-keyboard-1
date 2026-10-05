package com.switchtype.model;

/** What a switch press did, returned by {@code ScanEngine.onPress()}. Immutable. */
public final class Outcome {

    /** The four things a press can do. */
    public enum Kind {
        /** Nothing happened. */
        NOTHING,
        /** A row or group was chosen; scanning continues inside it. */
        NARROWED,
        /** A key was chosen. */
        SELECTED,
        /** The press resumed a paused scanner. */
        RESUMED
    }

    private static final Outcome NOTHING = new Outcome(Kind.NOTHING, null);
    private static final Outcome NARROWED = new Outcome(Kind.NARROWED, null);
    private static final Outcome RESUMED = new Outcome(Kind.RESUMED, null);

    private final Kind kind;
    private final Key key;

    private Outcome(Kind kind, Key key) {
        this.kind = kind;
        this.key = key;
    }

    public static Outcome nothing() {
        return NOTHING;
    }

    public static Outcome narrowed() {
        return NARROWED;
    }

    public static Outcome resumed() {
        return RESUMED;
    }

    public static Outcome selected(Key key) {
        return new Outcome(Kind.SELECTED, key);
    }

    public Kind getKind() {
        return kind;
    }

    /** The selected key, or null unless the kind is SELECTED. */
    public Key getKey() {
        return key;
    }

    public boolean isSelection() {
        return kind == Kind.SELECTED;
    }

    @Override
    public String toString() {
        return key == null ? kind.name() : kind + "(" + key + ")";
    }
}
