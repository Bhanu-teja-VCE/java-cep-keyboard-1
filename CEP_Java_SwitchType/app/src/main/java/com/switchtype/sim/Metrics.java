package com.switchtype.sim;

/** Metric formulas (CEP_ANALYSIS.md, Method). Static helpers only. */
public final class Metrics {

    private Metrics() {
    }

    /** Levenshtein edit distance: the minimum number of insertions, deletions and substitutions. */
    public static int levenshtein(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int substitute = previous[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
                current[j] = Math.min(substitute, Math.min(previous[j] + 1, current[j - 1] + 1));
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }

    /** Correct characters per minute. */
    public static double cpm(double correctChars, double timeMs) {
        return timeMs <= 0 ? 0 : correctChars * 60000.0 / timeMs;
    }

    /** Characters of the target that ended up correct: |target| - L(final, target), at least 0. */
    public static int correctChars(String finalText, String target) {
        return Math.max(0, target.length() - levenshtein(finalText, target));
    }
}
