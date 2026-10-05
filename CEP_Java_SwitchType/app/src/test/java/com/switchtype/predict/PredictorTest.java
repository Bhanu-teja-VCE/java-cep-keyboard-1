package com.switchtype.predict;

import com.switchtype.exception.CorpusFormatException;
import com.switchtype.model.Key;
import com.switchtype.model.KeyAction;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PredictorTest {

    private static LanguageModel small() {
        LanguageModel m = new LanguageModel();
        CorpusLoader.train(m, Arrays.asList(
                "please call my sister.", "please call the doctor.", "please call the nurse.",
                "the doctor is here.", "i want tea.", "i want water.", "i want water now."));
        return m;
    }

    @Test
    void noPredictorNeverSuggests() {
        assertTrue(new NoPredictor().predict("please", "", 4).isEmpty());
    }

    @Test
    void unigramRanksByCountWithinThePrefix() {
        UnigramPredictor p = new UnigramPredictor(small());
        List<String> words = p.predict(WordPredictor.START, "w", 4);
        assertEquals(Arrays.asList("want", "water"), words);       // want 3, water 2
        assertEquals("the", p.predict("call", "t", 1).get(0), "unigram ignores the previous word");
    }

    @Test
    void bigramUsesThePreviousWordFirst() {
        BigramPredictor p = new BigramPredictor(small());
        assertEquals("call", p.predict("please", "", 1).get(0));
        assertEquals(Arrays.asList("the", "my"), p.predict("call", "", 2));
        assertEquals("doctor", p.predict("the", "d", 1).get(0));
        // unknown previous word: falls back to unigram counts
        assertEquals("want", p.predict("zebra", "w", 1).get(0));
    }

    @Test
    void learningChangesTheRanking() {
        UnigramPredictor p = new UnigramPredictor(small());
        for (int i = 0; i < 5; i++) {
            p.learn(WordPredictor.START, "walk");
        }
        assertEquals("walk", p.predict(WordPredictor.START, "w", 1).get(0));
    }

    @Test
    void composerCompletesWordsAndUndoes() {
        TextComposer c = new TextComposer(new BigramPredictor(small()), false);
        c.apply(Key.character('i'));
        c.apply(Key.character(' '));
        assertEquals("i", c.previousWord());
        assertEquals("want", c.getPrediction(0));
        c.apply(Key.character('w'));
        c.apply(Key.prediction(0));                      // "want "
        assertEquals("i want ", c.getText());
        c.apply(Key.character('t'));
        c.apply(Key.character('e'));
        c.apply(Key.control(KeyAction.BACKSPACE, "delete"));
        assertEquals("i want t", c.getText());
        c.apply(Key.control(KeyAction.UNDO_WORD, "undo"));
        assertEquals("i want ", c.getText());
        c.apply(Key.control(KeyAction.UNDO_WORD, "undo"));
        assertEquals("i ", c.getText());
    }

    @Test
    void sentenceEndResetsTheContext() {
        TextComposer c = new TextComposer(new BigramPredictor(small()), false);
        for (char ch : "i want tea. ".toCharArray()) {
            c.apply(Key.character(ch));
        }
        assertEquals(WordPredictor.START, c.previousWord());
    }

    @Test
    void emptyPredictionSlotDoesNothing() {
        TextComposer c = new TextComposer(new NoPredictor(), false);
        assertEquals(false, c.apply(Key.prediction(2)));
    }

    @Test
    void badCorpusFilesAreRejected() throws Exception {
        File empty = File.createTempFile("empty", ".txt");
        empty.deleteOnExit();
        assertThrows(CorpusFormatException.class, () -> CorpusLoader.readFileLines(empty));
        File binary = File.createTempFile("binary", ".bin");
        binary.deleteOnExit();
        try (FileOutputStream out = new FileOutputStream(binary)) {
            out.write(new byte[]{'a', 0, 1, 2, '\n'});
        }
        assertThrows(CorpusFormatException.class, () -> CorpusLoader.readFileLines(binary));
    }

    @Test
    void vocabularyImportAddsWords() throws Exception {
        File vocab = File.createTempFile("vocab", ".txt");
        vocab.deleteOnExit();
        try (FileWriter w = new FileWriter(vocab)) {
            w.write("physiotherapy session tomorrow\nphysiotherapy again\n");
        }
        LanguageModel m = small();
        assertEquals(5, CorpusLoader.importVocabulary(m, vocab));
        assertEquals(2, m.count("physiotherapy"));
    }
}
