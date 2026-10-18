package com.uxplima.blysis.velocity.adapter.outbound;

import java.time.Duration;
import java.util.Objects;

import com.uxplima.blysis.application.port.Lanes;
import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.api.scheduler.Scheduler;

/**
 * The connection's lanes on a proxy: Velocity's scheduler, whose tasks run on its own pool and never on a
 * thread that carries a player's packets.
 */
public final class ProxyLanes implements Lanes {

    private final Scheduler scheduler;
    private final Object plugin;

    public ProxyLanes(Scheduler scheduler, Object plugin) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public Cancel now(Runnable work) {
        Objects.requireNonNull(work, "work");
        ScheduledTask task = scheduler.buildTask(plugin, work).schedule();
        return task::cancel;
    }

    @Override
    public Cancel later(Duration delay, Runnable work) {
        Objects.requireNonNull(work, "work");
        ScheduledTask task = scheduler.buildTask(plugin, work).delay(delay).schedule();
        return task::cancel;
    }

    @Override
    public Cancel every(Duration period, Runnable work) {
        Objects.requireNonNull(work, "work");
        ScheduledTask task =
                scheduler.buildTask(plugin, work).delay(period).repeat(period).schedule();
        return task::cancel;
    }
}
