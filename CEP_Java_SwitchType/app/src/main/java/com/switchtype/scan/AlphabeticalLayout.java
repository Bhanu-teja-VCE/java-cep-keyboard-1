package com.switchtype.scan;

import com.switchtype.model.LayoutType;

/** Space first, then a to z in rows of eight, then punctuation and newline. */
public class AlphabeticalLayout extends KeyboardLayout {

    @Override
    protected char[][] textRows() {
        return rowsOf(" abcdefg", "hijklmno", "pqrstuvw", "xyz.,?\n");
    }

    @Override
    public LayoutType getType() {
        return LayoutType.ALPHABETICAL;
    }
}
