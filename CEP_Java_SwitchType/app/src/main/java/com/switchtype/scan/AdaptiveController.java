package com.switchtype.scan;

import com.switchtype.model.Config;
import com.switchtype.model.Key;
import com.switchtype.model.Settings;

/**
 * Tunes the scan interval while the person types. The system never knows what the person
 * meant to type, so it judges a selection by what happens NEXT:
 * <ul>
 *   <li>next selection is Backspace or Undo word: the previous selection was an error,
 *       slow down by 20 % and reset the streak;</li>
 *   <li>any other next selection: the previous one was correct; after 5 correct in a row,
 *       speed up by 10 %;</li>
 *   <li>a row or group time-out (all cycles passed without a press) is also an error signal.</li>
 * </ul>
 * Corrections themselves are never judged. Pure logic, so it runs in the live app and in
 * the simulator unchanged.
 */
public class AdaptiveController {

    private double intervalMs;
    private int streak;
    private boolean pending;

    public AdaptiveController(int startIntervalMs) {
        this.intervalMs = startIntervalMs;
    }

    /**
     * Reports a selected key.
     *
     * @return the interval to use from now on
     */
    public int onSelection(Key key) {
        if (key.getAction().isCorrection()) {
            if (pending) {
                slowDown();
            }
            pending = false;
        } else {
            if (pending) {
                streak++;
                if (streak >= Config.ADAPT_STREAK) {
                    intervalMs = Settings.clampInterval(intervalMs * Config.ADAPT_FASTER);
                    streak = 0;
                }
            }
            pending = true;
        }
        return getIntervalMs();
    }

    /** Reports that the scanner gave up on a row or group. */
    public int onGiveUp() {
        slowDown();
        return getIntervalMs();
    }

    private void slowDown() {
        intervalMs = Settings.clampInterval(intervalMs * Config.ADAPT_SLOWER);
        streak = 0;
    }

    public int getIntervalMs() {
        return (int) Math.round(intervalMs);
    }
}
