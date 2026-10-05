package com.switchtype.scan;

import com.switchtype.model.Config;
import com.switchtype.model.Key;
import com.switchtype.model.KeyAction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdaptiveControllerTest {

    private static final Key A = Key.character('a');
    private static final Key BACK = Key.control(KeyAction.BACKSPACE, "delete");

    @Test
    void speedsUpAfterFiveConfirmedCorrectSelections() {
        AdaptiveController c = new AdaptiveController(1000);
        for (int i = 0; i < 5; i++) {
            assertEquals(1000, c.onSelection(A), "a selection is only judged when the next one comes");
        }
        assertEquals(900, c.onSelection(A), "the 6th selection confirms the 5th: 10 % faster");
    }

    @Test
    void slowsDownWhenTheUserCorrectsImmediately() {
        AdaptiveController c = new AdaptiveController(1000);
        c.onSelection(A);
        assertEquals(1200, c.onSelection(BACK), "backspace right after a selection: 20 % slower");
        assertEquals(1200, c.onSelection(BACK), "a second backspace is not judged again");
    }

    @Test
    void giveUpCountsAsAnErrorSignal() {
        AdaptiveController c = new AdaptiveController(500);
        assertEquals(600, c.onGiveUp());
    }

    @Test
    void staysWithinTheAllowedRange() {
        AdaptiveController fast = new AdaptiveController(Config.MIN_INTERVAL_MS);
        for (int i = 0; i < 50; i++) {
            fast.onSelection(A);
        }
        assertEquals(Config.MIN_INTERVAL_MS, fast.getIntervalMs());
        AdaptiveController slow = new AdaptiveController(Config.MAX_INTERVAL_MS);
        slow.onGiveUp();
        assertEquals(Config.MAX_INTERVAL_MS, slow.getIntervalMs());
    }
}
