package com.uxplima.blysis.paper.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import com.uxplima.uxmlib.condition.action.ActionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every seam the library's action engine offers is wired wherever this plugin builds a context.
 *
 * <p>The engine answers an unwired seam rather than failing on one. An unwired wallet reads every balance
 * as zero and refuses every take, an unwired item store answers "none" to every item question, an unwired
 * sink discards the command it was given. Each of those is a wrong answer rather than a missing one: the
 * operator's line is skipped, the player sees nothing, and the console says nothing either. That is the
 * shape of the defect this guard exists for, and it is the shape three plugins of this estate shipped in.
 *
 * <p>The list of seams is read off {@link ActionContext.Builder} rather than written here, so a seam the
 * library adds tomorrow is a failure here today rather than a hole nobody notices. A seam that genuinely
 * has nothing behind it is still named, through the empty form the library ships for it, which is a
 * decision written in the source rather than an omission.
 */
final class EverySeamTheEffectEngineOffersIsWiredTest {

    /**
     * The subject, and the one seam that may be absent.
     *
     * <p>A player is the subject of a context rather than a capability of it, and a context fired for the
     * server has no subject: a backup that finished, a broadcast nobody asked for. The library has no
     * empty player to name in that case, so absence is the only way to say it. Every other seam has one.
     */
    private static final String SUBJECT = "player";

    /**
     * Who the lines are said to, which the subject sets as well.
     *
     * <p>{@code player(...)} makes the player the target too, because a player is an audience, so a
     * context that names a subject has already answered this and a file that names both is overriding
     * rather than repeating. One of the two has to be there: a context that names neither says its lines
     * to nobody.
     */
    private static final String TARGET = "target";

    @Test
    @DisplayName("every file that builds a context wires every seam the engine offers")
    void everySeamIsNamedWhereverAContextIsBuilt() throws IOException {
        List<Path> builders = filesThatBuildAContext();

        assertThat(builders)
                .describedAs("this plugin runs an operator's action list, so something here builds a context")
                .isNotEmpty();

        List<String> missing = new ArrayList<>();
        for (Path file : builders) {
            String source = Files.readString(file);
            for (String seam : seams()) {
                if (!names(source, seam)) {
                    missing.add(file.getFileName() + " never names ." + seam + "(...)");
                }
            }
            if (!names(source, SUBJECT) && !names(source, TARGET)) {
                missing.add(file.getFileName() + " names neither a subject nor a target, so it says its"
                        + " lines to nobody");
            }
        }

        assertThat(missing)
                .describedAs("an unwired seam answers wrongly rather than failing, so nothing reports it")
                .isEmpty();
    }

    @Test
    @DisplayName("the seams are read off the library, so a new one is a failure rather than a hole")
    void theSeamsComeFromTheLibraryItself() {
        assertThat(seams())
                .describedAs("if this set changed, the library moved and every context here has to answer for it")
                .containsExactlyInAnyOrder(
                        "later", "broadcast", "consoleSink", "playerSink", "wallet", "itemStore", "words", "style");
        assertThat(everySeam()).contains(SUBJECT, TARGET);
    }

    /** Every seam that has to be named on its own. */
    private static Set<String> seams() {
        Set<String> named = new TreeSet<>(everySeam());
        named.remove(SUBJECT);
        named.remove(TARGET);
        return named;
    }

    private static boolean names(String source, String seam) {
        return source.contains("." + seam + "(");
    }

    /** Every method of the builder that returns the builder, which is what a seam looks like. */
    private static Set<String> everySeam() {
        Set<String> found = new LinkedHashSet<>();
        for (Method method : ActionContext.Builder.class.getDeclaredMethods()) {
            if (Modifier.isPublic(method.getModifiers())
                    && !Modifier.isStatic(method.getModifiers())
                    && method.getReturnType() == ActionContext.Builder.class
                    && method.getParameterCount() == 1) {
                found.add(method.getName());
            }
        }
        return found;
    }

    private static List<Path> filesThatBuildAContext() throws IOException {
        List<Path> found = new ArrayList<>();
        try (Stream<Path> sources = Files.walk(Path.of("src", "main", "java"))) {
            for (Path file :
                    sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                if (Files.readString(file).contains("ActionContext.builder(")) {
                    found.add(file);
                }
            }
        }
        return found;
    }
}
