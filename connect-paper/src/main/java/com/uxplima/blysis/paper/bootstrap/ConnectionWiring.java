package com.uxplima.blysis.paper.bootstrap;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import org.bukkit.Server;

import com.uxplima.blysis.adapter.outbound.ConsoleWatcher;
import com.uxplima.blysis.adapter.outbound.SiteConnection;
import com.uxplima.blysis.application.ConnectSettings;
import com.uxplima.blysis.application.SiteSession;
import com.uxplima.blysis.application.port.LinkWatcher;
import com.uxplima.blysis.domain.LinkState;
import com.uxplima.blysis.domain.PlayerRef;
import com.uxplima.blysis.domain.StatusPush;
import com.uxplima.blysis.paper.adapter.outbound.OnlinePlayers;
import com.uxplima.blysis.paper.adapter.outbound.PaperConsole;
import com.uxplima.blysis.paper.adapter.outbound.PaperLanes;
import com.uxplima.uxmlib.scheduler.Scheduler;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The connection's lifecycle on Paper and Folia: built from one snapshot of the settings, and built again when
 * a reload changed the address or the token. Any other reload leaves the running one alone.
 */
final class ConnectionWiring {

    private final Server server;
    private final Scheduler scheduler;
    private final Logger logger;
    private final Path dataFolder;
    private final String version;
    private final OnlinePlayers online;
    private final Supplier<ConnectSettings> current;

    // What players and staff are told: the announcements and the one line about a site that went away.
    private final LinkWatcher players;

    private volatile @Nullable SiteConnection running;

    ConnectionWiring(
            Server server,
            Scheduler scheduler,
            Logger logger,
            Path dataFolder,
            String version,
            OnlinePlayers online,
            Supplier<ConnectSettings> current,
            LinkWatcher players) {
        this.server = Objects.requireNonNull(server, "server");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.dataFolder = Objects.requireNonNull(dataFolder, "dataFolder");
        this.version = Objects.requireNonNull(version, "version");
        this.online = Objects.requireNonNull(online, "online");
        this.current = Objects.requireNonNull(current, "current");
        this.players = Objects.requireNonNull(players, "players");
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
        running = new SiteConnection(connect, current, platform());
    }

    private SiteConnection.Platform platform() {
        return new SiteConnection.Platform(
                dataFolder,
                version,
                server.getName(),
                server.getVersion(),
                "server",
                new PaperConsole(server, scheduler),
                online,
                new PaperLanes(scheduler),
                this::status,
                new ConsoleWatcher(new SlfConsole(logger), players));
    }

    /** Builds the connection again when the reload changed where or who this server is. */
    synchronized void reloaded() {
        SiteConnection now = running;
        ConnectSettings connect = current.get();
        if (now != null
                && now.settings().address().equals(connect.address())
                && now.settings().token().equals(connect.token())) {
            return;
        }
        stop();
        start();
    }

    synchronized void stop() {
        SiteConnection now = running;
        running = null;
        if (now != null) {
            now.close();
        }
    }

    /** Pushes who is online soon, when connected. */
    void statusSoon() {
        SiteConnection now = running;
        if (now != null) {
            now.statusSoon();
        }
    }

    /** What a command or a menu talks to the site through. Empty when the server is not set up. */
    Optional<SiteSession> session() {
        SiteConnection now = running;
        return now == null ? Optional.empty() : Optional.of(now.session());
    }

    /** Where the connection stands, for an operator. Empty when the server is not set up. */
    Optional<LinkState> state() {
        SiteConnection now = running;
        return now == null ? Optional.empty() : Optional.of(now.state());
    }

    /** How many outcomes wait for the site, for an operator. Empty when the server is not set up. */
    Optional<Integer> unreported() {
        SiteConnection now = running;
        return now == null ? Optional.empty() : Optional.of(now.unreported());
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

    /** The server's console, at the three levels the connection speaks in. */
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
