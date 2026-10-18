package com.uxplima.blysis.domain;

import java.time.Duration;
import java.util.Objects;
import java.util.function.DoubleSupplier;

/**
 * How long to wait before calling a site that did not answer: doubling from {@code first} up to
 * {@code longest}, each wait moved by up to a fifth either way, so a hundred servers that lost the same site
 * do not all call it again in the same second.
 */
public final class Backoff {

    private static final double SPREAD = 0.2;

    private final Duration first;
    private final Duration longest;
    private final DoubleSupplier random;
    private int failures;

    public Backoff(Duration first, Duration longest, DoubleSupplier random) {
        this.first = Objects.requireNonNull(first, "first");
        this.longest = Objects.requireNonNull(longest, "longest");
        this.random = Objects.requireNonNull(random, "random");
        if (first.isNegative() || first.isZero() || longest.compareTo(first) < 0) {
            throw new IllegalArgumentException("the first wait must be positive and no longer than the longest");
        }
    }

    /** The wait after one more failure. */
    public Duration next() {
        int doublings = Math.min(failures, 30);
        failures++;
        long base = Math.min(longest.toMillis(), first.toMillis() << Math.min(doublings, 20));
        double moved = base * (1 + SPREAD * (random.getAsDouble() * 2 - 1));
        return Duration.ofMillis(Math.max(first.toMillis() / 2, Math.min((long) moved, longest.toMillis())));
    }

    /** Forgets the failures: the site answered. */
    public void reset() {
        failures = 0;
    }
}
