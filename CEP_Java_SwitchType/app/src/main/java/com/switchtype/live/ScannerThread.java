package com.switchtype.live;

import com.switchtype.model.Key;
import com.switchtype.model.Outcome;
import com.switchtype.scan.ScanEngine;
import com.switchtype.scan.ScanStrategy;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;

/**
 * The real-time driver of the ScanEngine (the experiments use SimulationDriver instead).
 * <p>
 * Unit II: the thread waits with wait(timeout) until the next step is due and is woken
 * early with notifyAll() when the switch is pressed or settings change. Every wait is in a
 * loop that re-checks all conditions. The monitor of this object guards the engine and the
 * flags; it is the only lock this thread takes (lock order: see DESIGN.md section 5).
 * <p>
 * Steps are scheduled on an ideal timeline (next = previous ideal time + delay), so small
 * delays do not add up to drift. Lateness of each step is recorded in TimingStats.
 */
public class ScannerThread extends Thread {

    /** Above normal so a busy UI does not delay the highlight; below MAX so input is never starved. */
    public static final int PRIORITY = Thread.NORM_PRIORITY + 3;

    private final ScanEngine engine;            // guarded by this
    private final ScanListener listener;
    private final boolean onSwingThread;
    private final TimingStats timing = new TimingStats();

    private boolean running = true;             // guarded by this
    private boolean pressPending;               // guarded by this
    private boolean timingChanged;              // guarded by this
    private long nextStepAt;                    // System.nanoTime() of the next step

    /**
     * @param onSwingThread true in the app: listener calls go through invokeLater.
     *                      false for headless timing measurements.
     */
    public ScannerThread(ScanEngine engine, ScanListener listener, boolean onSwingThread) {
        super("scanner");
        this.engine = engine;
        this.listener = listener;
        this.onSwingThread = onSwingThread;
        setPriority(PRIORITY);
        setDaemon(true);
    }

    @Override
    public void run() {
        synchronized (this) {
            engine.start();
            nextStepAt = System.nanoTime() + millis(engine.nextDelayMs());
            publishHighlight();
            while (running) {
                long now = System.nanoTime();
                if (pressPending) {
                    pressPending = false;
                    Outcome outcome = engine.onPress();
                    nextStepAt = now + millis(engine.nextDelayMs());   // a press restarts the dwell
                    publishOutcome(outcome);
                    publishHighlight();
                } else if (timingChanged) {
                    timingChanged = false;
                    nextStepAt = now + millis(engine.nextDelayMs());
                    publishHighlight();
                } else if (now < nextStepAt) {
                    long waitNanos = nextStepAt - now;
                    try {
                        wait(waitNanos / 1000000L, (int) (waitNanos % 1000000L));   // Unit II: wait
                    } catch (InterruptedException e) {
                        // shutdown() interrupts; the loop condition decides whether to stop
                    }
                } else {
                    timing.record(now - nextStepAt);
                    boolean gaveUp = engine.onStep();
                    nextStepAt += millis(engine.nextDelayMs());
                    if (nextStepAt <= now) {
                        nextStepAt = now + millis(engine.nextDelayMs());  // far behind: resynchronise
                    }
                    publishHighlight();
                    if (gaveUp) {
                        publishGaveUp();
                    }
                }
            }
        }
    }

    private static long millis(int ms) {
        return ms * 1000000L;
    }

    /** Called by the switch (on the Swing thread). Holding the switch down must not repeat; the UI filters that. */
    public synchronized void press() {
        pressPending = true;
        notifyAll();                                // Unit II: wake the waiting scanner
    }

    public synchronized void setIntervalMs(int ms) {
        engine.setIntervalMs(ms);
        timingChanged = true;
        notifyAll();
    }

    public synchronized void setFirstExtraPct(int pct) {
        engine.setFirstExtraPct(pct);
        timingChanged = true;
        notifyAll();
    }

    /** Switches to a new grid or scan style; scanning restarts at the top. */
    public synchronized void setStrategy(ScanStrategy strategy) {
        engine.setStrategy(strategy);
        timingChanged = true;
        notifyAll();
    }

    public synchronized int getIntervalMs() {
        return engine.getIntervalMs();
    }

    /** Stops the thread and waits for it to finish (call from any thread except this one). */
    public void shutdown() throws InterruptedException {
        synchronized (this) {
            running = false;
            notifyAll();
        }
        interrupt();
        join(2000);
    }

    /** Lateness of every step so far. Read it after shutdown() for a consistent view. */
    public TimingStats getTimingStats() {
        return timing;
    }

    private void publishHighlight() {
        final List<Key> snapshot = new ArrayList<Key>(engine.highlighted());
        deliver(new Runnable() {
            @Override
            public void run() {
                listener.highlightChanged(snapshot);
            }
        });
    }

    private void publishOutcome(final Outcome outcome) {
        deliver(new Runnable() {
            @Override
            public void run() {
                listener.pressed(outcome);
            }
        });
    }

    private void publishGaveUp() {
        deliver(new Runnable() {
            @Override
            public void run() {
                listener.gaveUp();
            }
        });
    }

    /** All Swing changes happen on the event dispatch thread. */
    private void deliver(Runnable r) {
        if (onSwingThread) {
            SwingUtilities.invokeLater(r);
        } else {
            r.run();
        }
    }
}
