package com.switchtype.live;

import com.switchtype.model.Key;
import com.switchtype.model.LayoutType;
import com.switchtype.model.Outcome;
import com.switchtype.model.PredictionMode;
import com.switchtype.model.ScanStyle;
import com.switchtype.model.Settings;
import com.switchtype.predict.NoPredictor;
import com.switchtype.predict.TextComposer;
import com.switchtype.scan.KeyboardLayout;
import com.switchtype.scan.ScanEngine;
import com.switchtype.scan.ScanFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Multithreaded tests of the real-time driver (no Swing: listener calls stay on the scanner thread). */
class ScannerThreadTest {

    private static ScanEngine engine(ScanStyle style, int interval) {
        Settings s = new Settings(style, LayoutType.ALPHABETICAL, PredictionMode.OFF, interval);
        return ScanFactory.engine(s, null);
    }

    /** A listener that only counts. */
    private static class Counting implements ScanListener {
        final AtomicInteger highlights = new AtomicInteger();
        final CountDownLatch pressed = new CountDownLatch(1);
        volatile Outcome last;

        @Override
        public void highlightChanged(List<Key> keys) {
            highlights.incrementAndGet();
        }

        @Override
        public void pressed(Outcome outcome) {
            last = outcome;
            pressed.countDown();
        }

        @Override
        public void gaveUp() {
        }
    }

    @Test
    void stepsOnItsOwnAndStopsCleanly() throws Exception {
        Counting listener = new Counting();
        ScannerThread t = new ScannerThread(engine(ScanStyle.LINEAR, 200), listener, false);
        t.start();
        t.join(1100);                                  // about five steps at 200 ms
        assertTrue(listener.highlights.get() >= 3, "steps seen: " + listener.highlights.get());
        t.shutdown();
        assertFalse(t.isAlive(), "the thread must end after shutdown");
        assertEquals(ScannerThread.PRIORITY, t.getPriority());
    }

    @Test
    void aPressFromAnotherThreadIsHandledWithoutWaitingForTheStep() throws Exception {
        Counting listener = new Counting();
        ScannerThread t = new ScannerThread(engine(ScanStyle.ROW_COLUMN, 2000), listener, false);
        t.start();
        long start = System.nanoTime();
        t.press();                                     // the scanner is in wait(2000): notifyAll must wake it
        assertTrue(listener.pressed.await(1, TimeUnit.SECONDS));
        assertTrue((System.nanoTime() - start) / 1e6 < 1000, "woken early, not at the next step");
        assertEquals(Outcome.Kind.NARROWED, listener.last.getKind());
        t.shutdown();
    }

    @Test
    void realTimeDriverTypesAWordWithThePerfectUser() throws Exception {
        final ScanEngine engine = engine(ScanStyle.ROW_COLUMN, 200);
        final TextComposer composer = new TextComposer(new NoPredictor(), false);
        final String word = "hi";
        final Key[][] grid = engine.getStrategy().getGrid();
        final CountDownLatch done = new CountDownLatch(1);
        final ScannerThread[] holder = new ScannerThread[1];
        ScanListener perfectUser = new ScanListener() {
            @Override
            public void highlightChanged(List<Key> keys) {
                String typed = composer.getText();
                if (typed.length() < word.length()
                        && keys.contains(KeyboardLayout.findChar(grid, word.charAt(typed.length())))) {
                    holder[0].press();
                }
            }

            @Override
            public void pressed(Outcome outcome) {
                if (outcome.isSelection()) {
                    composer.apply(outcome.getKey());
                    if (composer.getText().equals(word)) {
                        done.countDown();
                    }
                }
            }

            @Override
            public void gaveUp() {
            }
        };
        holder[0] = new ScannerThread(engine, perfectUser, false);
        holder[0].start();
        assertTrue(done.await(20, TimeUnit.SECONDS), "typed so far: " + composer.getText());
        holder[0].shutdown();
        assertEquals(word, composer.getText());
        assertTrue(holder[0].getTimingStats().count() > 0);
    }

    @Test
    void workerRunsTasksInOrderAndDrainsOnShutdown() throws Exception {
        DbWorker worker = new DbWorker();
        final StringBuffer order = new StringBuffer();
        for (int i = 0; i < 5; i++) {
            final int n = i;
            worker.submit(() -> order.append(n));
        }
        worker.start();
        worker.shutdown();
        assertEquals("01234", order.toString());
        assertFalse(worker.isAlive());
    }
}
