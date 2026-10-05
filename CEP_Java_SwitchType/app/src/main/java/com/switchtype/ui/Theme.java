package com.switchtype.ui;

import java.awt.Color;

/**
 * The two colour themes. Every colour pair used for text or for the highlight is checked
 * against the WCAG contrast ratios by ThemeContrastTest (4.5:1 for text, 3:1 for the
 * highlight, borders and other non-text indicators).
 */
public final class Theme {

    public static final Theme NORMAL = new Theme("normal",
            rgb(0xF4F1EA), rgb(0x1F2328),
            rgb(0xFFFFFF), rgb(0x1F2328), rgb(0x5F6670),
            rgb(0xE3ECFA), rgb(0x1E3A8A),
            rgb(0xEFE9DC), rgb(0x3B2F1E),
            rgb(0x1E40AF), rgb(0xFFFFFF), rgb(0x111827));

    public static final Theme HIGH_CONTRAST = new Theme("high contrast",
            rgb(0x000000), rgb(0xFFFFFF),
            rgb(0x000000), rgb(0xFFFFFF), rgb(0xFFFFFF),
            rgb(0x001F5C), rgb(0xFFFFFF),
            rgb(0x1A1A1A), rgb(0xFFFF66),
            rgb(0xFFFF00), rgb(0x000000), rgb(0x00E5FF));

    public final String name;
    public final Color background;
    public final Color text;
    public final Color keyFill;
    public final Color keyText;
    public final Color keyBorder;
    public final Color predictionFill;
    public final Color predictionText;
    public final Color controlFill;
    public final Color controlText;
    public final Color highlightFill;
    public final Color highlightText;
    public final Color highlightBorder;

    private Theme(String name, Color background, Color text, Color keyFill, Color keyText, Color keyBorder,
                  Color predictionFill, Color predictionText, Color controlFill, Color controlText,
                  Color highlightFill, Color highlightText, Color highlightBorder) {
        this.name = name;
        this.background = background;
        this.text = text;
        this.keyFill = keyFill;
        this.keyText = keyText;
        this.keyBorder = keyBorder;
        this.predictionFill = predictionFill;
        this.predictionText = predictionText;
        this.controlFill = controlFill;
        this.controlText = controlText;
        this.highlightFill = highlightFill;
        this.highlightText = highlightText;
        this.highlightBorder = highlightBorder;
    }

    public static Theme of(boolean highContrast) {
        return highContrast ? HIGH_CONTRAST : NORMAL;
    }

    private static Color rgb(int value) {
        return new Color(value);
    }

    /** WCAG 2 contrast ratio (L1 + 0.05) / (L2 + 0.05), from 1:1 to 21:1. */
    public static double contrast(Color a, Color b) {
        double la = luminance(a);
        double lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    /** WCAG relative luminance of an sRGB colour. */
    static double luminance(Color c) {
        return 0.2126 * channel(c.getRed()) + 0.7152 * channel(c.getGreen()) + 0.0722 * channel(c.getBlue());
    }

    private static double channel(int value) {
        double v = value / 255.0;
        return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    /** Text colour on background pairs that must reach 4.5:1 (WCAG 1.4.3). */
    public Color[][] textPairs() {
        return new Color[][]{{text, background}, {keyText, keyFill}, {predictionText, predictionFill},
            {controlText, controlFill}, {highlightText, highlightFill}};
    }

    /** Non-text indicator pairs that must reach 3:1 (WCAG 1.4.11). */
    public Color[][] indicatorPairs() {
        return new Color[][]{{keyBorder, background}, {highlightBorder, background}, {highlightBorder, keyFill},
            {highlightFill, keyFill}, {highlightFill, background}};
    }
}
