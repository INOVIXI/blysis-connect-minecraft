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
 * Every judgement errs toward refusing: a refused purchase command is a ticket, a run one may be a server.
 *
 * <p>The blocklist compares a word without its slash and its {@code namespace:} prefix, under every name the
 * server knows it by, so {@code /minecraft:op} and an alias of {@code op} are {@code op}. A command that runs
 * another ({@code execute ... run}, {@code return run}) is judged by every command that could follow any of
 * its {@code run} words, because a score holder or a storage path may itself be called {@code run}.
 *
 * <p>The allow list is the strict form: everything not on it is refused. A word written with a namespace
 * passes only when the list names it with that namespace, because {@code someplugin:give} is not the
 * {@code give} the operator allowed.
 */
public final class CommandBlocklist {

    /** The words after which part of a command is a command of its own. */
    private static final Set<String> WRAPPERS = Set.of("execute", "return");

    /** More candidates than any honest command has. Past it the command is refused unread. */
    private static final int MOST_CANDIDATES = 64;

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final Set<String> blocked;
    private final Set<String> allowOnly;

    public CommandBlocklist(Collection<String> blocked) {
        this(blocked, List.of());
    }

    public CommandBlocklist(Collection<String> blocked, Collection<String> allowOnly) {
        this.blocked = words(Objects.requireNonNull(blocked, "blocked"), CommandBlocklist::label);
        this.allowOnly = words(Objects.requireNonNull(allowOnly, "allowOnly"), CommandBlocklist::typed);
    }

    /** True when the command, or any command inside it, may not run. Words are only compared as written. */
    public boolean blocks(String command) {
        return blocks(command, word -> Set.of(word));
    }

    /**
     * True when the command, or any command that could be inside it, may not run.
     *
     * @param names every name the server knows a bare command word by: the command an alias points at and
     *     its own aliases, the word itself among them
     */
    public boolean blocks(String command, Function<String, Set<String>> names) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(names, "names");
        List<String> candidates = new ArrayList<>();
        if (!collect(command.strip(), candidates)) {
            return true;
        }
        for (String candidate : candidates) {
            if (refuses(candidate, names)) {
                return true;
            }
        }
        return false;
    }

    private boolean refuses(String command, Function<String, Set<String>> names) {
        String bare = label(command);
        if (bare.isEmpty()) {
            return true;
        }
        Set<String> known =
                names.apply(bare).stream().map(CommandBlocklist::label).collect(Collectors.toSet());
        known.add(bare);
        if (known.stream().anyMatch(blocked::contains)) {
            return true;
        }
        if (allowOnly.isEmpty()) {
            return false;
        }
        String written = typed(command);
        if (allowOnly.contains(written)) {
            return false;
        }
        // A bare word may pass as any name of the command it reaches; a namespaced one only as written.
        return written.contains(":") || known.stream().noneMatch(allowOnly::contains);
    }

    /**
     * The command and every command that could follow one of its {@code run} words, depth first. False when
     * there are more than any honest command has.
     */
    static boolean collect(String command, List<String> into) {
        into.add(command);
        if (into.size() > MOST_CANDIDATES) {
            return false;
        }
        if (!WRAPPERS.contains(label(command))) {
            return true;
        }
        String[] words = WHITESPACE.split(command, -1);
        for (int i = 1; i < words.length - 1; i++) {
            if (words[i].equalsIgnoreCase("run")
                    && !collect(String.join(" ", Arrays.copyOfRange(words, i + 1, words.length)), into)) {
                return false;
            }
        }
        return true;
    }

    /** The first word of a command, lower case, without its slash and namespace. */
    static String label(String command) {
        String word = typed(command);
        int colon = word.lastIndexOf(':');
        return colon >= 0 ? word.substring(colon + 1) : word;
    }

    /** The first word of a command, lower case, without its slash, with its namespace if it has one. */
    static String typed(String command) {
        String trimmed = command.strip();
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        String[] words = WHITESPACE.split(trimmed, 2);
        return words.length == 0 ? "" : words[0].toLowerCase(Locale.ROOT);
    }

    private static Set<String> words(Collection<String> written, Function<String, String> form) {
        return written.stream().map(form).filter(word -> !word.isEmpty()).collect(Collectors.toUnmodifiableSet());
    }
}
