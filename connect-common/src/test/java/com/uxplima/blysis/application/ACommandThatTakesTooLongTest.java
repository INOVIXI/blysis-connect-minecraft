package com.uxplima.blysis.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

import com.uxplima.blysis.application.port.Console;
import com.uxplima.blysis.domain.CommandBlocklist;
import com.uxplima.blysis.domain.Job;
import com.uxplima.blysis.domain.JobResult;
import com.uxplima.blysis.domain.Outcome;
import com.uxplima.blysis.domain.PlayerRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A command that has not answered in time is reported as what it is: not started, or started and unknown.
 *
 * <p>Found live: with the main thread stalled past the timeout, a job was reported failed, and the command
 * ran a moment later anyway. Staff who sent a failed job again would have run it twice, and the rest of
 * the purchase was skipped while its first step was in place. A command now runs only if its turn comes
 * before the timeout gives up on it, so a failed job never ran and sending it again is safe; one that had
 * started and did not finish is unknown, which the site puts in front of staff to check.
 */
final class ACommandThatTakesTooLongTest {

    private final MemoryJournal journal = new MemoryJournal();
    private final List<String> ran = new ArrayList<>();
    private final List<Runnable> turns = new ArrayList<>();

    /** A console whose turn comes when the test says so, the way a stalled main thread's does. */
    private final Console late = new Console() {
        @Override
        public CompletableFuture<ConsoleAnswer> run(String command) {
            throw new AssertionError("a job is run through its gate");
        }

        @Override
        public CompletableFuture<ConsoleAnswer> run(String command, BooleanSupplier start) {
            CompletableFuture<ConsoleAnswer> answer = new CompletableFuture<>();
            turns.add(() -> {
                if (start.getAsBoolean()) {
                    ran.add(command);
                    answer.complete(new ConsoleAnswer(true, ""));
                }
            });
            return answer;
        }
    };

    /** A console that starts at once and never finishes. */
    private final Console stuck = command -> {
        ran.add(command);
        return new CompletableFuture<>();
    };

    private DeliverJobs deliverWith(Console console) {
        return new DeliverJobs(
                journal,
                console,
                player -> true,
                results -> Set.copyOf(results.stream().map(JobResult::jobId).toList()),
                () -> new CommandBlocklist(List.of()),
                Duration.ofMillis(50));
    }

    private static Job job(String id, int sequence, String command) {
        return new Job(id, "g1", sequence, command, new PlayerRef("uuid", "Steve"), false, "VIP");
    }

    @Test
    @DisplayName("a command whose turn came too late is failed and never runs, so sending it again is safe")
    void aLateTurnRunsNothing() {
        deliverWith(late).deliver(List.of(job("j1", 0, "first"), job("j2", 1, "second")));
        turns.forEach(Runnable::run);

        assertThat(ran).isEmpty();
        assertThat(journal.resultOf("j1")).extracting(JobResult::outcome).isEqualTo(Outcome.FAILED);
        assertThat(journal.resultOf("j1"))
                .extracting(JobResult::detail)
                .asString()
                .contains("did not start");
        assertThat(journal.resultOf("j2")).isEqualTo(new JobResult("j2", Outcome.FAILED, JobResult.SKIPPED));
    }

    @Test
    @DisplayName("a command that started and did not finish is unknown, and the rest of its purchase waits")
    void aStartedCommandIsUnknown() {
        deliverWith(stuck).deliver(List.of(job("j1", 0, "first"), job("j2", 1, "second")));

        assertThat(ran).containsExactly("first");
        assertThat(journal.resultOf("j1")).extracting(JobResult::outcome).isEqualTo(Outcome.UNKNOWN);
        assertThat(journal.resultOf("j2")).isEqualTo(new JobResult("j2", Outcome.FAILED, JobResult.SKIPPED));
    }

    @Test
    @DisplayName("a console that throws without a message says what kind of error it was, not null")
    void anErrorWithoutWordsIsNamed() {
        deliverWith(command -> CompletableFuture.failedFuture(new IllegalStateException()))
                .deliver(List.of(job("j1", 0, "first")));

        assertThat(journal.resultOf("j1")).extracting(JobResult::detail).isEqualTo("IllegalStateException");
    }
}
