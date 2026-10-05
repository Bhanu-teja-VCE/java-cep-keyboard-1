package com.switchtype.model;

/** The three interchangeable scanning methods. */
public enum ScanStyle {
    LINEAR("Linear"),
    ROW_COLUMN("Row-column"),
    GROUP_BINARY("Group-binary");

    private final String label;

    ScanStyle(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
