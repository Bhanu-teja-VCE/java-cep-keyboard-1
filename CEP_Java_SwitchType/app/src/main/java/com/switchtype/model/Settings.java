package com.switchtype.model;

import com.switchtype.exception.InvalidSettingsException;

import java.io.Serializable;

/**
 * All settings of one user. Serializable so profiles can be saved to a file when MySQL is
 * not available. Copies are handed between threads, never the same object.
 */
public class Settings implements Serializable {

    private static final long serialVersionUID = 1L;

    private int intervalMs = Config.DEFAULT_INTERVAL_MS;
    private int firstExtraPct = 50;
    private ScanStyle style = ScanStyle.ROW_COLUMN;
    private LayoutType layout = LayoutType.FREQUENCY;
    private PredictionMode prediction = PredictionMode.BIGRAM;
    private int rowCycles = 2;
    private SwitchChoice switchChoice = SwitchChoice.SPACE;
    private boolean adaptive = true;
    private boolean highContrast = false;
    private int keySize = Config.DEFAULT_KEY_SIZE;
    private int fontSize = Config.DEFAULT_FONT_SIZE;
    private boolean audio = false;
    private int restMinutes = 20;
    private boolean eventLogging = false;

    /** Default settings. */
    public Settings() {
    }

    /** Settings for an experiment configuration (Unit I: constructor overloading). */
    public Settings(ScanStyle style, LayoutType layout, PredictionMode prediction, int intervalMs) {
        this.style = style;
        this.layout = layout;
        this.prediction = prediction;
        this.intervalMs = intervalMs;
        this.adaptive = false;
        this.firstExtraPct = 0;
    }

    /** Throws if any value is outside its allowed range. */
    public void validate() throws InvalidSettingsException {
        check(intervalMs, Config.MIN_INTERVAL_MS, Config.MAX_INTERVAL_MS, "Scan interval (ms)");
        check(firstExtraPct, 0, Config.MAX_FIRST_EXTRA_PCT, "Extra delay on first item (%)");
        check(rowCycles, 1, Config.MAX_ROW_CYCLES, "Row cycles");
        check(keySize, Config.MIN_KEY_SIZE, Config.MAX_KEY_SIZE, "Key size (px)");
        check(fontSize, 12, 48, "Font size");
        check(restMinutes, 0, 240, "Rest reminder (min)");
        if (style == null || layout == null || prediction == null || switchChoice == null) {
            throw new InvalidSettingsException("Scan style, layout, prediction and switch must be chosen");
        }
    }

    private static void check(int value, int min, int max, String name) throws InvalidSettingsException {
        if (value < min || value > max) {
            throw new InvalidSettingsException(name + " must be between " + min + " and " + max + ", not " + value);
        }
    }

    /** Clamps an interval into the allowed range. */
    public static int clampInterval(double ms) {
        long rounded = Math.round(ms);
        return (int) Math.max(Config.MIN_INTERVAL_MS, Math.min(Config.MAX_INTERVAL_MS, rounded));
    }

    public Settings copy() {
        Settings c = new Settings();
        c.intervalMs = intervalMs;
        c.firstExtraPct = firstExtraPct;
        c.style = style;
        c.layout = layout;
        c.prediction = prediction;
        c.rowCycles = rowCycles;
        c.switchChoice = switchChoice;
        c.adaptive = adaptive;
        c.highContrast = highContrast;
        c.keySize = keySize;
        c.fontSize = fontSize;
        c.audio = audio;
        c.restMinutes = restMinutes;
        c.eventLogging = eventLogging;
        return c;
    }

    public int getIntervalMs() {
        return intervalMs;
    }

    public void setIntervalMs(int intervalMs) {
        this.intervalMs = intervalMs;
    }

    public int getFirstExtraPct() {
        return firstExtraPct;
    }

    public void setFirstExtraPct(int firstExtraPct) {
        this.firstExtraPct = firstExtraPct;
    }

    public ScanStyle getStyle() {
        return style;
    }

    public void setStyle(ScanStyle style) {
        this.style = style;
    }

    public LayoutType getLayout() {
        return layout;
    }

    public void setLayout(LayoutType layout) {
        this.layout = layout;
    }

    public PredictionMode getPrediction() {
        return prediction;
    }

    public void setPrediction(PredictionMode prediction) {
        this.prediction = prediction;
    }

    public int getRowCycles() {
        return rowCycles;
    }

    public void setRowCycles(int rowCycles) {
        this.rowCycles = rowCycles;
    }

    public SwitchChoice getSwitchChoice() {
        return switchChoice;
    }

    public void setSwitchChoice(SwitchChoice switchChoice) {
        this.switchChoice = switchChoice;
    }

    public boolean isAdaptive() {
        return adaptive;
    }

    public void setAdaptive(boolean adaptive) {
        this.adaptive = adaptive;
    }

    public boolean isHighContrast() {
        return highContrast;
    }

    public void setHighContrast(boolean highContrast) {
        this.highContrast = highContrast;
    }

    public int getKeySize() {
        return keySize;
    }

    public void setKeySize(int keySize) {
        this.keySize = keySize;
    }

    public int getFontSize() {
        return fontSize;
    }

    public void setFontSize(int fontSize) {
        this.fontSize = fontSize;
    }

    public boolean isAudio() {
        return audio;
    }

    public void setAudio(boolean audio) {
        this.audio = audio;
    }

    public int getRestMinutes() {
        return restMinutes;
    }

    public void setRestMinutes(int restMinutes) {
        this.restMinutes = restMinutes;
    }

    public boolean isEventLogging() {
        return eventLogging;
    }

    public void setEventLogging(boolean eventLogging) {
        this.eventLogging = eventLogging;
    }
}
