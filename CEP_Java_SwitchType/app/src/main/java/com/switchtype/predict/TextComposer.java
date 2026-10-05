package com.switchtype.predict;

import com.switchtype.model.Config;
import com.switchtype.model.Key;

import java.util.List;

/**
 * The text being written and the current predictions. Applies selected keys to the text.
 * Used by the live app (confined to the Swing thread) and by the simulator, so both edit
 * text in exactly the same way. The text is a StringBuffer (Unit II: StringBuffer).
 */
public class TextComposer {

    private final StringBuffer text = new StringBuffer();
    private final String[] predictions = new String[Config.PREDICTION_SLOTS];
    private WordPredictor predictor;
    private boolean learning;

    public TextComposer(WordPredictor predictor, boolean learning) {
        this.predictor = predictor;
        this.learning = learning;
        refreshPredictions();
    }

    /**
     * Applies a selected key to the text. Control keys that do not edit text (Pause,
     * Settings ...) are ignored here.
     *
     * @return true if the text changed
     */
    public boolean apply(Key key) {
        boolean changed;
        switch (key.getAction()) {
            case CHAR:
                if (learning && isSeparator(key.getChar())) {
                    learnCurrentWord();
                }
                text.append(key.getChar());
                changed = true;
                break;
            case BACKSPACE:
                changed = text.length() > 0;
                if (changed) {
                    text.setLength(text.length() - 1);
                }
                break;
            case UNDO_WORD:
                changed = undoWord();
                break;
            case PREDICTION:
                changed = acceptPrediction(key.getSlot());
                break;
            default:
                changed = false;
        }
        if (changed) {
            refreshPredictions();
        }
        return changed;
    }

    /** Replaces the word being typed by the prediction in the slot, followed by a space. */
    private boolean acceptPrediction(int slot) {
        String word = predictions[slot];
        if (word == null) {
            return false;                            // an empty slot does nothing
        }
        if (learning) {
            predictor.learn(previousWord(), word);
        }
        text.setLength(text.length() - currentPrefix().length());
        text.append(word).append(' ');
        return true;
    }

    /** Removes trailing spaces and then the last word (punctuation counts as part of it). */
    private boolean undoWord() {
        int end = text.length();
        while (end > 0 && isSpace(text.charAt(end - 1))) {
            end--;
        }
        while (end > 0 && !isSpace(text.charAt(end - 1))) {
            end--;
        }
        boolean changed = end < text.length();
        text.setLength(end);
        return changed;
    }

    private void learnCurrentWord() {
        String word = currentPrefix();
        if (!word.isEmpty()) {
            predictor.learn(previousWord(), word);
        }
    }

    /** Recomputes the prediction slots for the current prefix and previous word. */
    public final void refreshPredictions() {
        List<String> words = predictor.predict(previousWord(), currentPrefix(), predictions.length);
        for (int i = 0; i < predictions.length; i++) {
            predictions[i] = i < words.size() ? words.get(i) : null;
        }
    }

    /** The letters of the word being typed (after the last space or punctuation). */
    public String currentPrefix() {
        int start = text.length();
        while (start > 0 && isWordChar(text.charAt(start - 1))) {
            start--;
        }
        return text.substring(start);
    }

    /** The word before the current one, or START at the beginning of a sentence. */
    public String previousWord() {
        int i = text.length() - currentPrefix().length();
        while (i > 0 && (text.charAt(i - 1) == ' ' || text.charAt(i - 1) == ',')) {
            i--;
        }
        if (i == 0 || !isWordChar(text.charAt(i - 1))) {
            return WordPredictor.START;              // start of text, or after . ? or newline
        }
        int end = i;
        while (i > 0 && isWordChar(text.charAt(i - 1))) {
            i--;
        }
        return text.substring(i, end);
    }

    private static boolean isWordChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
    }

    private static boolean isSpace(char c) {
        return c == ' ' || c == '\n';
    }

    private static boolean isSeparator(char c) {
        return !isWordChar(c);
    }

    /** A copy of the current predictions (null = empty slot). */
    public String[] getPredictions() {
        return predictions.clone();
    }

    public String getPrediction(int slot) {
        return predictions[slot];
    }

    public String getText() {
        return text.toString();
    }

    public int length() {
        return text.length();
    }

    public void clear() {
        text.setLength(0);
        refreshPredictions();
    }

    public void setPredictor(WordPredictor predictor) {
        this.predictor = predictor;
        refreshPredictions();
    }

    public WordPredictor getPredictor() {
        return predictor;
    }
}
