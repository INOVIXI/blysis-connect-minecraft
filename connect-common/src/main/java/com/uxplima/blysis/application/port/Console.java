package com.uxplima.blysis.application.port;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

/**
 * The server's console, where a job's command runs.
 *
 * <p>The platform runs it where the platform says commands run: the global region on Folia, the main
 * thread on Paper, the proxy's console on Velocity. The answer arrives on a thread of the platform's choosing.
 */
public interface Console {

    /** Runs one command. The future completes with what the console said, or exceptionally when it threw. */
    CompletableFuture<ConsoleAnswer> run(String command);

    /**
     * Runs one command only if {@code start} answers true when its turn comes. The caller gives up on a
     * command that waits too long by closing that gate, so a command it gave up on never runs afterwards.
     * A platform that runs a command at once asks here; one that queues it asks when the queue reaches it.
     */
    default CompletableFuture<ConsoleAnswer> run(String command, BooleanSupplier start) {
        if (!start.getAsBoolean()) {
            return CompletableFuture.failedFuture(new IllegalStateException("given up before it started"));
        }
        return run(command);
    }

    /**
     * Every name the server knows a command word by: the command an alias points at, and its own aliases.
     * The blocklist judges a word under all of them. A platform that cannot tell answers the word alone.
     */
    default Set<String> namesOf(String word) {
        return Set.of(word);
    }

    /** What a command did: whether the platform reported success, and any text it gave back. */
    record ConsoleAnswer(boolean ok, String output) {}
}
