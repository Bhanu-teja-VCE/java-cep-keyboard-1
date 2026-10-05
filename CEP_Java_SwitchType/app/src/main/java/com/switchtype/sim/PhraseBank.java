package com.switchtype.sim;

import com.switchtype.exception.CorpusFormatException;
import com.switchtype.predict.CorpusLoader;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * The built-in phrases, split into TEST phrases (even positions, used for every
 * measurement) and TRAIN phrases (odd positions, used only to train the language model).
 * The split is fixed so every experiment uses the same held-out phrases.
 */
public class PhraseBank {

    private final List<String> all;
    private final List<String> test = new ArrayList<String>();
    private final List<String> train = new ArrayList<String>();

    public PhraseBank() throws CorpusFormatException {
        this(CorpusLoader.readResourceLines(CorpusLoader.PHRASES_RESOURCE));
    }

    public PhraseBank(List<String> phrases) {
        this.all = new ArrayList<String>(phrases);
        for (int i = 0; i < all.size(); i++) {
            (i % 2 == 0 ? test : train).add(all.get(i));
        }
    }

    public List<String> all() {
        return all;
    }

    public List<String> test() {
        return test;
    }

    public List<String> train() {
        return train;
    }

    /** A practice queue in a fixed order, consumed from the front (Unit III: LinkedList). */
    public LinkedList<String> practiceQueue() {
        return new LinkedList<String>(test);
    }
}
