package com.uxplima.blysis.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.uxplima.blysis.application.port.Console;
import com.uxplima.blysis.application.port.ResultSink;
import com.uxplima.blysis.application.port.SiteUnavailable;
import com.uxplima.blysis.domain.CommandBlocklist;
import com.uxplima.blysis.domain.Job;
import com.uxplima.blysis.domain.JobResult;
import com.uxplima.blysis.domain.Outcome;
import com.uxplima.blysis.domain.PlayerRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class DeliverJobsTest {

    private final MemoryJournal journal = new MemoryJournal();
    private final List<String> ran = new ArrayList<>();
    private final Set<String> online = new HashSet<>();
    private final List<List<JobResult>> reports = new ArrayList<>();
    private boolean siteAway;
    private String refused = "";

    private final Console console = command -> {
        ran.add(command);
        if (command.equals(refused)) {
            return CompletableFuture.completedFuture(new Console.ConsoleAnswer(false, "Unknown command"));
        }
        return CompletableFuture.completedFuture(new Console.ConsoleAnswer(true, ""));
    };

    private final ResultSink sink = results -> {
        if (siteAway) {
            throw new SiteUnavailable("down");
        }
        reports.add(List.copyOf(results));
        Set<String> accepted = new HashSet<>();
        results.forEach(result -> accepted.add(result.jobId()));
        return accepted;
    };

    private DeliverJobs deliver;

    @BeforeEach
    void setUp() {
        online.add("Steve");
        deliver = new DeliverJobs(
                journal,
                console,
                player -> online.contains(player.name()),
                sink,
                () -> new CommandBlocklist(List.of("op", "stop")),
                Duration.ofSeconds(5));
    }

    private static Job job(String id, String group, int sequence, String command) {
        return new Job(id, group, sequence, command, new PlayerRef("uuid", "Steve"), true, "VIP");
    }

    @Test
    @DisplayName("a job runs once, however often the site offers it")
    void runsOnce() {
        Job vip = job("j1", "g1", 0, "lp user Steve parent add vip");

        deliver.deliver(List.of(vip));
        deliver.deliver(List.of(vip));

        assertThat(ran).containsExactly("lp user Steve parent add vip");
    }

    @Test
    @DisplayName("a purchase's commands run in their order, whatever order they arrive in")
    void runsInSequence() {
        deliver.deliver(List.of(job("j2", "g1", 1, "second"), job("j1", "g1", 0, "first")));

        assertThat(ran).containsExactly("first", "second");
    }

    @Test
    @DisplayName("after a refused command, the rest of its purchase is skipped and not run")
    void skipsTheRestOfAFailedGroup() {
        refused = "first";

        deliver.deliver(
                List.of(job("j1", "g1", 0, "first"), job("j2", "g1", 1, "second"), job("j3", "g2", 0, "other")));

        assertThat(ran).containsExactly("first", "other");
        assertThat(journal.resultOf("j2")).isEqualTo(new JobResult("j2", Outcome.FAILED, JobResult.SKIPPED));
        assertThat(journal.resultOf("j3")).extracting(JobResult::outcome).isEqualTo(Outcome.DONE);
    }

    @Test
    @DisplayName("a blocked command is never run, with or without its slash and namespace")
    void refusesABlockedCommand() {
        deliver.deliver(List.of(job("j1", "g1", 0, "/minecraft:op Steve")));

        assertThat(ran).isEmpty();
        assertThat(journal.resultOf("j1")).isEqualTo(new JobResult("j1", Outcome.FAILED, JobResult.BLOCKED));
    }

    @Test
    @DisplayName("a job that waits for its player is deferred when they left, and not run")
    void defersWhenThePlayerLeft() {
        online.clear();

        deliver.deliver(List.of(job("j1", "g1", 0, "give Steve diamond")));

        assertThat(ran).isEmpty();
        assertThat(journal.resultOf("j1")).extracting(JobResult::outcome).isEqualTo(Outcome.DEFERRED);
    }

    @Test
    @DisplayName("an outcome the site did not take is sent again with the next report")
    void keepsWhatTheSiteDidNotTake() {
        siteAway = true;
        deliver.deliver(List.of(job("j1", "g1", 0, "first")));
        assertThat(reports).isEmpty();

        siteAway = false;
        deliver.flush();

        assertThat(reports)
                .singleElement()
                .satisfies(sent -> assertThat(sent).extracting(JobResult::jobId).containsExactly("j1"));
        deliver.flush();
        assertThat(reports).hasSize(1);
    }

    @Test
    @DisplayName("a job that was running when the server stopped is reported unknown, not run again")
    void recoversAnUnfinishedJobAsUnknown() {
        journal.started("j1");

        deliver.recover();
        deliver.deliver(List.of(job("j1", "g1", 0, "first")));

        assertThat(ran).isEmpty();
        assertThat(reports.getFirst()).extracting(JobResult::outcome).containsExactly(Outcome.UNKNOWN);
    }

    @Test
    @DisplayName("a console that throws fails the job with what it said")
    void aThrowingConsoleFailsTheJob() {
        DeliverJobs throwing = new DeliverJobs(
                journal,
                command -> CompletableFuture.failedFuture(new IllegalStateException("no such server")),
                player -> true,
                sink,
                () -> new CommandBlocklist(List.of()),
                Duration.ofSeconds(5));

        throwing.deliver(List.of(job("j1", "g1", 0, "first")));

        assertThat(journal.resultOf("j1")).isEqualTo(new JobResult("j1", Outcome.FAILED, "no such server"));
    }
}
