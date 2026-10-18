package com.uxplima.blysis.domain;

import java.util.Collection;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The commands an operator never lets the site run, whatever the site sends.
 *
 * <p>The first word is compared without a leading slash and without a {@code namespace:} prefix, so
 * {@code /minecraft:op} is the same command as {@code op}. A site that was taken over can still queue
 * anything; this is the line it cannot cross on this server.
 */
public final class CommandBlocklist {

    private final Set<String> blocked;

    public CommandBlocklist(Collection<String> blocked) {
        Objects.requireNonNull(blocked, "blocked");
        this.blocked = blocked.stream()
                .map(CommandBlocklist::label)
                .filter(word -> !word.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    /** True when the command's first word is one the operator blocked. */
    public boolean blocks(String command) {
        Objects.requireNonNull(command, "command");
        return blocked.contains(label(command));
    }

    /** The first word of a command, lower case, without its slash and namespace. */
    static String label(String command) {
        String trimmed = command.strip();
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        int space = trimmed.indexOf(' ');
        String word = space < 0 ? trimmed : trimmed.substring(0, space);
        int colon = word.indexOf(':');
        if (colon >= 0) {
            word = word.substring(colon + 1);
        }
        return word.toLowerCase(Locale.ROOT);
    }
}
