package com.switchtype.scan;

import com.switchtype.model.LayoutType;
import com.switchtype.predict.CharFrequency;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Most frequent characters first. In row-column scanning a key at (row, col) costs
 * row + col steps, so the characters are placed along diagonals: the most frequent one at
 * (0,0), the next two at (0,1) and (1,0), and so on.
 */
public class FrequencyLayout extends KeyboardLayout {

    static final int ROWS = 4;
    static final int COLS = 8;

    private final CharFrequency frequency;

    public FrequencyLayout(CharFrequency frequency) {
        this.frequency = frequency;
    }

    @Override
    protected char[][] textRows() {
        char[] sorted = frequency.sortByFrequency(TEXT_CHARS);

        // all cells of a 4 x 8 grid, cheapest first: by row + col, then by row
        List<int[]> cells = new ArrayList<int[]>();
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                cells.add(new int[]{r, c});
            }
        }
        Collections.sort(cells, new Comparator<int[]>() {
            @Override
            public int compare(int[] a, int[] b) {
                int byCost = Integer.compare(a[0] + a[1], b[0] + b[1]);
                return byCost != 0 ? byCost : Integer.compare(a[0], b[0]);
            }
        });

        // 31 characters fill 31 of the 32 cells; the last cell (3,7) stays empty
        char[][] grid = new char[ROWS][COLS];
        for (int i = 0; i < sorted.length; i++) {
            int[] cell = cells.get(i);
            grid[cell[0]][cell[1]] = sorted[i];
        }
        char[][] rows = new char[ROWS][];
        for (int r = 0; r < ROWS; r++) {
            int length = (r == ROWS - 1) ? COLS - 1 : COLS;
            rows[r] = new char[length];
            System.arraycopy(grid[r], 0, rows[r], 0, length);
        }
        return rows;
    }

    @Override
    public LayoutType getType() {
        return LayoutType.FREQUENCY;
    }
}
