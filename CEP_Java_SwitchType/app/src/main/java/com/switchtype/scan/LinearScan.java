package com.switchtype.scan;

import com.switchtype.model.Key;
import com.switchtype.model.Outcome;
import com.switchtype.model.ScanStyle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The highlight visits every key in row-major order, one at a time. One press per key. */
public class LinearScan extends ScanStrategy {

    private final ArrayList<Key> order = new ArrayList<Key>();
    private int index;

    public LinearScan(Key[][] grid) {
        super(grid, 1);
        for (Key[] row : grid) {
            Collections.addAll(order, row);
        }
    }

    @Override
    public void reset() {
        index = 0;
    }

    @Override
    public boolean step() {
        index = (index + 1) % order.size();
        return false;                               // linear scanning never gives up
    }

    @Override
    public Outcome press() {
        Key chosen = order.get(index);
        reset();
        return Outcome.selected(chosen);
    }

    @Override
    public List<Key> highlighted() {
        return Collections.singletonList(order.get(index));
    }

    @Override
    public boolean isHighlighted(Key k) {
        return order.get(index) == k;
    }

    @Override
    public boolean isFirstItem() {
        return index == 0;
    }

    @Override
    public ScanStyle getStyle() {
        return ScanStyle.LINEAR;
    }

    /** Key at position i in the order needs i steps. */
    @Override
    public int analyticSteps(Key k) {
        return order.indexOf(k);
    }

    @Override
    public int analyticPresses(Key k) {
        return 1;
    }
}
