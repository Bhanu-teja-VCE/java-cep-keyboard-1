package com.switchtype.live;

import java.util.LinkedList;

/**
 * Runs database and file work off the Swing thread, one task at a time
 * (Unit II: producer-consumer with wait/notifyAll; Unit III: LinkedList as a queue).
 * The Swing thread is the producer, this thread the consumer. Tasks run outside the lock.
 */
public class DbWorker extends Thread {

    private final LinkedList<Runnable> queue = new LinkedList<Runnable>();   // guarded by this
    private boolean running = true;                                            // guarded by this

    public DbWorker() {
        super("db-worker");
        setPriority(Thread.NORM_PRIORITY - 1);    // background work must not delay scanning
        setDaemon(true);
    }

    public synchronized void submit(Runnable task) {
        queue.addLast(task);
        notifyAll();
    }

    @Override
    public void run() {
        while (true) {
            Runnable task;
            synchronized (this) {
                while (queue.isEmpty() && running) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        // re-check the conditions
                    }
                }
                if (queue.isEmpty()) {
                    return;                         // stopped and nothing left to do
                }
                task = queue.removeFirst();
            }
            try {
                task.run();
            } catch (RuntimeException e) {
                System.err.println("Background task failed: " + e.getMessage());
            }
        }
    }

    /** Finishes the queued tasks, then stops. */
    public void shutdown() throws InterruptedException {
        synchronized (this) {
            running = false;
            notifyAll();
        }
        join(5000);
    }
}
