package com.switchtype.model;

/**
 * One key of the on-screen keyboard. Keys are immutable, so the scanner thread and the
 * Swing thread can share them safely. Identity matters: a grid holds each key once.
 */
public final class Key {

    private final KeyAction action;
    private final char ch;
    private final int slot;
    private final String label;

    private Key(KeyAction action, char ch, int slot, String label) {
        this.action = action;
        this.ch = ch;
        this.slot = slot;
        this.label = label;
    }

    /** A key that types one character. */
    public static Key character(char c) {
        return new Key(KeyAction.CHAR, c, -1, labelFor(c));
    }

    /** A control key such as Backspace or Pause. */
    public static Key control(KeyAction action, String label) {
        return new Key(action, '\0', -1, label);
    }

    /** A key of the prediction row; the word it shows changes, the key itself does not. */
    public static Key prediction(int slot) {
        return new Key(KeyAction.PREDICTION, '\0', slot, "");
    }

    private static String labelFor(char c) {
        if (c == ' ') {
            return "space";
        }
        if (c == '\n') {
            return "enter";
        }
        return String.valueOf(c);
    }

    public KeyAction getAction() {
        return action;
    }

    public char getChar() {
        return ch;
    }

    public int getSlot() {
        return slot;
    }

    public String getLabel() {
        return label;
    }

    public boolean isChar() {
        return action == KeyAction.CHAR;
    }

    @Override
    public String toString() {
        return action == KeyAction.PREDICTION ? "prediction#" + slot : label;
    }
}
