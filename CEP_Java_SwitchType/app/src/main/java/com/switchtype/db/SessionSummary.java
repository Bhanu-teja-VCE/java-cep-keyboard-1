package com.switchtype.db;

import com.switchtype.sim.Metrics;

/**
 * Summary of one typing session: counts and timings only, never the text (privacy).
 * The derived metrics are computed here for the offline store; with MySQL they are
 * computed by sp_finish_session instead.
 */
public class SessionSummary {

    private int id;
    private int profileId;
    private String mode;               // FREE or PRACTICE
    private long startedAt;            // epoch ms
    private int targetChars;
    private int correctChars;
    private int presses;
    private int selections;
    private int corrections;
    private long durationMs;

    public SessionSummary(int profileId, String mode, long startedAt) {
        this.profileId = profileId;
        this.mode = mode;
        this.startedAt = startedAt;
    }

    /** Correct characters per minute. */
    public double cpm() {
        return Metrics.cpm(correctChars, durationMs);
    }

    /** Share of selections that were corrections (Backspace / Undo word). */
    public double correctionRate() {
        return selections == 0 ? 0 : (double) corrections / selections;
    }

    public double pressesPerChar() {
        return targetChars == 0 ? 0 : (double) presses / targetChars;
    }

    /** Sets all counts at once (Unit I: this to name the fields). */
    public void setCounts(int targetChars, int correctChars, int presses, int selections,
                          int corrections, long durationMs) {
        this.targetChars = targetChars;
        this.correctChars = correctChars;
        this.presses = presses;
        this.selections = selections;
        this.corrections = corrections;
        this.durationMs = durationMs;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getProfileId() {
        return profileId;
    }

    public String getMode() {
        return mode;
    }

    public long getStartedAt() {
        return startedAt;
    }

    public int getTargetChars() {
        return targetChars;
    }

    public int getCorrectChars() {
        return correctChars;
    }

    public int getPresses() {
        return presses;
    }

    public int getSelections() {
        return selections;
    }

    public int getCorrections() {
        return corrections;
    }

    public long getDurationMs() {
        return durationMs;
    }
}
