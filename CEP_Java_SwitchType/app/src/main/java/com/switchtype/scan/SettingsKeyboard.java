package com.switchtype.scan;

import com.switchtype.model.Key;
import com.switchtype.model.KeyAction;

/**
 * The scanned settings screen. It is just another grid for the same engine, so a person
 * can change settings and leave practice mode with the switch alone (WCAG 2.1.1).
 */
public final class SettingsKeyboard {

    private SettingsKeyboard() {
    }

    public static Key[][] grid() {
        return new Key[][]{
            {Key.control(KeyAction.BACK, "back"),
                Key.control(KeyAction.SLOWER, "slower"),
                Key.control(KeyAction.FASTER, "faster")},
            {Key.control(KeyAction.NEXT_STYLE, "scan style"),
                Key.control(KeyAction.NEXT_LAYOUT, "layout"),
                Key.control(KeyAction.NEXT_PREDICTION, "prediction")},
            {Key.control(KeyAction.TOGGLE_ADAPTIVE, "adaptive"),
                Key.control(KeyAction.TOGGLE_CONTRAST, "contrast"),
                Key.control(KeyAction.TOGGLE_PRACTICE, "practice")}
        };
    }
}
