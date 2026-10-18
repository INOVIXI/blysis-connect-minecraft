package com.uxplima.blysis.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.uxplima.blysis.application.port.LinkWatcher;
import com.uxplima.blysis.application.port.SiteUnavailable;
import com.uxplima.blysis.domain.LinkState;
import com.uxplima.blysis.domain.account.Catalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The shelves are asked again the moment the site answers after an outage.
 *
 * <p>A refresh that fails while the site is away marks the kept shelves stale, and a stale store takes no
 * purchase. Waiting for the next timed refresh left the store closed for up to ten minutes after the site
 * was back, with every player told it did not answer.
 */
final class ShelfKeeperTest {

    private final FakeAccounts site = new FakeAccounts();
    private final CatalogCache cache = new CatalogCache(site, Clock.systemUTC());
    private final List<LinkState> passedOn = new ArrayList<>();
    private final LinkWatcher next = (state, detail) -> passedOn.add(state);
    private final ShelfKeeper keeper = new ShelfKeeper(cache, next);

    @Test
    @DisplayName("stale shelves are asked again as soon as the site answers")
    void staleShelvesRefreshOnReconnect() {
        Catalog shelves = new Catalog("c-1", List.of());
        site.catalogs.add(() -> Optional.of(shelves));
        site.catalogs.add(() -> {
            throw new SiteUnavailable("down");
        });
        site.catalogs.add(Optional::empty);
        cache.refresh();
        cache.refresh();
        assertThat(cache.view().stale()).isTrue();

        keeper.stateChanged(LinkState.UNREACHABLE, "down");
        keeper.stateChanged(LinkState.CONNECTED, "");

        assertThat(cache.view().stale()).isFalse();
        assertThat(site.catalogVersions).containsExactly(null, "c-1", "c-1");
        assertThat(passedOn).containsExactly(LinkState.UNREACHABLE, LinkState.CONNECTED);
    }

    @Test
    @DisplayName("fresh shelves are not asked again on every reconnect")
    void freshShelvesAreLeftAlone() {
        site.catalogs.add(() -> Optional.of(new Catalog("c-1", List.of())));
        cache.refresh();

        keeper.stateChanged(LinkState.CONNECTED, "");

        assertThat(site.catalogVersions).containsExactly((String) null);
    }
}
