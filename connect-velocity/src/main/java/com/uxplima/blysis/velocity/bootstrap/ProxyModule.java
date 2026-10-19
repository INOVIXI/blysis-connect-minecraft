package com.uxplima.blysis.velocity.bootstrap;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.blysis.adapter.outbound.ConsoleWatcher;
import com.uxplima.blysis.adapter.outbound.SiteConnection;
import com.uxplima.blysis.application.ConnectSettings;
import com.uxplima.blysis.application.SiteSession;
import com.uxplima.blysis.domain.LinkState;
import com.uxplima.blysis.domain.StatusPush;
import com.uxplima.blysis.velocity.adapter.inbound.ProxyWatcher;
import com.uxplima.blysis.velocity.adapter.inbound.Replies;
import com.uxplima.blysis.velocity.adapter.inbound.command.AdminCommand;
import com.uxplima.blysis.velocity.adapter.inbound.command.CreditsCommand;
import com.uxplima.blysis.velocity.adapter.inbound.command.LinkCommand;
import com.uxplima.blysis.velocity.adapter.inbound.command.VerifyCommand;
import com.uxplima.blysis.velocity.adapter.outbound.ProxyConsole;
import com.uxplima.blysis.velocity.adapter.outbound.ProxyLanes;
import com.uxplima.blysis.velocity.adapter.outbound.ProxyLocales;
import com.uxplima.blysis.velocity.adapter.outbound.ProxyPlayers;
import com.uxplima.blysis.velocity.adapter.outbound.ProxySettings;
import com.uxplima.blysis.velocity.application.message.Keys;
import com.uxplima.uxmlib.text.language.LanguageFiles;
import com.uxplima.uxmlib.text.language.Languages;
import com.uxplima.uxmlib.text.message.MessageCatalog;
import com.uxplima.uxmlib.text.message.Messages;
import com.uxplima.uxmlib.text.style.Styler;
import com.uxplima.uxmlib.text.style.Theme;
import com.uxplima.uxmlib.text.style.ThemeFiles;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.configurate.ConfigurateException;

/**
 * The composition root on a proxy. Every object is built here and handed its collaborators through a
 * constructor, and this is the only class besides the shell that names the proxy.
 */
final class ProxyModule {

    private static final String MESSAGES = "messages";
    private static final String THEME_FOLDER = "uxmTheme";

    private final Object plugin;
    private final ProxyServer proxy;
    private final Logger logger;
    private final Path folder;
    private final String version;
    private final ProxyPlayers players;
    private final ProxyLanes lanes;

    private @Nullable ProxySettings settings;
    private @Nullable Messages messages;
    private volatile @Nullable SiteConnection running;

