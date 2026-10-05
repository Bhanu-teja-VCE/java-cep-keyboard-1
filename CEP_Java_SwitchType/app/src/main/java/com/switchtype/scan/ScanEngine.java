package com.switchtype.scan;

import com.switchtype.model.Key;
import com.switchtype.model.KeyAction;
import com.switchtype.model.Outcome;

import java.util.List;

/**
 * The single scanning state machine used by BOTH the live app (ScannerThread, real time)
 * and the experiments (SimulationDriver, virtual time). It knows nothing about threads,
 * Swing or clocks: a driver calls onStep() when the dwell time has passed and onPress()
 * when the switch is pressed, and asks nextDelayMs() how long to wait.
 * Not thread-safe on purpose: each driver confines it to one thread.
 */
public class ScanEngine {

    private ScanStrategy strategy;
    private int intervalMs;
    private int firstExtraPct;
    private boolean paused;

    public ScanEngine(ScanStrategy strategy, int intervalMs, int firstExtraPct) {
        this.strategy = strategy;
        this.intervalMs = intervalMs;
        this.firstExtraPct = firstExtraPct;
    }

    /** Starts scanning from the first item. */
    public void start() {
        paused = false;
        strategy.reset();
    }

    /**
     * The dwell time of the current item has passed: move the highlight.
     *
     * @return true if the strategy gave up on a row or group (a hint that the user missed)
     */
    public boolean onStep() {
        if (paused) {
            return false;
        }
        return strategy.step();
    }

    /** The switch was pressed. Selecting the Pause key pauses; any press resumes. */
    public Outcome onPress() {
        if (paused) {
            paused = false;
            strategy.reset();
            return Outcome.resumed();
        }
        Outcome outcome = strategy.press();
        if (outcome.isSelection() && outcome.getKey().getAction() == KeyAction.PAUSE) {
            paused = true;
        }
        return outcome;
    }

    /** How long the current item stays highlighted: longer for the first item of a row. */
    public int nextDelayMs() {
        if (!paused && strategy.isFirstItem()) {
            return intervalMs + intervalMs * firstExtraPct / 100;
        }
        return intervalMs;
    }

    public List<Key> highlighted() {
        return paused ? java.util.Collections.<Key>emptyList() : strategy.highlighted();
    }

    public boolean isHighlighted(Key k) {
        return !paused && strategy.isHighlighted(k);
    }

    /** Replaces the strategy (new style, layout or grid) and starts from the top. */
    public void setStrategy(ScanStrategy strategy) {
        this.strategy = strategy;
        strategy.reset();
    }

    public ScanStrategy getStrategy() {
        return strategy;
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

    public boolean isPaused() {
        return paused;
    }
}
