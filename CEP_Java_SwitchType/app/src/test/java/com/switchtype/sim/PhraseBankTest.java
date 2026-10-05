package com.switchtype.sim;

import com.switchtype.predict.CorpusLoader;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhraseBankTest {

    @Test
    void phrasesFollowTheRules() throws Exception {
        PhraseBank bank = new PhraseBank();
        assertTrue(bank.all().size() >= 40, "at least 40 phrases");
        for (String p : bank.all()) {
            assertTrue(p.length() >= 15 && p.length() <= 40, "length 15..40: " + p);
            assertTrue(p.matches("[a-z0-9 .,?]+"), "only typeable characters: " + p);
        }
        assertEquals(bank.all().size(), new HashSet<String>(bank.all()).size(), "no duplicates");
    }

    @Test
    void testAndTrainPhrasesAreDisjointAndNotInTheCorpus() throws Exception {
        PhraseBank bank = new PhraseBank();
        HashSet<String> train = new HashSet<String>(bank.train());
        List<String> corpus = CorpusLoader.readResourceLines(CorpusLoader.CORPUS_RESOURCE);
        assertTrue(corpus.size() >= 300, "corpus has several hundred sentences: " + corpus.size());
        StringBuilder all = new StringBuilder();
        for (String line : corpus) {
            all.append(line).append('\n');
        }
        for (String p : bank.test()) {
            assertFalse(train.contains(p));
            assertFalse(all.toString().contains(p), "test phrase leaked into the corpus: " + p);
        }
    }
}
