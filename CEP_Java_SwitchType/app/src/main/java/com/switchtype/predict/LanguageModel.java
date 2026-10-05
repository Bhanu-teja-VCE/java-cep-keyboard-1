package com.switchtype.predict;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Word counts. Unigram counts live in a TreeMap so all words with a given prefix are one
 * contiguous range (subMap). Bigram counts are a HashMap from the previous word to a
 * HashMap of following words (Unit III: HashMap, TreeMap). Serializable so the trained
 * model can be saved and loaded quickly.
 */
public class LanguageModel implements Serializable {

    private static final long serialVersionUID = 1L;

    private final TreeMap<String, Integer> unigrams = new TreeMap<String, Integer>();
    private final HashMap<String, HashMap<String, Integer>> bigrams = new HashMap<String, HashMap<String, Integer>>();
    private long tokens;

    /** Adds one occurrence of word following previous (previous may be START). */
    public void add(String previous, String word) {
        increment(unigrams, word);
        HashMap<String, Integer> next = bigrams.get(previous);
        if (next == null) {
            next = new HashMap<String, Integer>();
            bigrams.put(previous, next);
        }
        increment(next, word);
        tokens++;
    }

    private static void increment(Map<String, Integer> map, String key) {
        Integer old = map.get(key);
        map.put(key, old == null ? 1 : old + 1);
    }

    public int count(String word) {
        Integer n = unigrams.get(word);
        return n == null ? 0 : n;
    }

    public int bigramCount(String previous, String word) {
        HashMap<String, Integer> next = bigrams.get(previous);
        if (next == null) {
            return 0;
        }
        Integer n = next.get(word);
        return n == null ? 0 : n;
    }

    /** All words that start with prefix, in alphabetical order, with their counts. */
    public SortedMap<String, Integer> withPrefix(String prefix) {
        return unigrams.subMap(prefix, prefix + Character.MAX_VALUE);
    }

    /** Words seen after previous, or null. */
    public Map<String, Integer> followers(String previous) {
        return bigrams.get(previous);
    }

    public int vocabularySize() {
        return unigrams.size();
    }

    public long getTokens() {
        return tokens;
    }

    /** A deep copy, so experiments can never change the shared model. */
    public LanguageModel copy() {
        LanguageModel c = new LanguageModel();
        c.unigrams.putAll(unigrams);
        for (Map.Entry<String, HashMap<String, Integer>> e : bigrams.entrySet()) {
            c.bigrams.put(e.getKey(), new HashMap<String, Integer>(e.getValue()));
        }
        c.tokens = tokens;
        return c;
    }
}
