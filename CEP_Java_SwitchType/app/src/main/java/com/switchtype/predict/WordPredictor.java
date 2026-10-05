package com.switchtype.predict;

import com.switchtype.model.PredictionMode;

import java.util.List;

/** Suggests words for the prediction row (Unit I: interface with three implementations). */
public interface WordPredictor {

    /** Marks the start of a sentence when there is no previous word. */
    String START = "<s>";

    /**
     * Returns at most k words that start with the prefix, best first.
     *
     * @param previousWord the word before the current one, or {@link #START}
     * @param prefix       the letters of the current word typed so far (may be empty)
     */
    List<String> predict(String previousWord, String prefix, int k);

    /** Learns that the user accepted or typed this word after previousWord. */
    void learn(String previousWord, String word);

    PredictionMode getMode();
}
