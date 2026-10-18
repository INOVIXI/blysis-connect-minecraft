package com.uxplima.blysis.application;

import java.util.List;
import java.util.Objects;

import com.uxplima.blysis.application.port.LinkWatcher;
import com.uxplima.blysis.domain.Hello;
import com.uxplima.blysis.domain.LinkState;
import com.uxplima.blysis.domain.SiteEvent;

/**
 * Keeps the shelves current when the site greets, says they changed, or answers again after an outage, and
 * passes everything on.
 *
 * <p>A stale store takes no purchase, so shelves marked stale while the site was away are asked again the
 * moment it answers, not at the next timed refresh.
 */
public record ShelfKeeper(CatalogCache catalog, LinkWatcher next) implements LinkWatcher {

    public ShelfKeeper {
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(next, "next");
    }

    @Override
    public void stateChanged(LinkState state, String detail) {
        if (state == LinkState.CONNECTED && catalog.view().stale()) {
            catalog.refresh();
        }
        next.stateChanged(state, detail);
    }

    @Override
    public void greeted(Hello hello) {
        catalog.refresh();
        next.greeted(hello);
    }

    @Override
    public void events(List<SiteEvent> events) {
        next.events(events);
    }

    @Override
    public void catalogChanged(String version) {
        catalog.refresh();
        next.catalogChanged(version);
    }
}
