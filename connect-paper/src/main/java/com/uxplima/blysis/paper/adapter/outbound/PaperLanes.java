package com.uxplima.blysis.paper.adapter.outbound;

import java.time.Duration;
import java.util.Objects;

import com.uxplima.blysis.application.port.Lanes;
import com.uxplima.uxmlib.scheduler.Scheduler;

/** The connection's lanes on Paper and Folia: the async scheduler, which both have. */
public final class PaperLanes implements Lanes {

    private final Scheduler scheduler;

    public PaperLanes(Scheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public Cancel now(Runnable work) {
        return scheduler.async(work)::cancel;
    }

    @Override
    public Cancel later(Duration delay, Runnable work) {
        return scheduler.asyncLater(delay, work)::cancel;
    }

    @Override
    public Cancel every(Duration period, Runnable work) {
        return scheduler.asyncTimer(period, period, handle -> work.run())::cancel;
    }
}