    ProxyModule(Object plugin, ProxyServer proxy, Logger logger, Path folder, String version) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.proxy = Objects.requireNonNull(proxy, "proxy");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.folder = Objects.requireNonNull(folder, "folder");
        this.version = Objects.requireNonNull(version, "version");
        this.players = new ProxyPlayers(proxy::getAllPlayers);
        this.lanes = new ProxyLanes(proxy.getScheduler(), plugin);
    }

    /** Builds the graph, registers the commands and connects. */
    void wire() {
        extractDefaults();
        ProxySettings read = new ProxySettings(folder.resolve("config.conf"), folder.resolve("commands.conf"));
        this.settings = read;
        Messages words = new Messages(
                catalog(read), new ProxyLocales(() -> read.current().language()));
        this.messages = words;
        Replies replies = new Replies(words, lanes, this::session);
        registerCommands(read, words, replies);
        proxy.getEventManager().register(plugin, PostLoginEvent.class, event -> statusSoon());
        proxy.getEventManager().register(plugin, DisconnectEvent.class, event -> statusSoon());
        start(read, new ProxyWatcher(players, words, replies));
    }

    private @Nullable ProxyWatcher watcher;

    private synchronized void start(ProxySettings read, ProxyWatcher players) {
        this.watcher = players;
        ConnectSettings connect = read.current().connect();
        if (!connect.configured()) {
            logger.warn("site.address and site.token in config.conf are empty, so this proxy connects to no site."
                    + " Make a token on the site under Game Connect, write both, and run /blysisproxy reload.");
            return;
        }
        connect.insecureAddress()
                .ifPresent(address -> logger.warn(
                        "site.address is plain http to another machine ({}). The token crosses the network in clear:"
                                + " use https.",
                        address));
        running = new SiteConnection(connect, () -> read.current().connect(), platform(players));
    }

    private SiteConnection.Platform platform(ProxyWatcher events) {
        return new SiteConnection.Platform(
                folder,
                version,
                proxy.getVersion().getName(),
                proxy.getVersion().getVersion(),
                "network-root",
                new ProxyConsole(proxy.getCommandManager(), proxy.getConsoleCommandSource()),
                players,
                lanes,
                this::status,
                new ConsoleWatcher(new SlfConsole(logger), events));
    }

    /** Reads every file again, and connects again when the address or the token changed, or the site stopped it. */
    synchronized boolean reload() {
        ProxySettings read = settings;
        Messages words = messages;
        ProxyWatcher events = watcher;
        if (read == null || words == null || events == null) {
            return false;
        }
        try {
            read.reload();
            words.reload(catalog(read));
        } catch (RuntimeException unreadable) {
            logger.error("The reload failed and the last good configuration stays: {}", unreadable.getMessage());
            return false;
        }
        SiteConnection now = running;
        if (now != null && !now.settings().reconnectsFor(read.current().connect(), now.state())) {
            return true;
        }
        stop();
        start(read, events);
        return true;
    }

    synchronized void stop() {
        SiteConnection now = running;
        running = null;
        if (now != null) {
            now.close();
        }
    }

    private Optional<SiteSession> session() {
        SiteConnection now = running;
        return now == null ? Optional.empty() : Optional.of(now.session());
    }

    private Optional<LinkState> state() {
        SiteConnection now = running;
        return now == null ? Optional.empty() : Optional.of(now.state());
    }

    private Optional<Integer> waiting() {
        SiteConnection now = running;
        return now == null ? Optional.empty() : Optional.of(now.unreported());
    }

    private void statusSoon() {
        SiteConnection now = running;
        if (now != null) {
            now.statusSoon();
        }
    }

    private StatusPush status() {
        return new StatusPush(
                proxy.getPlayerCount(),
                proxy.getConfiguration().getShowMaxPlayers(),
                players.all(),
                proxy.getVersion().getVersion(),
                20,
                0);
    }

    private void registerCommands(ProxySettings read, Messages words, Replies replies) {
        register(read, "credits", new CreditsCommand(replies, words));
        register(read, "link", new LinkCommand(replies, words));
        register(read, "verify", new VerifyCommand(replies, words));
        register(read, "blysisproxy", new AdminCommand(words, version, this::reload, this::state, this::waiting));
    }

    private void register(ProxySettings read, String key, SimpleCommand command) {
        ProxySettings.CommandNames names = read.current().command(key);
        if (!names.enabled()) {
            return;
        }
        CommandMeta meta = proxy.getCommandManager()
                .metaBuilder(names.name())
                .aliases(names.aliases().toArray(String[]::new))
                .plugin(plugin)
                .build();
        proxy.getCommandManager().register(meta, command);
    }

    /** The words, painted, read from the folder so a language is gained by gaining a file. */
    private MessageCatalog catalog(ProxySettings read) {
        Locale fallback = read.current().language().defaultLocale();
        Languages languages = Languages.load(folder.resolve(MESSAGES), fallback);
        for (String problem : languages.problems()) {
            logger.warn(problem);
        }
        return new Styler(theme()).style(languages.catalog(), List.of(Keys.values()), languages.entries(), fallback);
    }

    private Theme theme() {
        try {
            return ThemeFiles.load(ThemeFiles.shared(folder, THEME_FOLDER), ThemeFiles.own(folder));
        } catch (ConfigurateException | IllegalArgumentException unreadable) {
            logger.warn("Cannot read theme.conf. The shipped palette is used.", unreadable);
            return Theme.defaults();
        }
    }

    /** Writes the shipped files on the first start, and gives an older config.conf the keys this release adds. */
    private void extractDefaults() {
        copyIfAbsent("config.conf", folder.resolve("config.conf"));
        copyIfAbsent("commands.conf", folder.resolve("commands.conf"));
        try {
            if (ProxySettings.bringUpToDate(
                    folder.resolve("config.conf"), getClass().getClassLoader())) {
                logger.info("config.conf has the keys this release adds. Nothing you wrote changed, and the file as it"
                        + " was is beside it as config.conf.bak.");
            }
        } catch (RuntimeException unreadable) {
            logger.warn("config.conf could not be brought up to date: {}", unreadable.getMessage());
        }
        copyIfAbsent("theme.conf", ThemeFiles.shared(folder, THEME_FOLDER));
        LanguageFiles.extractShipped(getClass().getClassLoader(), MESSAGES, folder.resolve(MESSAGES));
    }

    private void copyIfAbsent(String resource, Path target) {
        if (Files.isRegularFile(target)) {
            return;
        }
        try (InputStream shipped = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (shipped == null) {
                return;
            }
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(shipped, target);
        } catch (IOException unwritable) {
            logger.warn("Cannot write {}: {}", target, unwritable.getMessage());
        }
    }

    /** The proxy's console, at the three levels the connection speaks in. */
    private record SlfConsole(Logger logger) implements ConsoleWatcher.Console {

        @Override
        public void info(String line) {
            logger.info(line);
        }

        @Override
        public void warn(String line) {
            logger.warn(line);
        }

        @Override
        public void error(String line) {
            logger.error(line);
        }
    }
}
