package com.switchtype.model;

/** What happens when a key is selected. */
public enum KeyAction {
    /** Types the character of the key (letters, digits, space, punctuation, newline). */
    CHAR,
    BACKSPACE,
    /** Completes the current word with the prediction shown in the slot of the key. */
    PREDICTION,
    UNDO_WORD,
    PAUSE,
    /** Opens the scanned settings keyboard. */
    SETTINGS,
    // ---- keys of the settings keyboard ----
    SLOWER,
    FASTER,
    NEXT_STYLE,
    NEXT_LAYOUT,
    NEXT_PREDICTION,
    TOGGLE_ADAPTIVE,
    TOGGLE_CONTRAST,
    TOGGLE_PRACTICE,
    BACK;

    /** True for Backspace and Undo word: the selections that correct an earlier one. */
    public boolean isCorrection() {
        return this == BACKSPACE || this == UNDO_WORD;
    }
}
