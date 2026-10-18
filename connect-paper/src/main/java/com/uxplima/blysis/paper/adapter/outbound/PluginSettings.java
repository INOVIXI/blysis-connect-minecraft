package com.uxplima.blysis.paper.adapter.outbound;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
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
 * The typed view of {@code config.conf}. The file is read once on enable and once per reload, and the
 * result is swapped in as one immutable snapshot. A caller reads {@link #current()} on the hot path, so no
 * caller ever sees a half-applied reload.
 *
 * <p>Nothing in this plugin reads a configuration value by string path outside this class.
 */
public final class PluginSettings {

    /**
     * Add the keys this release ships to a file an operator already has.
     *
     * <p>It lives here because this is the one class that reads the configuration file, and the guard that
     * says so is the reason: a second class touching {@code HoconConfig} is a second place the file surface
     * can drift. Nothing a person wrote is changed, and the file as it was is kept beside it.
     *
     * @return whether anything was added, so the caller can say so once in the console
     */
    public static boolean bringUpToDate(Path file, ClassLoader resources) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(resources, "resources");
        if (!Files.isRegularFile(file)) {
            return false;
        }
        return HoconConfig.load(file).mergeDefaults("config.conf", resources);
    }

    /** One immutable snapshot of the file. */
    public record Snapshot(
            LanguageSettings language,
            ConnectSettings connect,
            String voucherMaterial,
            Icons icons,
            Map<String, Announcement> announcements) {

        public Snapshot {
            Objects.requireNonNull(language, "language");
            Objects.requireNonNull(connect, "connect");
            Objects.requireNonNull(voucherMaterial, "voucherMaterial");
            Objects.requireNonNull(icons, "icons");
            announcements = Map.copyOf(announcements);
        }

        /** The language every message falls back to. */
        public Locale defaultLocale() {
            return language.defaultLocale();
        }
    }

    /** The items a store tile falls back to when the site names none this server knows. */
    public record Icons(String shelf, String product) {

        public Icons {
            Objects.requireNonNull(shelf, "shelf");
            Objects.requireNonNull(product, "product");
        }
    }

    /**
     * What one kind of site event does: lines run for every player online, for the player the event names,
     * and once with the console as the target.
     */
    public record Announcement(List<String> everyone, List<String> player, List<String> once) {

        public Announcement {
            everyone = List.copyOf(everyone);
            player = List.copyOf(player);
            once = List.copyOf(once);
        }
    }

    /** The events this release announces before an operator has said otherwise. */
    private static final Map<String, Announcement> SHIPPED_ANNOUNCEMENTS = Map.of(
            "purchase",
            new Announcement(List.of("[message] @announce.purchase"), List.of(), List.of()),
            "topup",
            new Announcement(List.of("[message] @announce.topup"), List.of(), List.of()),
            "goal-reached",
            new Announcement(
                    List.of(
                            "[title] @announce.goal-title|@announce.goal-subtitle",
                            "[sound] ui.toast.challenge_complete 0.7 1"),
                    List.of(),
                    List.of()),
            "link-confirmed",
            new Announcement(
                    List.of(), List.of("[message] @link.done", "[sound] entity.player.levelup 0.6 1.4"), List.of()),
            "gift-received",
            new Announcement(
                    List.of(),
                    List.of("[message] @announce.gift-received", "[sound] entity.experience_orb.pickup 0.7 1.2"),
                    List.of()));

    private final Path file;

    // A volatile reference, not an AtomicReference. A reference assignment is already atomic, and volatile
    // gives the visibility that a reader on another region thread needs. AtomicReference would only add a
    // compare-and-set that nothing here uses, and its get() is nullable, which this field never is.
    private volatile Snapshot current;

    public PluginSettings(Path file) {
        this.file = Objects.requireNonNull(file, "file");
        this.current = read();
    }

    public Snapshot current() {
        return current;
    }

    /** What runs at the end of every reload, in the order it was registered. */
    private final List<Runnable> afterReload = new ArrayList<>();

    /**
     * Run {@code action} after every reload, once the new snapshot is in place.
     *
     * <p>The words and the palette are read by the module rather than here, and they have to move at the same
     * moment as the numbers: a reload that repaints half of what a player sees is worse than one that repaints
     * none. See UI-STYLE.md section 14.2.
     */
    public void afterReload(Runnable action) {
        afterReload.add(Objects.requireNonNull(action, "action"));
    }

    /**
     * Re-read the file. A parse error propagates, and the previous snapshot stays in place, so the server
     * keeps running on the last configuration that was known to be good.
     */
    public void reload() {
        this.current = read();
        for (Runnable action : afterReload) {
            action.run();
        }
    }

    private Snapshot read() {
        HoconConfig config = HoconConfig.load(file);
        return new Snapshot(
                language(config),
                ConnectSettingsFile.read(config),
                config.getString("voucher.material", "PAPER"),
                new Icons(config.getString("icons.shelf", "CHEST"), config.getString("icons.product", "PAPER")),
                announcements(config));
    }

    /**
     * Every event an operator wrote a block for, and the shipped ones they did not touch.
     *
     * <p>A list is asked whether it exists rather than for its size: an absent list keeps the shipped one, and
     * a list emptied on purpose stays empty.
     */
    private static Map<String, Announcement> announcements(HoconConfig config) {
        Map<String, Announcement> read = new HashMap<>(SHIPPED_ANNOUNCEMENTS);
        for (String type : config.keys("announcements")) {
            Announcement shipped = read.getOrDefault(type, new Announcement(List.of(), List.of(), List.of()));
            read.put(
                    type,
                    new Announcement(
                            lines(config, type, "everyone", shipped.everyone()),
                            lines(config, type, "player", shipped.player()),
                            lines(config, type, "once", shipped.once())));
        }
        return read;
    }

    private static List<String> lines(HoconConfig config, String type, String who, List<String> shipped) {
        if (config.nodeAt("announcements", type, who).virtual()) {
            return shipped;
        }
        return List.copyOf(config.getList("announcements." + type + "." + who, String.class));
    }

    /**
     * How a player's language is answered.
     *
     * <p>The three keys are the same in every plugin of ours, and uxmLib holds the order they are read in.
     */
    private static LanguageSettings language(HoconConfig config) {
        return new LanguageSettings(
                localeOf(config.getString("language.default", "en"), Locale.ENGLISH),
                config.getBoolean("language.follow-client", true),
                forcedLocale(config.getString("language.force", "")));
    }

    private static @Nullable Locale forcedLocale(String tag) {
        return tag.isBlank() ? null : localeOf(tag, Locale.ENGLISH);
    }

    private static Locale localeOf(String tag, Locale fallback) {
        Locale locale = Locale.forLanguageTag(tag.trim().replace('_', '-'));
        return locale.getLanguage().isEmpty() ? fallback : locale;
    }
}
