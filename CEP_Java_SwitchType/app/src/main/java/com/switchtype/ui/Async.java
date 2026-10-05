package com.switchtype.ui;

import com.switchtype.live.DbWorker;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.Component;

/**
 * Runs a job on the background worker and hands the result back to the Swing thread.
 * This keeps all database and file I/O off the event dispatch thread.
 */
final class Async {

    /** Work that may fail; runs on the worker thread. */
    interface Job<T> {
        T run() throws Exception;
    }

    /** What to do with the result; runs on the Swing thread. */
    interface Done<T> {
        void done(T value);
    }

    private Async() {
    }

    static <T> void run(DbWorker worker, final Component parent, final Job<T> job, final Done<T> done) {
        worker.submit(() -> {
            try {
                final T value = job.run();
                SwingUtilities.invokeLater(() -> done.done(value));
            } catch (final Exception e) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(parent, e.getMessage(),
                        "SwitchType", JOptionPane.WARNING_MESSAGE));
            }
        });
    }
}
