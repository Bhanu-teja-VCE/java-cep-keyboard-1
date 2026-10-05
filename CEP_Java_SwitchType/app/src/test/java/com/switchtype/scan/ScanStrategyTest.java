package com.switchtype.scan;

import com.switchtype.model.Key;
import com.switchtype.model.KeyAction;
import com.switchtype.model.LayoutType;
import com.switchtype.model.Outcome;
import com.switchtype.model.ScanStyle;
import com.switchtype.predict.CharFrequency;
import com.switchtype.predict.CorpusLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every key of every layout, with and without the prediction row, must be selected by a
 * perfect user in exactly the analytic number of steps and presses, in every strategy.
 */
class ScanStrategyTest {

    private static CharFrequency frequency;

    @BeforeAll
    static void loadCorpus() throws Exception {
        frequency = CorpusLoader.frequencies(CorpusLoader.readResourceLines(CorpusLoader.CORPUS_RESOURCE));
    }

    /** Steps the engine like a perfect user: press as soon as the key is highlighted. */
    private static int[] selectPerfectly(ScanEngine engine, Key target) {
        engine.start();
        int steps = 0;
        int presses = 0;
        while (true) {
            if (engine.isHighlighted(target)) {
                Outcome o = engine.onPress();
                presses++;
                if (o.isSelection()) {
                    assertSame(target, o.getKey(), "wrong key selected");
                    return new int[]{steps, presses};
                }
            } else {
                engine.onStep();
                steps++;
            }
            assertTrue(steps < 10000, "never reached " + target);
        }
    }

    @Test
    void everyKeyMatchesAnalyticStepsInEveryStyleAndLayout() {
        for (ScanStyle style : ScanStyle.values()) {
            for (LayoutType type : LayoutType.values()) {
                for (boolean predictionRow : new boolean[]{false, true}) {
                    Key[][] grid = ScanFactory.layout(type, frequency).buildGrid(predictionRow);
                    ScanStrategy strategy = ScanFactory.strategy(style, grid, 2, frequency);
                    ScanEngine engine = new ScanEngine(strategy, 500, 0);
                    for (Key[] row : grid) {
                        for (Key k : row) {
                            int[] measured = selectPerfectly(engine, k);
                            String where = style + "/" + type + "/" + k;
                            assertEquals(strategy.analyticSteps(k), measured[0], "steps " + where);
                            assertEquals(strategy.analyticPresses(k), measured[1], "presses " + where);
                        }
                    }
                }
            }
        }
    }

    @Test
    void linearStepsEqualPositionInRowMajorOrder() {
        Key[][] grid = new AlphabeticalLayout().buildGrid(false);
        LinearScan scan = new LinearScan(grid);
        assertEquals(0, scan.analyticSteps(grid[0][0]));
        assertEquals(8 + 3, scan.analyticSteps(grid[1][3]));   // first row has 8 keys
    }

    @Test
    void rowColumnStepsAreRowPlusColumn() {
        Key[][] grid = new QwertyLayout().buildGrid(false);
        RowColumnScan scan = new RowColumnScan(grid, 2);
        assertEquals(2 + 4, scan.analyticSteps(grid[2][4]));
        assertEquals(2, scan.analyticPresses(grid[2][4]));
    }

    @Test
    void rowColumnGoesBackToRowsAfterUnusedCycles() {
        Key[][] grid = new AlphabeticalLayout().buildGrid(false);
        RowColumnScan scan = new RowColumnScan(grid, 2);
        scan.reset();
        scan.step();                                  // row 1
        assertEquals(Outcome.Kind.NARROWED, scan.press().getKind());
        boolean gaveUp = false;
        for (int i = 0; i < 2 * grid[1].length; i++) {
            gaveUp = scan.step();
        }
        assertTrue(gaveUp, "should give up after two full cycles of the row");
        assertFalse(scan.isInRow());
        assertTrue(scan.isHighlighted(grid[0][0]), "back at the first row");
    }

    @Test
    void groupBinaryPutsFrequentLettersHigherInTheTree() {
        Key[][] grid = new FrequencyLayout(frequency).buildGrid(false);
        GroupBinaryScan scan = new GroupBinaryScan(grid, 2, frequency);
        Key e = KeyboardLayout.findChar(grid, 'e');
        Key z = KeyboardLayout.findChar(grid, 'z');
        Key space = KeyboardLayout.findChar(grid, ' ');
        assertTrue(scan.analyticPresses(e) < scan.analyticPresses(z), "e must need fewer presses than z");
        assertTrue(scan.analyticPresses(space) <= scan.analyticPresses(e));
    }

    @Test
    void groupBinaryGivesUpOnAWrongGroup() {
        Key[][] grid = new AlphabeticalLayout().buildGrid(false);
        GroupBinaryScan scan = new GroupBinaryScan(grid, 1, frequency);
        scan.reset();
        assertEquals(Outcome.Kind.NARROWED, scan.press().getKind());
        scan.step();
        assertTrue(scan.step(), "one unused cycle (A then B) returns to the root");
    }

    @Test
    void firstItemGetsExtraDelay() {
        Key[][] grid = new AlphabeticalLayout().buildGrid(false);
        ScanEngine engine = new ScanEngine(new RowColumnScan(grid, 2), 400, 50);
        engine.start();
        assertEquals(600, engine.nextDelayMs());     // first row: 400 + 50 %
        engine.onStep();
        assertEquals(400, engine.nextDelayMs());
    }

    @Test
    void selectingPauseStopsScanningUntilTheNextPress() {
        Key[][] grid = new AlphabeticalLayout().buildGrid(false);
        ScanEngine engine = new ScanEngine(new LinearScan(grid), 400, 0);
        Key pause = KeyboardLayout.find(grid, KeyAction.PAUSE, 0);
        selectPerfectly(engine, pause);
        assertTrue(engine.isPaused());
        assertTrue(engine.highlighted().isEmpty());
        engine.onStep();
        assertTrue(engine.isPaused(), "steps do nothing while paused");
        assertEquals(Outcome.Kind.RESUMED, engine.onPress().getKind());
        assertFalse(engine.isPaused());
    }
}
