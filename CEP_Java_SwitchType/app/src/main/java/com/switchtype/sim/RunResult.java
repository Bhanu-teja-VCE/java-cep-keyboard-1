package com.switchtype.sim;

import java.util.ArrayList;
import java.util.List;

/** Counts collected while the simulator types a set of phrases, and the metrics derived from them. */
public class RunResult {

    long timeMs;
    int targetChars;
    int correctChars;
    int editDistance;
    int maxLengthSum;
    int selections;
    int wrongSelections;
    int presses;
    int steps;
    int phrases;
    int unfinished;
    /** (time ms, interval ms) every time the adaptive controller changed the interval. */
    final List<long[]> intervalTrace = new ArrayList<long[]>();
    /** (end time ms, correct chars) of every phrase, for CPM over time. */
    final List<long[]> phraseLog = new ArrayList<long[]>();

    public double cpm() {
        return Metrics.cpm(correctChars, timeMs);
    }

    public double wpm() {
        return cpm() / 5.0;
    }

    /** Wrong selections / all selections (corrected or not). */
    public double totalErrorRate() {
        return selections == 0 ? 0 : (double) wrongSelections / selections;
    }

    /** Edit distance of the final texts / longer of final and target lengths. */
    public double uncorrectedErrorRate() {
        return maxLengthSum == 0 ? 0 : (double) editDistance / maxLengthSum;
    }

    public double pressesPerChar() {
        return targetChars == 0 ? 0 : (double) presses / targetChars;
    }

    public double stepsPerChar() {
        return targetChars == 0 ? 0 : (double) steps / targetChars;
    }

    public double selectionsPerChar() {
        return targetChars == 0 ? 0 : (double) selections / targetChars;
    }

    public long getTimeMs() {
        return timeMs;
    }

    public int getSteps() {
        return steps;
    }

    public int getPresses() {
        return presses;
    }

    public int getSelections() {
        return selections;
    }

    public int getTargetChars() {
        return targetChars;
    }

    public int getUnfinished() {
        return unfinished;
    }

    public List<long[]> getIntervalTrace() {
        return intervalTrace;
    }

    public List<long[]> getPhraseLog() {
        return phraseLog;
    }
}
