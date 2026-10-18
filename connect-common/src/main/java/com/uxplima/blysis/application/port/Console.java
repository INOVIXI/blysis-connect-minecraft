package com.uxplima.blysis.application.port;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

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
     * Every name the server knows a command word by: the command an alias points at, and its own aliases.
     * The blocklist judges a word under all of them. A platform that cannot tell answers the word alone.
     */
    default Set<String> namesOf(String word) {
        return Set.of(word);
    }

    /** What a command did: whether the platform reported success, and any text it gave back. */
    record ConsoleAnswer(boolean ok, String output) {}
}
