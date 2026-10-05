package com.switchtype.predict;

import com.switchtype.model.PredictionMode;

import java.util.Collections;
import java.util.List;

/** Prediction switched off: never suggests anything. */
public class NoPredictor implements WordPredictor {

    @Override
    public List<String> predict(String previousWord, String prefix, int k) {
        return Collections.emptyList();
    }

    @Override
    public void learn(String previousWord, String word) {
        // nothing to learn
    }

    @Override
    public PredictionMode getMode() {
        return PredictionMode.OFF;
    }
}
