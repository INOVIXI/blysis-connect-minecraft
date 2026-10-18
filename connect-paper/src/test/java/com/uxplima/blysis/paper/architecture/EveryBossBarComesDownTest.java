package com.uxplima.blysis.paper.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every action context this plugin builds wires the delay that {@code [bossbar]} needs.
 *
 * <p>A boss bar is one of the channels CONTRACT section 17 promises an operator, and it is the only verb
 * in the engine that has to happen twice: the bar goes up, and something has to take it down again. The
 * library does not know a scheduler on purpose, so the second half is a seam the caller wires, and left
 * unwired it throws.
 *
 * <p>What that costs is the rest of the list. {@code Actions.bossBar} asks for the delay before it shows
 * the bar, so an unwired plugin throws out of the middle of the list and loses every line the operator
 * wrote after it, while the player's screen is left alone.
 *
 * <p>It used to show the bar first, and that was worse: the bar stayed until the player logged out,
 * because the only thing that would have removed it was the call that threw. The order was swapped in
 * uxmLib 0.104.0 on 2026-09-22, which lowers the price of being unwired and does not pay it off.
 *
 * <p>The scan is by file, and a file that builds a context has to name the seam. That is coarse on
 * purpose: this guard is not trying to follow a builder through a method chain, it is asking whether the
 * plugin knows the seam exists at all. Every plugin in the estate was answering no.
 */
final class EveryBossBarComesDownTest {

    private static final Path SOURCE_ROOT = Path.of("src", "main", "java");

    /** Where a context is built. */
    private static final String BUILDS_A_CONTEXT = "ActionContext.builder(";

    /** The seam that takes the bar down again. */
    private static final String WIRES_THE_DELAY = ".later(";

    @Test
    @DisplayName("every action context this plugin builds can take a boss bar down again")
    void everycontextWiresTheDelay() throws IOException {
        List<String> unwired = new ArrayList<>();
        for (Path file : java()) {
            String body = withoutSpaces(Files.readString(file, StandardCharsets.UTF_8));
            if (body.contains(BUILDS_A_CONTEXT) && !body.contains(WIRES_THE_DELAY)) {
                unwired.add(file.getFileName().toString());
            }
        }

        assertThat(unwired)
                .describedAs("A context with no delay wired shows the boss bar, throws, and leaves it on the"
                        + " player forever. Wire ActionContext.Builder.later to this plugin's Scheduler.")
                .isEmpty();
    }

    @Test
    @DisplayName("the scan finds the contexts, so it cannot pass by reading nothing")
    void thescanFindsTheContexts() throws IOException {
        List<String> builders = new ArrayList<>();
        for (Path file : java()) {
            if (withoutSpaces(Files.readString(file, StandardCharsets.UTF_8)).contains(BUILDS_A_CONTEXT)) {
                builders.add(file.getFileName().toString());
            }
        }

        assertThat(builders)
                .describedAs("this plugin runs the action engine, so a scan that finds no context is broken")
                .isNotEmpty();
    }

    /**
     * The source with every space taken out, which is what the search reads.
     *
     * <p>A guard that looks for a text fragment can be defeated by the formatter. {@code spotlessApply}
     * wraps a long chain at the dot, so {@code ActionContext.builder(} becomes {@code ActionContext}
     * then {@code .builder(} on the next line and the text this scan wants is never in the file. **The
     * break that triggers the wrap is the same break that makes the line longer**, and a line gets
     * longer exactly when somebody adds an argument or nests the call deeper, so the guard would be
     * blind in a case close to the one it exists for, and blind silently.
     *
     * <p>Found by the uxmPlots session on 2026-09-22 against a different guard, and reproduced against
     * this one: with the chain wrapped and the wiring removed, this test passed. Only the non-vacuity
     * test below noticed, and only because that plugin builds exactly one context; a plugin that builds
     * two would have hidden one in silence.
     */
    private static String withoutSpaces(String body) {
        return body.replaceAll("\\s+", "");
    }

    private static List<Path> java() throws IOException {
        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList();
        }
    }
}
