package com.switchtype.predict;

import com.switchtype.model.PredictionMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * First suggests words that followed the previous word in the corpus, then fills the
 * remaining slots with unigram suggestions (Unit I: inheritance and overriding).
 */
public class BigramPredictor extends UnigramPredictor {

    public BigramPredictor(LanguageModel model) {
        super(model);
    }

    @Override
    public List<String> predict(String previousWord, String prefix, int k) {
        List<String> result = new ArrayList<String>();
        Map<String, Integer> followers = model.followers(previousWord);
        if (followers != null) {
            addBest(followers.entrySet(), prefix, k, result);
        }
        if (result.size() < k) {
            addBest(model.withPrefix(prefix).entrySet(), prefix, k, result);
        }
        return result;
    }

    @Override
    public PredictionMode getMode() {
        return PredictionMode.BIGRAM;
    }
}
