package com.switchtype.scan;

import com.switchtype.model.LayoutType;

/** The familiar QWERTY rows, with space and newline on a bottom row like a real keyboard. */
public class QwertyLayout extends KeyboardLayout {

    @Override
    protected char[][] textRows() {
        return rowsOf("qwertyuiop", "asdfghjkl", "zxcvbnm,.?", " \n");
    }

    @Override
    public LayoutType getType() {
        return LayoutType.QWERTY;
    }
}
