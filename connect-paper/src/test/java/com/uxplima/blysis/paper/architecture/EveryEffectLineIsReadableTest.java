package com.uxplima.blysis.paper.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.uxplima.uxmlib.condition.action.ActionParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * Every line of every shipped effects list is one the engine can read, and every catalogue key one of
 * them names is a key the words answer.
 *
 * <p>An announcement is a list the operator writes, and the two ways to get it wrong are both quiet. A
 * verb the engine does not know is skipped with a warning in a log nobody reads. A key that is not in the
 * words falls back to its own path, so the player is told a dotted path in place of a sentence. A shipped
 * file that is wrong is wrong on every server that installs this plugin.
 *
 * <p>The lines are read out of the file rather than off the record, because the file is what a first
 * boot writes and the file is what the operator edits.
 */
final class EveryEffectLineIsReadableTest {

    private static final Path RESOURCES = Path.of("src", "main", "resources");

    private static final Path MESSAGES = RESOURCES.resolve("messages");

    /** A line: its verb, any modifier block after it, and its payload. */
    private static final Pattern LINE = Pattern.compile("^\\[([a-z-]+)\\](?:\\s*\\{[^}]*\\})?\\s*(.*)$");

    /** The times after a subtitle's second line: up to three numbers at the end. */
    private static final Pattern TIMES = Pattern.compile("(\\s+[0-9.]+){1,3}\\s*$");

    /** A text part that names its words by key, and nothing else. */
    private static final Pattern KEY = Pattern.compile("^@([A-Za-z0-9._-]+)$");

    @Test
    @DisplayName("every shipped effect line is one the action engine can read")
    void everylineIsReadable() throws IOException, ConfigurateException {
        List<String> unreadable = new ArrayList<>();
        for (String line : shippedEffectLines()) {
            try {
                ActionParser.parse(line);
            } catch (IllegalArgumentException refused) {
                unreadable.add(line + " (" + refused.getMessage() + ")");
            }
        }

        assertThat(unreadable)
                .describedAs("A line the engine cannot read does nothing, and it does nothing quietly.")
                .isEmpty();
    }

    @Test
    @DisplayName("every catalogue key a shipped effect line names is answered by every language")
    void everykeyIsAnswered() throws IOException, ConfigurateException {
        Set<String> named = new LinkedHashSet<>();
        for (String line : shippedEffectLines()) {
            named.addAll(keysIn(line));
        }

        List<String> missing = new ArrayList<>();
        for (Path file : catalogues()) {
            Set<String> answered = pathsIn(file);
            for (String path : named) {
                if (!answered.contains(path)) {
                    missing.add(file.getFileName() + " has no " + path);
                }
            }
        }

        assertThat(missing)
                .describedAs("A key the words do not answer reaches the player as its own path.")
                .isEmpty();
    }

    @Test
    @DisplayName("a key is read from every text part, the way the library reads it")
    void everyTextPartIsRead() {
        // A key after a pipe used to go unread here, so a typo in the second half of a title passed.
        assertThat(keysIn("[subtitle] @a.title | @a.subtitle 0.1 1.2 0.3")).containsExactly("a.title", "a.subtitle");
        assertThat(keysIn("[bossbar] 5 RED PROGRESS | @a.bar")).containsExactly("a.bar");
        assertThat(keysIn("[message] {delay=40} @a.chat")).containsExactly("a.chat");
        assertThat(keysIn("[message] mail me @ the forum")).isEmpty();
    }

    /**
     * The keys one line names, one per text part, the way the library reads them: both sides of a subtitle's
     * pipe, the words of a bar after its seconds, and the whole payload of any other verb.
     */
    private static List<String> keysIn(String line) {
        Matcher read = LINE.matcher(line.strip());
        if (!read.matches()) {
            return List.of();
        }
        String verb = read.group(1);
        String payload = read.group(2);
        int pipe = payload.indexOf('|');
        List<String> parts = new ArrayList<>();
        if (pipe >= 0 && verb.equals("subtitle")) {
            parts.add(payload.substring(0, pipe));
            parts.add(TIMES.matcher(payload.substring(pipe + 1)).replaceFirst(""));
        } else if (pipe >= 0 && (verb.equals("bossbar") || verb.equals("questbar"))) {
            parts.add(payload.substring(pipe + 1));
        } else {
            parts.add(payload);
        }
        List<String> keys = new ArrayList<>();
        for (String part : parts) {
            Matcher key = KEY.matcher(part.strip());
            if (key.matches()) {
                keys.add(key.group(1));
            }
        }
        return keys;
    }

    @Test
    @DisplayName("both sides are read, so this cannot pass by finding nothing")
    void bothsidesAreRead() throws IOException, ConfigurateException {
        assertThat(shippedEffectLines())
                .describedAs("this plugin ships effect lists, so a scan that finds none is a broken scan")
                .hasSizeGreaterThan(5);
        assertThat(catalogues())
                .describedAs("this plugin ships language files, so a scan that finds none is a broken scan")
                .hasSizeGreaterThan(1);
    }

    /**
     * Every line of every list under the shipped {@code announcements} block: each event's everyone, player
     * and once lists. The lines are read out of the file, because the file is what a first boot writes.
     */
    private static List<String> shippedEffectLines() throws IOException, ConfigurateException {
        ConfigurationNode announcements = HoconConfigurationLoader.builder()
                .path(RESOURCES.resolve("config.conf"))
                .build()
                .load()
                .node("announcements");
        List<String> lines = new ArrayList<>();
        for (ConfigurationNode event : announcements.childrenMap().values()) {
            for (ConfigurationNode list : event.childrenMap().values()) {
                lines.addAll(list.getList(String.class, List.of()));
            }
        }
        return lines;
    }

    private static Set<String> pathsIn(Path file) throws ConfigurateException {
        Set<String> paths = new LinkedHashSet<>();
        collect(HoconConfigurationLoader.builder().path(file).build().load(), "", paths);
        return paths;
    }

    private static void collect(ConfigurationNode node, String prefix, Set<String> paths) {
        if (node.isMap()) {
            node.childrenMap()
                    .forEach((name, child) ->
                            collect(child, prefix.isEmpty() ? name.toString() : prefix + "." + name, paths));
            return;
        }
        if (!prefix.isEmpty()) {
            paths.add(prefix);
        }
    }

    private static List<Path> catalogues() throws IOException {
        try (Stream<Path> files = Files.list(MESSAGES)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".conf"))
                    .sorted()
                    .toList();
        }
    }
}
