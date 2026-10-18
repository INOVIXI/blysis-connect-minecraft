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
 * Every action context this plugin builds can run the two command verbs.
 *
 * <p>{@code [console]} and {@code [player]} dispatch through a sink the caller wires, and until
 * 2026-09-22 the default was a sink that discarded the line. An operator writes {@code [console] give
 * %player% diamond} into an effects list, the file is right, the log is clean, and the command never
 * runs. That is the failure this estate keeps paying for, and it is why the delay seam throws instead.
 *
 * <p>The library's default throws now and names the seam, which turns silence into a line in the
 * console. This is the other half: a plugin should not be relying on that message, because the operator
 * who reads it has already written a file that does nothing.
 *
 * <p>The scan is by file, and a file that builds a context has to name both seams. That is coarse on
 * purpose, the same way {@code EveryBossBarComesDownTest} is: it does not follow a builder through a
 * method chain, it asks whether the plugin knows the seams exist at all.
 */
final class EveryCommandVerbHasASinkTest {

    private static final Path SOURCE_ROOT = Path.of("src", "main", "java");

    /** Where a context is built. */
    private static final String BUILDS_A_CONTEXT = "ActionContext.builder(";

    /** The two seams a command verb dispatches through. */
    private static final List<String> SEAMS = List.of(".consoleSink(", ".playerSink(");

    @Test
    @DisplayName("every action context this plugin builds can run a console and a player command")
    void everycontextWiresBothSinks() throws IOException {
        List<String> unwired = new ArrayList<>();
        for (Path file : java()) {
            String body = withoutSpaces(Files.readString(file, StandardCharsets.UTF_8));
            if (!body.contains(BUILDS_A_CONTEXT)) {
                continue;
            }
            for (String seam : SEAMS) {
                if (!body.contains(seam)) {
                    unwired.add(file.getFileName() + " has no " + seam);
                }
            }
        }

        assertThat(unwired)
                .describedAs("a context with no sink wired discards the command an operator wrote, or, on a"
                        + " current library, throws out of the middle of their list. Wire"
                        + " ActionContext.Builder.consoleSink and .playerSink to this plugin's dispatcher.")
                .isEmpty();
    }

    @Test
    @DisplayName("the scan finds the contexts, so it cannot pass by reading nothing")
    void thescanFindsTheContexts() throws IOException {
        List<String> building = new ArrayList<>();
        for (Path file : java()) {
            if (withoutSpaces(Files.readString(file, StandardCharsets.UTF_8)).contains(BUILDS_A_CONTEXT)) {
                building.add(file.getFileName().toString());
            }
        }

        assertThat(building)
                .describedAs("this plugin runs action lists, so finding no context is a broken scan")
                .isNotEmpty();
    }

    private static List<Path> java() throws IOException {
        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            return files.filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .toList();
        }
    }

    /**
     * The source with every space taken out, which is what a token search reads.
     *
     * <p>A guard that looks for a text fragment can be defeated by the formatter: {@code spotlessApply}
     * wraps a long chain at the dot, so {@code .consoleSink(} sits at the head of its own line and a
     * search for the text as written finds nothing.
     */
    private static String withoutSpaces(String body) {
        return body.replaceAll("\\s+", "");
    }
}
