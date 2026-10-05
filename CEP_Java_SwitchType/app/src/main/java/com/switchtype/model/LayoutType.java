package com.switchtype.model;

/** Order of the characters on the keyboard. */
public enum LayoutType {
    ALPHABETICAL("Alphabetical"),
    QWERTY("QWERTY"),
    FREQUENCY("Frequency");

    private final String label;

    LayoutType(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
