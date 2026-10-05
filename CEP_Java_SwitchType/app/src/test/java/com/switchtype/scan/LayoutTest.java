package com.switchtype.scan;

import com.switchtype.model.Config;
import com.switchtype.model.Key;
import com.switchtype.model.KeyAction;
import com.switchtype.model.LayoutType;
import com.switchtype.predict.CharFrequency;
import com.switchtype.predict.CorpusLoader;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LayoutTest {

    @Test
    void everyLayoutHasEveryRequiredKeyExactlyOnce() throws Exception {
        CharFrequency f = CorpusLoader.frequencies(CorpusLoader.readResourceLines(CorpusLoader.CORPUS_RESOURCE));
        for (LayoutType type : LayoutType.values()) {
            Key[][] grid = ScanFactory.layout(type, f).buildGrid(true);
            HashSet<Character> chars = new HashSet<Character>();
            int charKeys = 0;
            for (Key[] row : grid) {
                for (Key k : row) {
                    if (k.isChar()) {
                        chars.add(k.getChar());
                        charKeys++;
                    }
                }
            }
            String required = KeyboardLayout.TEXT_CHARS + KeyboardLayout.DIGITS;
            assertEquals(required.length(), charKeys, type + ": no duplicates");
            for (char c : required.toCharArray()) {
                assertTrue(chars.contains(c), type + " is missing '" + c + "'");
            }
            for (KeyAction a : new KeyAction[]{KeyAction.BACKSPACE, KeyAction.UNDO_WORD, KeyAction.PAUSE, KeyAction.SETTINGS}) {
                assertNotNull(KeyboardLayout.find(grid, a, 0), type + " is missing " + a);
            }
            assertEquals(Config.PREDICTION_SLOTS, grid[0].length, "prediction row first");
            assertEquals(KeyAction.PREDICTION, grid[0][0].getAction());
        }
    }

    @Test
    void qwertyKeepsTheFamiliarRows() {
        Key[][] grid = new QwertyLayout().buildGrid(false);
        StringBuilder first = new StringBuilder();
        for (Key k : grid[0]) {
            first.append(k.getChar());
        }
        assertEquals("qwertyuiop", first.toString());
    }

    @Test
    void frequencyLayoutPutsTheCommonestCharactersOnTheCheapestCells() {
        CharFrequency f = new CharFrequency();
        f.addText("eeeeeeeee ttttttt aaaaa ooo");          // space 3, e 9, t 7, a 5, o 3
        Key[][] grid = new FrequencyLayout(f).buildGrid(false);
        assertEquals('e', grid[0][0].getChar());
        assertEquals('t', grid[0][1].getChar());
        assertEquals('a', grid[1][0].getChar());
        assertEquals(7, grid[3].length, "31 characters fill 31 of 32 cells");
    }
}
