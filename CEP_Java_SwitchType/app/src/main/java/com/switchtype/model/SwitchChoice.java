package com.switchtype.model;

/** Which physical input acts as the single switch. */
public enum SwitchChoice {
    SPACE("Space bar"),
    ENTER("Enter key"),
    MOUSE("Mouse click");

    private final String label;

    SwitchChoice(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
