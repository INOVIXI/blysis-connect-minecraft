package com.uxplima.blysis.adapter.outbound;

import java.util.List;
import java.util.Objects;

import com.uxplima.blysis.application.port.LinkWatcher;
import com.uxplima.blysis.domain.Hello;
import com.uxplima.blysis.domain.LinkState;
import com.uxplima.blysis.domain.SiteEvent;

/**
 * Says each change of the connection once, in the console, and passes everything on to what players and staff
 * are told. The console of either platform is reached through {@link Console}, so the words are written once.
 */
public final class ConsoleWatcher implements LinkWatcher {

    /** The console's three levels. */
    public interface Console {
        void info(String line);

        void warn(String line);

        void error(String line);
    }

    private final Console console;
    private final LinkWatcher next;

    public ConsoleWatcher(Console console, LinkWatcher next) {
        this.console = Objects.requireNonNull(console, "console");
        this.next = Objects.requireNonNull(next, "next");
    }

    @Override
    public void stateChanged(LinkState state, String detail) {
        next.stateChanged(state, detail);
        switch (state) {
            case CONNECTING -> console.info("Connecting to the site.");
            case CONNECTED -> console.info("Connected to the site.");
            case UNREACHABLE ->
                console.warn("The site does not answer (" + detail + "). Deliveries wait there, and anything that"
                        + " moves credits is refused until it answers again.");
            case STOPPED ->
                console.error("The site refused this server and it stops calling until /blysis reload: " + detail);
        }
    }

    @Override
    public void greeted(Hello hello) {
        console.info("This is " + hello.serverName() + " on " + hello.siteName() + " (" + hello.siteUrl() + ").");
        if (hello.takeoverOf() != null) {
            console.warn("Another process used this token in the last minute and a half. After a restart that"
                    + " was this server; if not, two servers share one token and only the newest is served.");
        }
        next.greeted(hello);
    }

    @Override
    public void events(List<SiteEvent> events) {
        next.events(events);
    }

    @Override
    public void catalogChanged(String version) {
        next.catalogChanged(version);
    }
}
