package com.switchtype.sim;

/**
 * The behaviour of a (simulated) person using the switch. The simulator asks this
 * interface every question that depends on the person (Unit I: interface).
 */
public interface UserModel {

    /**
     * Called when the highlight arrives on the item that holds the wanted key.
     *
     * @return delay in ms from the arrival to the press, or -1 if the person misses this time
     */
    int pressDelayMs();

    /** True if the person presses by accident during this highlight step. */
    boolean slipsThisStep();

    /** A uniform point in [0, dwellMs) at which an accidental press happens. */
    int slipOffsetMs(int dwellMs);

    /** True if the person notices a wrong selection (and will correct it). */
    boolean noticesError();

    /** Thinking time at the start of each word and after a noticed error, in ms. */
    int thinkTimeMs();

    /** Records one switch press (for fatigue). */
    void countPress();

    /** A rest break: fatigue starts again from zero. */
    void rest();
}
