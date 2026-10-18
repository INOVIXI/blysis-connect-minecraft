package com.uxplima.blysis.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.blysis.application.port.Accounts;
import com.uxplima.blysis.application.port.SiteRefused;
import com.uxplima.blysis.application.port.SiteUnavailable;
import com.uxplima.blysis.domain.account.Catalog;
import org.jspecify.annotations.Nullable;

/**
 * The catalogue, kept here so a menu opens without a call and still opens while the site is away.
 *
 * <p>It is asked again when the site says it changed, and on a timer as a backstop. A refresh that fails
 * keeps what was there and marks it stale, so a player sees the shelves with a note rather than an empty
 * window.
 */
public final class CatalogCache {

    private final Accounts accounts;
    private final Clock clock;
    private volatile Catalog catalog = Catalog.EMPTY;
    private volatile @Nullable Instant fetchedAt;
    private volatile boolean stale = true;

    public CatalogCache(Accounts accounts, Clock clock) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** What a menu draws: the shelves, and whether they may be out of date. */
    public View view() {
        return new View(catalog, stale, Optional.ofNullable(fetchedAt));
    }

    /** Asks the site, sending the version held. Call it off the server threads. */
    public void refresh() {
        try {
            Optional<Catalog> changed = accounts.catalog(catalog.version().isEmpty() ? null : catalog.version());
            changed.ifPresent(fresh -> catalog = fresh);
            fetchedAt = clock.instant();
            stale = false;
        } catch (SiteUnavailable | SiteRefused away) {
            stale = true;
        }
    }

    /** The shelves and how fresh they are. */
    public record View(Catalog catalog, boolean stale, Optional<Instant> fetchedAt) {}
}
