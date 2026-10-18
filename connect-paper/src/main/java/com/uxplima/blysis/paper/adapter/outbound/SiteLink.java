package com.uxplima.blysis.paper.adapter.outbound;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import com.uxplima.blysis.application.StayConnected;
import com.uxplima.blysis.domain.StatusPush;
import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.scheduler.TaskHandle;
import org.jspecify.annotations.Nullable;

/**
 * Turns the connection's steps into scheduled tasks: each step on the async lane, the next one when the step
 * says, and a status push on a timer and shortly after a join or a quit.
 *
 * <p>No thread of this plugin's own exists. A long-poll holds one async worker while the site holds it, which
 * is what that lane is for.
 */
public final class SiteLink {

    /** The shortest time between two pushes caused by joins and quits. */
    private static final Duration PUSH_SETTLE = Duration.ofSeconds(1);

    private final StayConnected connection;
    private final Scheduler scheduler;
    private final Supplier<StatusPush> status;
    private final Supplier<Duration> statusInterval;
    private final AtomicBoolean pushPending = new AtomicBoolean();
    private volatile boolean running;
    private volatile @Nullable TaskHandle next;
    private volatile @Nullable TaskHandle statusTimer;

    public SiteLink(
            StayConnected connection,
            Scheduler scheduler,
            Supplier<StatusPush> status,
            Supplier<Duration> statusInterval) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.status = Objects.requireNonNull(status, "status");
        this.statusInterval = Objects.requireNonNull(statusInterval, "statusInterval");
    }

    /** Starts calling the site. */
    public void start() {
        running = true;
        next = scheduler.async(this::step);
    }

    /** Stops calling. A poll the site is holding ends when its connection is closed by the caller. */
    public void stop() {
        running = false;
        cancel(next);
        cancel(statusTimer);
    }

    /** Pushes who is online soon: once, a second after the first change of a burst. */
    public void statusSoon() {
        if (running && pushPending.compareAndSet(false, true)) {
            scheduler.asyncLater(PUSH_SETTLE, () -> {
                pushPending.set(false);
                connection.pushStatus(status.get());
            });
        }
    }

    private void step() {
        if (!running) {
            return;
        }
        boolean greeted = connection.hello().isPresent();
        Optional<Duration> wait = connection.step();
        if (!greeted && connection.hello().isPresent()) {
            startStatusTimer();
            connection.pushStatus(status.get());
        }
        if (wait.isEmpty() || !running) {
            return;
        }
        Duration delay = wait.get();
        next = delay.isZero() ? scheduler.async(this::step) : scheduler.asyncLater(delay, this::step);
    }

    private void startStatusTimer() {
        Duration period = statusInterval.get();
        cancel(statusTimer);
        statusTimer = scheduler.asyncTimer(period, period, handle -> connection.pushStatus(status.get()));
    }

    private static void cancel(@Nullable TaskHandle handle) {
        if (handle != null) {
            handle.cancel();
        }
    }
}
