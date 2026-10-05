package com.switchtype.model;

/** Word prediction mode. */
public enum PredictionMode {
    OFF("Off"),
    UNIGRAM("Unigram"),
    BIGRAM("Bigram");

    private final String label;

    PredictionMode(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
