package com.switchtype.sim;

import com.switchtype.model.Config;

import java.util.Random;

/**
 * The simulated person (DESIGN.md section 7). All randomness comes from one seeded
 * Random, so a run is exactly reproducible from its seed.
 * <pre>
 *   f      = 1 + fatiguePct/100 * pressesSinceRest/100
 *   r      ~ Normal(reactionMean * f, reactionSd), redrawn until r >= 150 ms
 *   delay  = max(0, r * (1 - compensation) + j),  j ~ Normal(0, jitter)
 *   miss   with probability miss * f   (per arrival of the target)
 *   slip   with probability spurious * f (per highlight step)
 * </pre>
 */
public class SimulatedUser implements UserModel {

    private final UserParams p;
    private final Random random;
    private int pressesSinceRest;

    public SimulatedUser(UserParams params, long seed) {
        this.p = params;
        this.random = new Random(seed);
    }

    private double fatigueFactor() {
        return 1.0 + p.get("fatiguePct") / 100.0 * pressesSinceRest / 100.0;
    }

    @Override
    public int pressDelayMs() {
        double f = fatigueFactor();
        if (random.nextDouble() < Math.min(0.9, p.get("miss") * f)) {
            return -1;
        }
        double reaction;
        do {
            reaction = p.get("reactionMean") * f + random.nextGaussian() * p.get("reactionSd");
        } while (reaction < Config.MIN_REACTION_MS);
        double jitter = random.nextGaussian() * p.get("jitter");
        double delay = reaction * (1.0 - p.get("compensation")) + jitter;
        return (int) Math.max(0, Math.round(delay));
    }

    @Override
    public boolean slipsThisStep() {
        return random.nextDouble() < Math.min(0.5, p.get("spurious") * fatigueFactor());
    }

    @Override
    public int slipOffsetMs(int dwellMs) {
        return random.nextInt(Math.max(1, dwellMs));
    }

    @Override
    public boolean noticesError() {
        return random.nextDouble() < p.get("detect");
    }

    @Override
    public int thinkTimeMs() {
        return (int) p.get("think");
    }

    @Override
    public void countPress() {
        pressesSinceRest++;
    }

    @Override
    public void rest() {
        pressesSinceRest = 0;
    }
}
