package com.switchtype.predict;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;

/**
 * How often each character occurs in the corpus. Used by the frequency layout and to
 * build the Huffman tree of group-binary scanning.
 */
public class CharFrequency implements Serializable {

    private static final long serialVersionUID = 1L;

    private final HashMap<Character, Integer> counts = new HashMap<Character, Integer>();
    private long total;

    /** Counts every character of the text (lower-cased). */
    public void addText(String text) {
        String lower = text.toLowerCase();
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            Integer old = counts.get(c);
            counts.put(c, old == null ? 1 : old + 1);
            total++;
        }
    }

    public int count(char c) {
        Integer n = counts.get(c);
        return n == null ? 0 : n;
    }

    public long getTotal() {
        return total;
    }

    /**
     * Returns the given characters sorted from most to least frequent. Ties keep the
     * order of the input string, so the result is always the same (Unit III: Arrays.sort).
     */
    public char[] sortByFrequency(final String chars) {
        Character[] boxed = new Character[chars.length()];
        for (int i = 0; i < boxed.length; i++) {
            boxed[i] = chars.charAt(i);
        }
        Arrays.sort(boxed, new Comparator<Character>() {
            @Override
            public int compare(Character a, Character b) {
                int byCount = Integer.compare(count(b), count(a));
                return byCount != 0 ? byCount : Integer.compare(chars.indexOf(a), chars.indexOf(b));
            }
        });
        char[] result = new char[boxed.length];
        for (int i = 0; i < boxed.length; i++) {
            result[i] = boxed[i];
        }
        return result;
    }
}
