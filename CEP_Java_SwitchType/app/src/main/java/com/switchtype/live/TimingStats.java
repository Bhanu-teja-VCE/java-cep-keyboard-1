package com.switchtype.live;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * How late each scan step happened compared with its ideal time. Written only by the
 * scanner thread; read after the thread has been joined (join gives the visibility).
 */
public class TimingStats {

    private final List<Long> latenessNanos = new ArrayList<Long>();

    void record(long nanos) {
        latenessNanos.add(nanos);
    }

    public int count() {
        return latenessNanos.size();
    }

    public double meanMs() {
        if (latenessNanos.isEmpty()) {
            return 0;
        }
        double sum = 0;
        for (long n : latenessNanos) {
            sum += n;
        }
        return sum / latenessNanos.size() / 1e6;
    }

    /** The given percentile (0..100) of the lateness, in ms. */
    public double percentileMs(double percentile) {
        if (latenessNanos.isEmpty()) {
            return 0;
        }
        List<Long> sorted = new ArrayList<Long>(latenessNanos);
        Collections.sort(sorted);
        int index = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(sorted.size() - 1, index))) / 1e6;
    }

    public double maxMs() {
        return latenessNanos.isEmpty() ? 0 : Collections.max(latenessNanos) / 1e6;
    }
}
