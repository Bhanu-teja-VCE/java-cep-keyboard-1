package com.switchtype.scan;

import com.switchtype.model.Config;
import com.switchtype.model.Key;
import com.switchtype.model.KeyAction;
import com.switchtype.model.LayoutType;

import java.util.ArrayList;
import java.util.List;

/**
 * Arranges the keys into a grid. Subclasses only decide the order of the 31 text
 * characters; the prediction row, edit row and digit row are the same for every layout
 * (Unit I: abstract class with a template method).
 */
public abstract class KeyboardLayout {

    /** The 31 characters every layout must contain: letters, space, . , ? and newline. */
    public static final String TEXT_CHARS = "abcdefghijklmnopqrstuvwxyz .,?\n";
    public static final String DIGITS = "1234567890";

    /** The rows of text characters, top to bottom. */
    protected abstract char[][] textRows();

    public abstract LayoutType getType();

    /**
     * Builds the full grid (Unit I: two-dimensional, jagged array).
     *
     * @param predictionRow true to put the prediction row on top (scanned first)
     */
    public final Key[][] buildGrid(boolean predictionRow) {
        List<Key[]> rows = new ArrayList<Key[]>();
        if (predictionRow) {
            Key[] row = new Key[Config.PREDICTION_SLOTS];
            for (int i = 0; i < row.length; i++) {
                row[i] = Key.prediction(i);
            }
            rows.add(row);
        }
        for (char[] chars : textRows()) {
            Key[] row = new Key[chars.length];
            for (int i = 0; i < chars.length; i++) {
                row[i] = Key.character(chars[i]);
            }
            rows.add(row);
        }
        rows.add(new Key[]{
            Key.control(KeyAction.BACKSPACE, "delete"),
            Key.control(KeyAction.UNDO_WORD, "undo word"),
            Key.control(KeyAction.PAUSE, "pause"),
            Key.control(KeyAction.SETTINGS, "settings")});
        Key[] digits = new Key[DIGITS.length()];
        for (int i = 0; i < digits.length; i++) {
            digits[i] = Key.character(DIGITS.charAt(i));
        }
        rows.add(digits);
        return rows.toArray(new Key[rows.size()][]);
    }

    /** Helper for subclasses: turns strings into character rows. */
    protected static char[][] rowsOf(String... rows) {
        char[][] result = new char[rows.length][];
        for (int i = 0; i < rows.length; i++) {
            result[i] = rows[i].toCharArray();
        }
        return result;
    }

    /** Finds the key that types character c, or null. */
    public static Key findChar(Key[][] grid, char c) {
        for (Key[] row : grid) {
            for (Key k : row) {
                if (k.isChar() && k.getChar() == c) {
                    return k;
                }
            }
        }
        return null;
    }

    /** Finds the first key with the given action (and slot for predictions), or null. */
    public static Key find(Key[][] grid, KeyAction action, int slot) {
        for (Key[] row : grid) {
            for (Key k : row) {
                if (k.getAction() == action && (action != KeyAction.PREDICTION || k.getSlot() == slot)) {
                    return k;
                }
            }
        }
        return null;
    }
}
