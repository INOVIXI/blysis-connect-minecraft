package com.uxplima.blysis.paper.adapter.inbound;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The event's fields written into an announcement's command effect, each as one argument.
 *
 * <p>A message line takes the fields through the message engine, which inserts them as text. A command line
 * is dispatched as written, so the fields are written into it here. They are what somebody typed on the site,
 * and a command reads a space as the end of an argument, an {@code @} as a selector and a line break as the
 * end of the line, so each value keeps letters, digits and {@code _ . - : #} only, with every space made an
 * underscore. A real player name passes unchanged.
 */
final class CommandFields {

    /** A line whose effect is a command. Its modifiers, if any, come after the bracket. */
    private static final Pattern COMMAND_LINE = Pattern.compile("^\\s*\\[(console|player)]", Pattern.CASE_INSENSITIVE);

    private static final Pattern SPACE = Pattern.compile("\\s");

    private static final Pattern NOT_KEPT = Pattern.compile("[^\\p{L}\\p{N}_.\\-:#]");

    /** Past any name and any product title a site keeps. */
    private static final int MOST = 64;

    /** The names of the fields that say who an event is for. A command naming one is a command for a player. */
    private static final List<String> PLAYER = List.of("player", "player-name", "player-id");

    /** What a field is written under, besides its own name. */
    private static final String EVENT = "event-";

    private CommandFields() {}

    /**
     * The line with the fields written in, or empty when it is a command for a player the event does not
     * name: an anonymous purchase gives no one a rank.
     */
    static Optional<String> fill(String line, Map<String, String> fields) {
        Objects.requireNonNull(line, "line");
        Objects.requireNonNull(fields, "fields");
        if (!COMMAND_LINE.matcher(line).find()) {
            return Optional.of(line);
        }
        String filled = line;
        for (Map.Entry<String, String> field : fields.entrySet()) {
            String name = field.getKey().replace('.', '-');
            String value = argument(field.getValue());
            filled = filled.replace(token(name), value).replace(token(EVENT + name), value);
        }
        for (String name : PLAYER) {
            if (filled.contains(token(name)) || filled.contains(token(EVENT + name))) {
                return Optional.empty();
            }
        }
        return Optional.of(filled);
    }

    /** A placeholder as a line writes it, built here because it is a name and never words a player reads. */
    private static String token(String name) {
        return '<' + name + '>';
    }

    /** One argument of a command, which a typed value can neither widen nor end. */
    static String argument(String value) {
        String kept = NOT_KEPT.matcher(SPACE.matcher(value).replaceAll("_")).replaceAll("");
        return kept.length() > MOST ? kept.substring(0, MOST) : kept;
    }
}
