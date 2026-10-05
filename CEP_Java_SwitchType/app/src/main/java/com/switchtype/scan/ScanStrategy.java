package com.switchtype.scan;

import com.switchtype.model.Key;
import com.switchtype.model.Outcome;
import com.switchtype.model.ScanStyle;

import java.util.List;

/**
 * One way of moving the highlight over the grid. The engine only talks to this type,
 * so the strategies are interchangeable (Unit I: abstract class, dynamic method dispatch).
 * A strategy knows nothing about time: it only counts steps.
 */
public abstract class ScanStrategy {

    protected final Key[][] grid;
    /** Full cycles inside a row or group without a press before going back to the top. */
    protected final int maxCycles;

    protected ScanStrategy(Key[][] grid, int maxCycles) {
        this.grid = grid;
        this.maxCycles = maxCycles;
    }

    /** Puts the highlight back on the first item. */
    public abstract void reset();

    /**
     * Moves the highlight one item on.
     *
     * @return true if the strategy gave up on a row or group and went back to the top
     */
    public abstract boolean step();

    /** The user pressed the switch while the current item was highlighted. */
    public abstract Outcome press();

    /** The keys inside the currently highlighted item (a key, a row or a group). */
    public abstract List<Key> highlighted();

    /** True if key k is inside the highlighted item. */
    public abstract boolean isHighlighted(Key k);

    /** True if the highlighted item is the first one of its row, group or order. */
    public abstract boolean isFirstItem();

    public abstract ScanStyle getStyle();

    /** Analytic number of steps a perfect user needs to select k (no noise, no misses). */
    public abstract int analyticSteps(Key k);

    /** Analytic number of switch presses needed to select k. */
    public abstract int analyticPresses(Key k);

    public Key[][] getGrid() {
        return grid;
    }
}
