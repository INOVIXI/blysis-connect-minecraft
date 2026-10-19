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
 * end of the line. So a value goes in only as one plain argument: letters, digits and {@code _ . - : #}, no
 * space, no dash in front where a command reads a flag. A real player name is one.
 *
 * <p>A value that is not one is not mended. A name stripped of what it may not carry can come out as another
 * player's ("St@eve" is Steve), and any mended value is a guess at what somebody meant; a guess in a console
 * command is how a rank lands on the wrong account. Such a line does not run, and neither does one that names
 * a player for an event that names none.
 */
final class CommandFields {

    /** A line whose effect is a command. Its modifiers, if any, come after the bracket. */
    private static final Pattern COMMAND_LINE = Pattern.compile("^\\s*\\[(console|player)]", Pattern.CASE_INSENSITIVE);

    private static final Pattern NOT_KEPT = Pattern.compile("[^\\p{L}\\p{N}_.\\-:#]");

    /** Past any name and any product title a site keeps. */
    private static final int MOST = 64;

    /** The names of the fields that say who an event is for. A command naming one is a command for a player. */
    private static final List<String> PLAYER = List.of("player", "player-name", "player-id");

    private static final Pattern LEADING_DASHES = Pattern.compile("^-+");

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
            if (!filled.contains(token(name)) && !filled.contains(token(EVENT + name))) {
                continue;
            }
            String value = field.getValue();
            if (!isArgument(value)) {
                return Optional.empty();
            }
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

    /** Whether a value is one plain argument a command reads as it is: never empty, widened, ended or a flag. */
    static boolean isArgument(String value) {
        return !value.isEmpty()
                && value.length() <= MOST
                && !NOT_KEPT.matcher(value).find()
                && !LEADING_DASHES.matcher(value).find();
    }
}
