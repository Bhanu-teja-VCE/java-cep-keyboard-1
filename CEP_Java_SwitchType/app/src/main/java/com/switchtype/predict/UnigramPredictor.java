package com.switchtype.predict;

import com.switchtype.model.PredictionMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Suggests the most frequent words that start with the typed prefix. */
public class UnigramPredictor implements WordPredictor {

    protected final LanguageModel model;

    public UnigramPredictor(LanguageModel model) {
        this.model = model;
    }

    @Override
    public List<String> predict(String previousWord, String prefix, int k) {
        List<String> result = new ArrayList<String>();
        addBest(model.withPrefix(prefix).entrySet(), prefix, k, result);
        return result;
    }

    /**
     * Adds the best-counted words of the candidates to result until it holds k words.
     * One pass that keeps a small sorted list, instead of sorting every candidate.
     * Ties are broken alphabetically so the output is always the same.
     */
    protected static void addBest(Iterable<Map.Entry<String, Integer>> candidates, String prefix,
                                  int k, List<String> result) {
        List<Map.Entry<String, Integer>> best = new ArrayList<Map.Entry<String, Integer>>();
        int wanted = k - result.size();
        for (Map.Entry<String, Integer> e : candidates) {
            String word = e.getKey();
            if (!word.startsWith(prefix) || result.contains(word)) {
                continue;
            }
            int at = best.size();
            while (at > 0 && better(e, best.get(at - 1))) {
                at--;
            }
            if (at < wanted) {
                best.add(at, e);
                if (best.size() > wanted) {
                    best.remove(best.size() - 1);
                }
            }
        }
        for (Map.Entry<String, Integer> e : best) {
            result.add(e.getKey());
        }
    }

    private static boolean better(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) {
        int byCount = a.getValue().compareTo(b.getValue());
        return byCount > 0 || (byCount == 0 && a.getKey().compareTo(b.getKey()) < 0);
    }

    @Override
    public void learn(String previousWord, String word) {
        model.add(previousWord, word);
    }

    @Override
    public PredictionMode getMode() {
        return PredictionMode.UNIGRAM;
    }
}
