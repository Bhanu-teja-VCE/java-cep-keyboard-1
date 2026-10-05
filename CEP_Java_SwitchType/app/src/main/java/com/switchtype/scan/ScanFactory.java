package com.switchtype.scan;

import com.switchtype.model.Key;
import com.switchtype.model.LayoutType;
import com.switchtype.model.PredictionMode;
import com.switchtype.model.ScanStyle;
import com.switchtype.model.Settings;
import com.switchtype.predict.CharFrequency;

/** Creates layouts, grids and strategies from settings (one place for the switch statements). */
public final class ScanFactory {

    private ScanFactory() {
    }

    public static KeyboardLayout layout(LayoutType type, CharFrequency frequency) {
        switch (type) {
            case QWERTY:
                return new QwertyLayout();
            case FREQUENCY:
                return new FrequencyLayout(frequency);
            default:
                return new AlphabeticalLayout();
        }
    }

    /** The typing grid for these settings (prediction row only when prediction is on). */
    public static Key[][] grid(Settings s, CharFrequency frequency) {
        return layout(s.getLayout(), frequency).buildGrid(s.getPrediction() != PredictionMode.OFF);
    }

    public static ScanStrategy strategy(ScanStyle style, Key[][] grid, int rowCycles, CharFrequency frequency) {
        switch (style) {
            case LINEAR:
                return new LinearScan(grid);
            case GROUP_BINARY:
                return new GroupBinaryScan(grid, rowCycles, frequency);
            default:
                return new RowColumnScan(grid, rowCycles);
        }
    }

    /** A ready engine for these settings. */
    public static ScanEngine engine(Settings s, CharFrequency frequency) {
        ScanStrategy strategy = strategy(s.getStyle(), grid(s, frequency), s.getRowCycles(), frequency);
        ScanEngine engine = new ScanEngine(strategy, s.getIntervalMs(), s.getFirstExtraPct());
        engine.start();
        return engine;
    }
}
