package com.switchtype.ui;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineUnavailableException;

/**
 * A short tone for every scan step, generated in memory (no sound files).
 * If the computer has no audio device the cue silently stays off.
 */
public class AudioCue {

    private static final float RATE = 22050f;
    private Clip clip;

    /** Builds a 40 ms, 880 Hz tone with a soft fade so it does not click. */
    public AudioCue() {
        int samples = (int) (RATE * 0.04);
        byte[] data = new byte[samples];
        for (int i = 0; i < samples; i++) {
            double fade = Math.min(1.0, Math.min(i, samples - i) / (samples * 0.2));
            data[i] = (byte) (Math.sin(2 * Math.PI * 880 * i / RATE) * 60 * fade);
        }
        try {
            clip = AudioSystem.getClip();
            clip.open(new AudioFormat(RATE, 8, 1, true, false), data, 0, data.length);
        } catch (LineUnavailableException | IllegalArgumentException | SecurityException e) {
            clip = null;
        }
    }

    public boolean isAvailable() {
        return clip != null;
    }

    /** Plays the tone from the start (does not block). */
    public void play() {
        if (clip != null) {
            clip.stop();
            clip.setFramePosition(0);
            clip.start();
        }
    }
}
