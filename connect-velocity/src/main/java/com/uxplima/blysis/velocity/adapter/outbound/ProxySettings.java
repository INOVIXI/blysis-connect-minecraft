package com.uxplima.blysis.velocity.adapter.outbound;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.uxplima.blysis.adapter.outbound.ConnectSettingsFile;
import com.uxplima.blysis.application.ConnectSettings;
import com.uxplima.uxmlib.config.HoconConfig;
import com.uxplima.uxmlib.text.language.LanguageSettings;
import org.jspecify.annotations.Nullable;

/**
 * The typed view of the proxy's {@code config.conf} and {@code commands.conf}, read once on start and once per
 * reload and swapped in as one snapshot. Nothing on the proxy reads a configuration value by string path
 * outside this class.
 */
public final class ProxySettings {

    /** One command's names: the label, its aliases, and whether it is registered at all. */
    public record CommandNames(String name, List<String> aliases, boolean enabled) {

        public CommandNames {
            Objects.requireNonNull(name, "name");
            aliases = List.copyOf(aliases);
        }
    }

    /** One immutable snapshot of both files. */
    public record Snapshot(LanguageSettings language, ConnectSettings connect, Map<String, CommandNames> commands) {

        public Snapshot {
            Objects.requireNonNull(language, "language");
            Objects.requireNonNull(connect, "connect");
            commands = Map.copyOf(commands);
        }

        /** The names a command is registered under, or its own name when the file says nothing. */
        public CommandNames command(String key) {
            return commands.getOrDefault(key, new CommandNames(key, List.of(), true));
        }
    }

    /** The commands the proxy offers, by the key the file names them under. */
    public static final List<String> COMMANDS = List.of("credits", "link", "verify", "blysisproxy");

    private final Path config;
    private final Path commandsFile;
    private volatile Snapshot current;

    public ProxySettings(Path config, Path commandsFile) {
        this.config = Objects.requireNonNull(config, "config");
        this.commandsFile = Objects.requireNonNull(commandsFile, "commandsFile");
        this.current = read();
    }

    /** Adds the keys this release ships to a file an operator already has. Nothing they wrote changes. */
    public static boolean bringUpToDate(Path file, ClassLoader resources) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(resources, "resources");
        return Files.isRegularFile(file) && HoconConfig.load(file).mergeDefaults("config.conf", resources);
    }

    public Snapshot current() {
        return current;
    }

    /** Reads both files again. A parse error propagates and the last good snapshot stays. */
    public void reload() {
        this.current = read();
    }

    private Snapshot read() {
        HoconConfig settings = HoconConfig.load(config);
        return new Snapshot(language(settings), ConnectSettingsFile.read(settings), commands());
    }

    private Map<String, CommandNames> commands() {
        if (!Files.isRegularFile(commandsFile)) {
            return Map.of();
        }
        HoconConfig file = HoconConfig.load(commandsFile);
        java.util.Map<String, CommandNames> read = new java.util.HashMap<>();
        for (String key : COMMANDS) {
            String base = "commands." + key;
            read.put(
                    key,
                    new CommandNames(
                            file.getString(base + ".name", key),
                            file.getList(base + ".aliases", String.class),
                            file.getBoolean(base + ".enabled", true)));
        }
        return read;
    }

    private static LanguageSettings language(HoconConfig config) {
        return new LanguageSettings(
                localeOf(config.getString("language.default", "en")),
                config.getBoolean("language.follow-client", true),
                forced(config.getString("language.force", "")));
    }

    private static @Nullable Locale forced(String tag) {
        return tag.isBlank() ? null : localeOf(tag);
    }

    private static Locale localeOf(String tag) {
        Locale locale = Locale.forLanguageTag(tag.trim().replace('_', '-'));
        return locale.getLanguage().isEmpty() ? Locale.ENGLISH : locale;
    }
}
