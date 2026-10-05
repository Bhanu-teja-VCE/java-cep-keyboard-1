package com.switchtype.sim;

import com.switchtype.model.Key;
import com.switchtype.model.LayoutType;
import com.switchtype.model.PredictionMode;
import com.switchtype.model.ScanStyle;
import com.switchtype.model.Settings;
import com.switchtype.scan.KeyboardLayout;
import com.switchtype.scan.ScanFactory;
import com.switchtype.scan.ScanStrategy;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The checks of section 5 of the brief: the simulated user must behave as specified. */
class SimulationValidationTest {

    private static ExperimentRunner runner;
    private static UserParams typical;

    @BeforeAll
    static void setUp() throws Exception {
        runner = new ExperimentRunner();
        typical = UserParams.loadAll(null).get(1).withoutFatigue();
    }

    /** Analytic expectation: sum over the characters of the per-key formula. */
    private static long[] analytic(Settings s, List<String> phrases) {
        ScanStrategy strategy = ScanFactory.strategy(s.getStyle(), ScanFactory.grid(s, runner.getFrequency()),
                s.getRowCycles(), runner.getFrequency());
        long steps = 0;
        long presses = 0;
        for (String p : phrases) {
            for (char c : p.toCharArray()) {
                Key k = KeyboardLayout.findChar(strategy.getGrid(), c);
                steps += strategy.analyticSteps(k);
                presses += strategy.analyticPresses(k);
            }
        }
        return new long[]{steps, presses};
    }

    @Test
    void zeroNoiseSimulationMatchesTheAnalyticFormulaWithinOnePercent() {
        UserParams perfect = typical.perfect();
        for (ScanStyle style : ScanStyle.values()) {
            for (LayoutType layout : LayoutType.values()) {
                Settings s = new Settings(style, layout, PredictionMode.OFF, 600);
                RunResult r = runner.run(perfect, s, 1);
                long[] expected = analytic(s, runner.getPhrases().test());
                String where = style + "/" + layout;
                assertEquals(1.0, (double) r.getSteps() / expected[0], 0.01, "steps " + where);
                assertEquals(1.0, (double) r.getPresses() / expected[1], 0.01, "presses " + where);
                assertEquals(expected[0] * 600, r.getTimeMs(), "time = steps x interval when extra delay is 0");
                assertEquals(0.0, r.totalErrorRate(), 1e-9);
            }
        }
    }

    @Test
    void veryLongIntervalGivesAlmostNoErrors() {
        UserParams calm = typical.copyWith("miss", 0).copyWith("spurious", 0);
        RunResult r = runner.run(calm, new Settings(ScanStyle.ROW_COLUMN, LayoutType.FREQUENCY, PredictionMode.OFF, 2000), 7);
        assertTrue(r.totalErrorRate() < 0.01, "error rate " + r.totalErrorRate());
    }

    @Test
    void veryShortIntervalWithoutCompensationGivesManyErrors() {
        UserParams naive = typical.copyWith("compensation", 0);
        RunResult r = runner.run(naive, new Settings(ScanStyle.ROW_COLUMN, LayoutType.FREQUENCY, PredictionMode.OFF, 200), 7);
        assertTrue(r.totalErrorRate() > 0.5, "error rate " + r.totalErrorRate());
    }

    @Test
    void sameSeedGivesIdenticalResultsAndOtherSeedsClose() {
        Settings s = new Settings(ScanStyle.ROW_COLUMN, LayoutType.FREQUENCY, PredictionMode.BIGRAM, 700);
        RunResult a = runner.run(typical, s, 42);
        RunResult b = runner.run(typical, s, 42);
        RunResult c = runner.run(typical, s, 43);
        assertEquals(a.getTimeMs(), b.getTimeMs());
        assertEquals(a.getPresses(), b.getPresses());
        assertEquals(a.cpm(), b.cpm(), 0.0);
        assertNotEquals(a.getTimeMs(), c.getTimeMs(), "a different seed should differ");
        assertEquals(1.0, c.cpm() / a.cpm(), 0.15, "different seeds stay close");
    }

    @Test
    void perfectUserTypesEveryPhraseInEveryCombination() {
        UserParams perfect = typical.perfect();
        List<String> one = Collections.singletonList("can you check the weather for me?");
        for (ScanStyle style : ScanStyle.values()) {
            for (LayoutType layout : LayoutType.values()) {
                for (PredictionMode mode : PredictionMode.values()) {
                    Settings s = new Settings(style, layout, mode, 500);
                    SimulationDriver d = new SimulationDriver(s, runner.getFrequency(), runner.predictor(mode),
                            new SimulatedUser(perfect, 1));
                    RunResult r = d.typePhrases(one);
                    String where = style + "/" + layout + "/" + mode;
                    assertEquals(0, r.getUnfinished(), where);
                    assertEquals(0.0, r.uncorrectedErrorRate(), 1e-9, where);
                    if (mode != PredictionMode.OFF) {
                        assertTrue(r.getSelections() < r.getTargetChars(), "prediction saves selections: " + where);
                    }
                }
            }
        }
    }
}
