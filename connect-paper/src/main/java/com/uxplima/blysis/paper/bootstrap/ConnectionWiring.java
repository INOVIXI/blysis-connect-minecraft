package com.uxplima.blysis.paper.bootstrap;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import org.bukkit.Server;

import com.uxplima.blysis.adapter.outbound.HttpSite;
import com.uxplima.blysis.adapter.outbound.SqlJournal;
import com.uxplima.blysis.application.ConnectSettings;
import com.uxplima.blysis.application.DeliverJobs;
import com.uxplima.blysis.application.StayConnected;
import com.uxplima.blysis.application.port.LinkWatcher;
import com.uxplima.blysis.domain.Backoff;
import com.uxplima.blysis.domain.Hello;
import com.uxplima.blysis.domain.LinkState;
import com.uxplima.blysis.domain.PlayerRef;
import com.uxplima.blysis.domain.StatusPush;
import com.uxplima.blysis.paper.adapter.outbound.OnlinePlayers;
import com.uxplima.blysis.paper.adapter.outbound.PaperConsole;
import com.uxplima.blysis.paper.adapter.outbound.SiteLink;
import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.scheduler.TaskHandle;
import com.uxplima.uxmlib.storage.sql.Database;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The connection to the site, built from one snapshot of the settings and torn down whole. A reload that
 * changed the address or the token builds a new one; any other reload leaves the running one alone.
 */
final class ConnectionWiring {

    /** The client name the site shows beside this server. */
    static final String CLIENT = "blysis-connect-minecraft";

    private static final Duration DEFAULT_STATUS_INTERVAL = Duration.ofSeconds(30);
    private static final Duration SWEEP_PERIOD = Duration.ofHours(6);

    private final Server server;
    private final Scheduler scheduler;
    private final Logger logger;
    private final Path dataFolder;
    private final String version;
    private final OnlinePlayers online;
    private final Supplier<ConnectSettings> current;

    private @Nullable Running running;

    ConnectionWiring(
            Server server,
            Scheduler scheduler,
            Logger logger,
            Path dataFolder,
            String version,
            OnlinePlayers online,
            Supplier<ConnectSettings> current) {
        this.server = Objects.requireNonNull(server, "server");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.dataFolder = Objects.requireNonNull(dataFolder, "dataFolder");
        this.version = Objects.requireNonNull(version, "version");
        this.online = Objects.requireNonNull(online, "online");
        this.current = Objects.requireNonNull(current, "current");
    }

    /** Connects with the settings now in place, or says once why it does not. */
    synchronized void start() {
        ConnectSettings connect = current.get();
        if (!connect.configured()) {
            logger.warn("site.address and site.token in config.conf are empty, so this server connects to no site."
                    + " Make a token on the site under Game Connect, write both, and run /blysis reload.");
            return;
        }
        connect.insecureAddress()
                .ifPresent(address -> logger.warn(
                        "site.address is plain http to another machine ({}). The token crosses the network in clear:"
                                + " use https.",
                        address));
        running = new Running(connect);
    }

    /** Builds the connection again when the reload changed where or who this server is. */
    synchronized void reloaded() {
        Running now = running;
        ConnectSettings connect = current.get();
        if (now != null
                && now.settings.address().equals(connect.address())
                && now.settings.token().equals(connect.token())) {
            return;
        }
        stop();
        start();
    }

    synchronized void stop() {
        Running now = running;
        running = null;
        if (now != null) {
            now.close();
        }
    }

    /** Pushes who is online soon, when connected. */
    void statusSoon() {
        Running now = running;
        if (now != null) {
            now.link.statusSoon();
        }
    }

    /** Where the connection stands, for an operator. Empty when the server is not set up. */
    Optional<LinkState> state() {
        Running now = running;
        return now == null ? Optional.empty() : Optional.of(now.connection.state());
    }

    /** How many outcomes wait for the site, for an operator. Empty when the server is not set up. */
    Optional<Integer> unreported() {
        Running now = running;
        return now == null ? Optional.empty() : Optional.of(now.journal.unreportedCount());
    }

    private StatusPush status() {
        List<PlayerRef> players = online.all();
        double tps = 20;
        double mspt = 0;
        try {
            tps = Math.min(20, server.getTPS()[0]);
            mspt = server.getAverageTickTime();
        } catch (UnsupportedOperationException notOnThisServer) {
            // Folia keeps a tick time per region and no single figure for the whole server.
        }
        return new StatusPush(players.size(), server.getMaxPlayers(), players, server.getMinecraftVersion(), tps, mspt);
    }

    /** One connection and everything it opened. */
    private final class Running {

        private final ConnectSettings settings;
        private final Database database;
        private final SqlJournal journal;
        private final HttpClient http;
        private final StayConnected connection;
        private final SiteLink link;
        private final TaskHandle sweep;

        Running(ConnectSettings settings) {
            this.settings = settings;
            this.database = Database.builder()
                    .sqlite(dataFolder.resolve("journal.db"))
                    .poolName("BlysisConnect-journal")
                    .build();
            this.journal = SqlJournal.open(database, Clock.systemUTC());
            this.http = HttpSite.client(settings.connectTimeout(), settings.address());
            HttpSite site = new HttpSite(
                    http,
                    new HttpSite.Connection(
                            settings.address(),
                            settings.token(),
                            UUID.randomUUID().toString(),
                            CLIENT,
                            version,
                            "minecraft",
                            settings.requestTimeout()));
            DeliverJobs delivery = new DeliverJobs(
                    journal,
                    new PaperConsole(server, scheduler),
                    online,
                    site,
                    () -> current.get().blocklist(),
                    settings.commandTimeout());
            this.connection = new StayConnected(
                    site,
                    delivery,
                    new ConsoleWatcher(),
                    new Backoff(
                            settings.retryFirst(),
                            settings.retryLongest(),
                            () -> ThreadLocalRandom.current().nextDouble()),
                    server.getName(),
                    server.getVersion(),
                    "server");
            this.link = new SiteLink(
                    connection,
                    scheduler,
                    ConnectionWiring.this::status,
                    () -> connection
                            .hello()
                            .map(hello -> hello.settings().statusInterval())
                            .orElse(DEFAULT_STATUS_INTERVAL));
            this.sweep = scheduler.asyncTimer(SWEEP_PERIOD, SWEEP_PERIOD, handle -> journal.sweep());
            link.start();
        }

        void close() {
            link.stop();
            sweep.cancel();
            http.shutdownNow();
            database.close();
        }
    }

    /** Says each change of the connection once, in the console. */
    private final class ConsoleWatcher implements LinkWatcher {

        @Override
        public void stateChanged(LinkState state, String detail) {
            switch (state) {
                case CONNECTING -> logger.info("Connecting to the site.");
                case CONNECTED -> logger.info("Connected to the site.");
                case UNREACHABLE ->
                    logger.warn(
                            "The site does not answer ({}). Deliveries wait there, and purchases are refused until it"
                                    + " answers again.",
                            detail);
                case STOPPED ->
                    logger.error("The site refused this server and it stops calling until /blysis reload: {}", detail);
            }
        }

        @Override
        public void greeted(Hello hello) {
            logger.info("This is {} on {} ({}).", hello.serverName(), hello.siteName(), hello.siteUrl());
            String previous = hello.takeoverOf();
            if (previous != null) {
                logger.warn("Another process used this token in the last minute and a half. After a restart that"
                        + " was this server; if not, two servers share one token and only the newest is served.");
            }
        }
    }
}
