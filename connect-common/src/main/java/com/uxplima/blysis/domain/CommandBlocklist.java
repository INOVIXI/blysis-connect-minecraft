package com.uxplima.blysis.domain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The commands an operator never lets the site run, and optionally the only ones it may.
 *
 * <p>A site that was taken over can still queue anything; this is the line it cannot cross on this server.
 * A word is compared without its slash and its {@code namespace:} prefix, under every name the server knows
 * it by, so {@code /minecraft:op} and an alias of {@code op} are {@code op}. A command that runs another
 * ({@code execute ... run}, {@code return run}) is judged by every command inside it as well as itself.
 *
 * <p>A blocklist names what is known to be dangerous and cannot name a plugin's command it has never heard
 * of. An operator who wants the strict form lists the commands the site may run in {@code allowOnly}, and
 * everything else is refused.
 */
public final class CommandBlocklist {

    /** The words after which the rest of an {@code execute} or {@code return} is a command of its own. */
    private static final Set<String> WRAPPERS = Set.of("execute", "return");

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final Set<String> blocked;
    private final Set<String> allowOnly;

    public CommandBlocklist(Collection<String> blocked) {
        this(blocked, List.of());
    }

    public CommandBlocklist(Collection<String> blocked, Collection<String> allowOnly) {
        this.blocked = labels(Objects.requireNonNull(blocked, "blocked"));
        this.allowOnly = labels(Objects.requireNonNull(allowOnly, "allowOnly"));
    }

    /** True when the command, or any command inside it, may not run. Words are only compared as written. */
    public boolean blocks(String command) {
        return blocks(command, word -> Set.of(word));
    }

    /**
     * True when the command, or any command inside it, may not run.
     *
     * @param names every name the server knows a command word by, the word itself among them: its aliases
     *     and the command they point at
     */
    public boolean blocks(String command, Function<String, Set<String>> names) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(names, "names");
        for (String inner : commandsIn(command)) {
            String word = label(inner);
            if (word.isEmpty()) {
                return true;
            }
            Set<String> known =
                    names.apply(word).stream().map(CommandBlocklist::label).collect(Collectors.toSet());
            known.add(word);
            if (known.stream().anyMatch(blocked::contains)) {
                return true;
            }
            if (!allowOnly.isEmpty() && known.stream().noneMatch(allowOnly::contains)) {
                return true;
            }
        }
        return false;
    }

    /** The command and every command it runs, outermost first. */
    static List<String> commandsIn(String command) {
        List<String> found = new ArrayList<>();
        String current = command.strip();
        while (true) {
            found.add(current);
            if (!WRAPPERS.contains(label(current))) {
                return found;
            }
            String[] words = WHITESPACE.split(current, -1);
            int run = -1;
            for (int i = 1; i < words.length; i++) {
                if (words[i].equalsIgnoreCase("run")) {
                    run = i;
                    break;
                }
            }
            if (run < 0 || run == words.length - 1) {
                return found;
            }
            current = String.join(" ", Arrays.copyOfRange(words, run + 1, words.length));
        }
    }

    /** The first word of a command, lower case, without its slash and namespace. */
    static String label(String command) {
        String trimmed = command.strip();
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        String[] words = WHITESPACE.split(trimmed, 2);
        String word = words.length == 0 ? "" : words[0];
        int colon = word.lastIndexOf(':');
        if (colon >= 0) {
            word = word.substring(colon + 1);
        }
        return word.toLowerCase(Locale.ROOT);
    }

    private static Set<String> labels(Collection<String> words) {
        return words.stream()
                .map(CommandBlocklist::label)
                .filter(word -> !word.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}
