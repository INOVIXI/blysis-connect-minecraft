package com.uxplima.blysis.adapter.outbound;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import com.uxplima.blysis.application.Boards;
import com.uxplima.blysis.application.CatalogCache;
import com.uxplima.blysis.application.ConnectSettings;
import com.uxplima.blysis.application.DeliverJobs;
import com.uxplima.blysis.application.Once;
import com.uxplima.blysis.application.Profiles;
import com.uxplima.blysis.application.ShelfKeeper;
import com.uxplima.blysis.application.SiteLoop;
import com.uxplima.blysis.application.SiteSession;
import com.uxplima.blysis.application.StayConnected;
import com.uxplima.blysis.application.port.Console;
import com.uxplima.blysis.application.port.Lanes;
import com.uxplima.blysis.application.port.LinkWatcher;
import com.uxplima.blysis.application.port.Presence;
import com.uxplima.blysis.domain.Backoff;
import com.uxplima.blysis.domain.LinkState;
import com.uxplima.blysis.domain.StatusPush;
import com.uxplima.uxmlib.storage.sql.Database;

/**
 * One connection to the site and everything it opened: the journal, the HTTP client, the loop that keeps the
 * site called, the kept shelves, profiles and rankings, and their timers. Built from one snapshot of the
 * settings and closed whole, so a reload that changed the address or the token builds a new one.
 *
 * <p>Paper and Velocity build the same object. What differs between them is handed in: where commands run,
 * who is online, which lanes the work runs on, and what the status push says.
 */
public final class SiteConnection implements AutoCloseable {

    /** The client name the site shows beside this server. */
    public static final String CLIENT = "blysis-connect-minecraft";

    private static final Duration DEFAULT_STATUS_INTERVAL = Duration.ofSeconds(30);
    private static final Duration SWEEP_PERIOD = Duration.ofHours(6);
    private static final Duration PROFILE_FRESH = Duration.ofSeconds(30);

    /** A backstop to the site's word that the catalogue changed. */
    private static final Duration SHELF_PERIOD = Duration.ofMinutes(10);

    /** How often a ranking a scoreboard reads is asked again. */
    private static final Duration BOARD_PERIOD = Duration.ofMinutes(1);

    /** What a platform hands a connection. */
    public record Platform(
            Path dataFolder,
            String pluginVersion,
            String software,
            String softwareVersion,
            String role,
            Console console,
            Presence presence,
            Lanes lanes,
            Supplier<StatusPush> status,
            LinkWatcher watcher) {

        public Platform {
            Objects.requireNonNull(dataFolder, "dataFolder");
            Objects.requireNonNull(pluginVersion, "pluginVersion");
            Objects.requireNonNull(software, "software");
            Objects.requireNonNull(softwareVersion, "softwareVersion");
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(console, "console");
            Objects.requireNonNull(presence, "presence");
            Objects.requireNonNull(lanes, "lanes");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(watcher, "watcher");
        }
    }

    private final ConnectSettings settings;
    private final Database database;
    private final SqlJournal journal;
    private final HttpClient http;
    private final StayConnected connection;
    private final SiteLoop loop;
    private final SiteSession session;
    private final List<Lanes.Cancel> timers;

    public SiteConnection(ConnectSettings settings, Supplier<ConnectSettings> current, Platform platform) {
        this.settings = Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(platform, "platform");
        loadTheJournalDriver();
        this.database = Database.builder()
                .sqlite(platform.dataFolder().resolve("journal.db"))
                .poolName("BlysisConnect-journal")
                .build();
        this.journal = SqlJournal.open(database, Clock.systemUTC());
        this.http = HttpSite.client(settings.connectTimeout(), settings.address());
        HttpSite site = new HttpSite(http, connectionOf(settings, platform));
        DeliverJobs delivery = new DeliverJobs(
                journal,
                platform.console(),
                platform.presence(),
                site,
                () -> current.get().blocklist(),
                settings.commandTimeout());
        HttpAccounts accounts = new HttpAccounts(site, site.instance());
        CatalogCache catalog = new CatalogCache(accounts, Clock.systemUTC());
        this.connection = new StayConnected(
                site,
                delivery,
                new ShelfKeeper(catalog, platform.watcher()),
                new Backoff(
                        settings.retryFirst(),
                        settings.retryLongest(),
                        () -> ThreadLocalRandom.current().nextDouble()),
                platform.software(),
                platform.softwareVersion(),
                platform.role());
        this.loop = new SiteLoop(connection, platform.lanes(), platform.status(), this::statusInterval);
        Boards boards = new Boards(accounts);
        this.session = new SiteSession(
                accounts,
                new Profiles(accounts, Clock.systemUTC(), PROFILE_FRESH),
                catalog,
                boards,
                new Once(Once.Sleeper.real()),
                connection::state);
        Lanes lanes = platform.lanes();
        this.timers = List.of(
                lanes.every(SWEEP_PERIOD, journal::sweep),
                lanes.every(SHELF_PERIOD, catalog::refresh),
                lanes.every(BOARD_PERIOD, boards::refresh));
        loop.start();
    }

    /**
     * Registers the SQLite driver this jar carries. Paper finds it through the service loader; Velocity gives a
     * plugin its own class loader, which the JDBC driver manager never searches, so the journal failed with "no
     * suitable driver" on the first proxy it ran on. Loading the class registers it, and the pool asks from a
     * class of this jar, which the manager accepts.
     */
    private static void loadTheJournalDriver() {
        try {
            Class.forName("org.sqlite.JDBC", true, SiteConnection.class.getClassLoader());
        } catch (ClassNotFoundException missing) {
            throw new IllegalStateException("the SQLite driver is not in this jar", missing);
        }
    }

    private static HttpSite.Connection connectionOf(ConnectSettings settings, Platform platform) {
        return new HttpSite.Connection(
                settings.address(),
                settings.token(),
                UUID.randomUUID().toString(),
                CLIENT,
                platform.pluginVersion(),
                "minecraft",
                settings.requestTimeout());
    }

    private Duration statusInterval() {
        return connection
                .hello()
                .map(hello -> hello.settings().statusInterval())
                .orElse(DEFAULT_STATUS_INTERVAL);
    }

    /** The settings this connection was built from. */
    public ConnectSettings settings() {
        return settings;
    }

    /** What a command or a menu talks to the site through. */
    public SiteSession session() {
        return session;
    }

    public LinkState state() {
        return connection.state();
    }

    /** How many outcomes wait for the site. */
    public int unreported() {
        return journal.unreportedCount();
    }

    /** Pushes who is online soon. */
    public void statusSoon() {
        loop.statusSoon();
    }

    @Override
    public void close() {
        loop.stop();
        timers.forEach(Lanes.Cancel::cancel);
        http.shutdownNow();
        database.close();
    }
}
