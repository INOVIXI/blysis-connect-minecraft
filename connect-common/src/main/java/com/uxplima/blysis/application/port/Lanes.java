package com.uxplima.blysis.application.port;

import java.time.Duration;

/**
 * Where the connection's work runs off the server threads: now, later, and on a timer. Paper answers with its
 * async scheduler and Velocity with the proxy's, so the loop that keeps the site called is written once.
 *
 * <p>A long-poll holds one worker while the site holds it, which is what an async lane is for.
 */
public interface Lanes {

    Cancel now(Runnable work);

    Cancel later(Duration delay, Runnable work);

    Cancel every(Duration period, Runnable work);

    /** Stops a task that has not run, or a timer. */
    @FunctionalInterface
    interface Cancel {
        void cancel();
    }
}
