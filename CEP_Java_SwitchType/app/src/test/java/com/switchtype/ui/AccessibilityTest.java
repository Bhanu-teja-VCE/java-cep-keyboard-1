package com.switchtype.ui;

import com.switchtype.model.Config;
import com.switchtype.model.Key;
import com.switchtype.model.LayoutType;
import com.switchtype.model.ScanStyle;
import com.switchtype.predict.CharFrequency;
import com.switchtype.predict.CorpusLoader;
import com.switchtype.scan.ScanEngine;
import com.switchtype.scan.ScanFactory;
import com.switchtype.scan.SettingsKeyboard;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** WCAG 2.x used as a design benchmark: contrast, target size and flashing. */
class AccessibilityTest {

    @Test
    void contrastFormulaMatchesKnownValues() {
        assertEquals(21.0, Theme.contrast(Color.BLACK, Color.WHITE), 0.01);
        assertEquals(1.0, Theme.contrast(Color.RED, Color.RED), 1e-9);
        assertEquals(4.54, Theme.contrast(new Color(0x767676), Color.WHITE), 0.01);
    }

    @Test
    void everyThemeMeetsTextAndIndicatorContrast() {
        for (Theme t : new Theme[]{Theme.NORMAL, Theme.HIGH_CONTRAST}) {
            for (Color[] pair : t.textPairs()) {
                double ratio = Theme.contrast(pair[0], pair[1]);
                assertTrue(ratio >= 4.5, t.name + " text " + pair[0] + " on " + pair[1] + " = " + ratio);
            }
            for (Color[] pair : t.indicatorPairs()) {
                double ratio = Theme.contrast(pair[0], pair[1]);
                assertTrue(ratio >= 3.0, t.name + " indicator " + pair[0] + " vs " + pair[1] + " = " + ratio);
            }
        }
    }

    @Test
    void keysAreAtLeast44PixelsAtTheDefaultSize() throws Exception {
        CharFrequency f = CorpusLoader.frequencies(CorpusLoader.readResourceLines(CorpusLoader.CORPUS_RESOURCE));
        List<Key[][]> grids = new ArrayList<Key[][]>();
        for (LayoutType type : LayoutType.values()) {
            grids.add(ScanFactory.layout(type, f).buildGrid(true));
        }
        grids.add(SettingsKeyboard.grid());
        for (Key[][] grid : grids) {
            for (Rectangle r : KeyboardView.keyBounds(grid, Config.DEFAULT_KEY_SIZE).values()) {
                assertTrue(r.width >= 44 && r.height >= 44, "key too small: " + r);
            }
        }
        assertTrue(Config.MIN_KEY_SIZE >= 44, "the smallest allowed key size is still 44 px");
    }

    /**
     * WCAG 2.3.1: no key may light up more than three times in any one-second window, even
     * at the fastest allowed interval, in every style, before and after entering a row/group.
     */
    @Test
    void nothingFlashesMoreThanThreeTimesPerSecond() throws Exception {
        CharFrequency f = CorpusLoader.frequencies(CorpusLoader.readResourceLines(CorpusLoader.CORPUS_RESOURCE));
        List<Key[][]> grids = new ArrayList<Key[][]>();
        for (LayoutType type : LayoutType.values()) {
            grids.add(ScanFactory.layout(type, f).buildGrid(true));
        }
        grids.add(SettingsKeyboard.grid());
        for (ScanStyle style : ScanStyle.values()) {
            for (Key[][] grid : grids) {
                for (int pressesFirst = 0; pressesFirst <= 2; pressesFirst++) {
                    ScanEngine engine = new ScanEngine(ScanFactory.strategy(style, grid, 5, f), Config.MIN_INTERVAL_MS, 0);
                    engine.start();
                    for (int i = 0; i < pressesFirst; i++) {
                        engine.onPress();                 // go into a row / group (may also select a key)
                    }
                    int worst = maxOnsetsPerSecond(engine, 100);
                    assertTrue(worst <= 3, style + ": a key lit " + worst + " times in one second");
                }
            }
        }
    }

    /** Steps the engine and counts, per key, how often it turns on within any 1000 ms window. */
    private static int maxOnsetsPerSecond(ScanEngine engine, int steps) {
        Map<Key, List<Long>> onsets = new HashMap<Key, List<Long>>();
        Set<Key> before = new HashSet<Key>();
        long t = 0;
        for (int i = 0; i < steps; i++) {
            Set<Key> now = new HashSet<Key>(engine.highlighted());
            for (Key k : now) {
                if (!before.contains(k)) {
                    if (!onsets.containsKey(k)) {
                        onsets.put(k, new ArrayList<Long>());
                    }
                    onsets.get(k).add(t);
                }
            }
            before = now;
            t += engine.nextDelayMs();
            engine.onStep();
        }
        int worst = 0;
        for (List<Long> times : onsets.values()) {
            for (int a = 0; a < times.size(); a++) {
                int count = 0;
                for (int b = a; b < times.size() && times.get(b) - times.get(a) < 1000; b++) {
                    count++;
                }
                worst = Math.max(worst, count);
            }
        }
        return worst;
    }
}
