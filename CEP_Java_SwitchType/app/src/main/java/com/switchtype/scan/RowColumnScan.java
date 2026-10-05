package com.switchtype.scan;

import com.switchtype.model.Key;
import com.switchtype.model.Outcome;
import com.switchtype.model.ScanStyle;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * First the rows are highlighted one by one. A press enters the row, then its keys are
 * highlighted one by one and a press selects the key. If the row cycles maxCycles times
 * without a press, scanning goes back to the rows.
 */
public class RowColumnScan extends ScanStrategy {

    private final HashMap<Key, int[]> position = new HashMap<Key, int[]>();
    private boolean inRow;
    private int row;
    private int col;
    private int cycles;

    public RowColumnScan(Key[][] grid, int maxCycles) {
        super(grid, maxCycles);
        for (int r = 0; r < grid.length; r++) {
            for (int c = 0; c < grid[r].length; c++) {
                position.put(grid[r][c], new int[]{r, c});
            }
        }
    }

    @Override
    public void reset() {
        inRow = false;
        row = 0;
        col = 0;
        cycles = 0;
    }

    @Override
    public boolean step() {
        if (!inRow) {
            row = (row + 1) % grid.length;
            return false;
        }
        col++;
        if (col == grid[row].length) {
            col = 0;
            cycles++;
            if (cycles >= maxCycles) {
                reset();                            // the user let the row pass: back to rows
                return true;
            }
        }
        return false;
    }

    @Override
    public Outcome press() {
        if (!inRow) {
            inRow = true;
            col = 0;
            cycles = 0;
            return Outcome.narrowed();
        }
        Key chosen = grid[row][col];
        reset();
        return Outcome.selected(chosen);
    }

    @Override
    public List<Key> highlighted() {
        if (inRow) {
            return Collections.singletonList(grid[row][col]);
        }
        return Arrays.asList(grid[row]);
    }

    @Override
    public boolean isHighlighted(Key k) {
        int[] p = position.get(k);
        if (p == null || p[0] != row) {
            return false;
        }
        return !inRow || p[1] == col;
    }

    @Override
    public boolean isFirstItem() {
        return inRow ? col == 0 : row == 0;
    }

    @Override
    public ScanStyle getStyle() {
        return ScanStyle.ROW_COLUMN;
    }

    /** Key at (r, c) needs r row steps plus c column steps. */
    @Override
    public int analyticSteps(Key k) {
        int[] p = position.get(k);
        return p[0] + p[1];
    }

    @Override
    public int analyticPresses(Key k) {
        return 2;
    }

    /** True while the keys of a row are being scanned (used by the painter). */
    public boolean isInRow() {
        return inRow;
    }
}
