package com.uxplima.blysis.application;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.blysis.application.port.LinkWatcher;
import com.uxplima.blysis.application.port.Site;
import com.uxplima.blysis.application.port.SiteRefused;
import com.uxplima.blysis.application.port.SiteUnavailable;
import com.uxplima.blysis.domain.Backoff;
import com.uxplima.blysis.domain.Hello;
import com.uxplima.blysis.domain.LinkState;
import com.uxplima.blysis.domain.Poll;
import com.uxplima.blysis.domain.StatusPush;
import org.jspecify.annotations.Nullable;

/**
 * Keeps this server's connection to the site: hello once, then one long-poll after another.
 *
 * <p>Each {@link #step()} makes one call and answers how long to wait before the next, so the platform runs
 * it on its async scheduler and no thread of this plugin's own exists. A site that does not answer is called
 * again after a growing, jittered wait. A site that says stop is not called again until a reload.
 */
public final class StayConnected {

    private final Site site;
    private final DeliverJobs delivery;
    private final LinkWatcher watcher;
    private final Backoff backoff;
    private final String software;
    private final String softwareVersion;
    private final String role;

    private volatile LinkState state = LinkState.CONNECTING;
    private volatile @Nullable Hello hello;
    private volatile @Nullable String eventCursor;
    private volatile @Nullable String catalogVersion;

    public StayConnected(
            Site site,
            DeliverJobs delivery,
            LinkWatcher watcher,
            Backoff backoff,
            String software,
            String softwareVersion,
            String role) {
        this.site = Objects.requireNonNull(site, "site");
        this.delivery = Objects.requireNonNull(delivery, "delivery");
        this.watcher = Objects.requireNonNull(watcher, "watcher");
        this.backoff = Objects.requireNonNull(backoff, "backoff");
        this.software = Objects.requireNonNull(software, "software");
        this.softwareVersion = Objects.requireNonNull(softwareVersion, "softwareVersion");
        this.role = Objects.requireNonNull(role, "role");
    }

    /**
     * Makes one call. Answers the wait before the next step, or empty when the connection must stop.
     */
    public Optional<Duration> step() {
        if (state == LinkState.STOPPED) {
            return Optional.empty();
        }
        try {
            if (hello == null) {
                greet();
            } else {
                pollOnce();
            }
            backoff.reset();
            move(LinkState.CONNECTED, "");
            return Optional.of(Duration.ZERO);
        } catch (SiteRefused refused) {
            if (refused.stopsTheConnection()) {
                move(LinkState.STOPPED, refused.code() + ": " + refused.getMessage());
                return Optional.empty();
            }
            return away(refused.code());
        } catch (SiteUnavailable unavailable) {
            return away(String.valueOf(unavailable.getMessage()));
        }
    }

    /** Sends who is online, when connected. Quietly skipped otherwise: the next push carries the truth. */
    public void pushStatus(StatusPush push) {
        Objects.requireNonNull(push, "push");
        if (state != LinkState.CONNECTED) {
            return;
        }
        try {
            site.status(push);
        } catch (SiteRefused | SiteUnavailable missed) {
            // The poll sees the same trouble and reports it once; a status is sent again next interval.
        }
    }

    /** Where the connection stands now. */
    public LinkState state() {
        return state;
    }

    /** The site's answer to hello, once it has answered. */
    public Optional<Hello> hello() {
        return Optional.ofNullable(hello);
    }

    private void greet() {
        Hello greeted = site.hello(software, softwareVersion, role);
        hello = greeted;
        catalogVersion = greeted.settings().catalogVersion();
        watcher.greeted(greeted);
        delivery.recover();
    }

    private void pollOnce() {
        Poll poll = site.poll(eventCursor);
        delivery.deliver(poll.jobs());
        eventCursor = poll.eventCursor();
        if (!poll.events().isEmpty()) {
            watcher.events(poll.events());
        }
        if (!poll.catalogVersion().equals(catalogVersion)) {
            catalogVersion = poll.catalogVersion();
            watcher.catalogChanged(poll.catalogVersion());
        }
    }

    private Optional<Duration> away(String detail) {
        move(LinkState.UNREACHABLE, detail);
        return Optional.of(backoff.next());
    }

    private void move(LinkState next, String detail) {
        if (state != next) {
            state = next;
            watcher.stateChanged(next, detail);
        }
    }
}
