package com.uxplima.blysis.application;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import com.uxplima.blysis.application.port.Console;
import com.uxplima.blysis.application.port.Journal;
import com.uxplima.blysis.application.port.Presence;
import com.uxplima.blysis.application.port.ResultSink;
import com.uxplima.blysis.application.port.SiteUnavailable;
import com.uxplima.blysis.domain.CommandBlocklist;
import com.uxplima.blysis.domain.Job;
import com.uxplima.blysis.domain.JobResult;
import com.uxplima.blysis.domain.Outcome;
import org.jspecify.annotations.Nullable;

/**
 * Runs the jobs a poll claimed, each at most once, and tells the site what happened.
 *
 * <p>The journal is written before a command runs and after it, so a crash at any point leaves either a
 * job this server never started, which the site offers again, or one it knows about and never runs again.
 * An outcome the site has not acknowledged stays in the journal and goes with the next report.
 *
 * <p>It runs on an async thread and waits there for the console, never on a server thread.
 */
public final class DeliverJobs {

    private static final int WAITING = 0;
    private static final int STARTED = 1;
    private static final int GIVEN_UP = 2;

    private final Journal journal;
    private final Console console;
    private final Presence presence;
    private final ResultSink sink;
    private final Supplier<CommandBlocklist> blocklist;
    private final Duration commandTimeout;

    public DeliverJobs(
            Journal journal,
            Console console,
            Presence presence,
            ResultSink sink,
            Supplier<CommandBlocklist> blocklist,
            Duration commandTimeout) {
        this.journal = Objects.requireNonNull(journal, "journal");
        this.console = Objects.requireNonNull(console, "console");
        this.presence = Objects.requireNonNull(presence, "presence");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.blocklist = Objects.requireNonNull(blocklist, "blocklist");
        this.commandTimeout = Objects.requireNonNull(commandTimeout, "commandTimeout");
    }

    /** Runs what is new among these jobs, then reports every outcome the site does not have yet. */
    public void deliver(List<Job> jobs) {
        Objects.requireNonNull(jobs, "jobs");
        for (List<Job> group : groups(jobs)) {
            runGroup(group);
        }
        flush();
    }

    /**
     * On start: gives every job that was running when the process stopped the outcome {@code unknown}, so
     * staff decide rather than this server guessing, and reports what the site has not acknowledged.
     */
    public void recover() {
        for (String jobId : journal.unfinished()) {
            journal.finished(new JobResult(jobId, Outcome.UNKNOWN, "the server stopped while it ran"));
        }
        flush();
    }

    /** Sends the outcomes the site has not acknowledged. Quietly keeps them when the site is away. */
    public void flush() {
        List<JobResult> pending = journal.unreported();
        if (pending.isEmpty()) {
            return;
        }
        try {
            Set<String> accepted = sink.report(pending);
            journal.acknowledged(accepted);
        } catch (SiteUnavailable away) {
            // Kept in the journal; the next round sends them again.
        }
    }

    private void runGroup(List<Job> group) {
        boolean failed = false;
        for (Job job : group) {
            if (journal.knows(job.id())) {
                continue;
            }
            journal.started(job.id());
            JobResult result = failed ? new JobResult(job.id(), Outcome.FAILED, JobResult.SKIPPED) : run(job);
            journal.finished(result);
            failed |= result.outcome() == Outcome.FAILED || result.outcome() == Outcome.UNKNOWN;
        }
    }

    private JobResult run(Job job) {
        if (job.whenOnline() && job.player() != null && !presence.isOnline(job.player())) {
            return new JobResult(job.id(), Outcome.DEFERRED, "the player left");
        }
        if (blocklist.get().blocks(job.command(), console::namesOf)) {
            return new JobResult(job.id(), Outcome.FAILED, JobResult.BLOCKED);
        }
        AtomicInteger turn = new AtomicInteger(WAITING);
        try {
            Console.ConsoleAnswer answer = console.run(job.command(), () -> turn.compareAndSet(WAITING, STARTED))
                    .orTimeout(commandTimeout.toMillis(), TimeUnit.MILLISECONDS)
                    .join();
            return answer.ok()
                    ? new JobResult(job.id(), Outcome.DONE, blankToNull(answer.output()))
                    : new JobResult(job.id(), Outcome.FAILED, failureText(answer.output()));
        } catch (CompletionException thrown) {
            Throwable cause = thrown.getCause() == null ? thrown : thrown.getCause();
            if (cause instanceof TimeoutException) {
                return tooLong(job, turn);
            }
            String said = cause.getMessage();
            return new JobResult(
                    job.id(), Outcome.FAILED, said == null ? cause.getClass().getSimpleName() : said);
        }
    }

    /**
     * A command that has not answered in time. Closing its gate first means one still waiting for its turn
     * never runs, so failed is true and sending it again is safe; one that already started may still be
     * running, and only staff can tell whether it did.
     */
    private JobResult tooLong(Job job, AtomicInteger turn) {
        long seconds = commandTimeout.toSeconds();
        if (turn.compareAndSet(WAITING, GIVEN_UP)) {
            return new JobResult(job.id(), Outcome.FAILED, "did not start within " + seconds + " s, and will not");
        }
        return new JobResult(
                job.id(), Outcome.UNKNOWN, "started and did not finish within " + seconds + " s: check whether it ran");
    }

    /** The jobs of each purchase together, in the order the site gave them. */
    private static List<List<Job>> groups(List<Job> jobs) {
        Map<String, List<Job>> byKey = new LinkedHashMap<>();
        for (Job job : jobs) {
            byKey.computeIfAbsent(job.groupKey(), key -> new ArrayList<>()).add(job);
        }
        List<List<Job>> groups = new ArrayList<>();
        for (List<Job> group : byKey.values()) {
            group.sort((a, b) -> Integer.compare(a.sequence(), b.sequence()));
            groups.add(group);
        }
        return groups;
    }

    private static String failureText(String output) {
        return output.isBlank() ? "the console refused the command" : output;
    }

    private static @Nullable String blankToNull(String output) {
        return output.isBlank() ? null : output;
    }
}
