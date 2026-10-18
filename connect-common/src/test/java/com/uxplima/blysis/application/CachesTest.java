package com.uxplima.blysis.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.uxplima.blysis.application.port.SiteUnavailable;
import com.uxplima.blysis.domain.PlayerRef;
import com.uxplima.blysis.domain.account.Catalog;
import com.uxplima.blysis.domain.account.Profile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class CachesTest {

    private static final PlayerRef NOTCH = new PlayerRef("u", "Notch");
    private static final Profile LINKED = new Profile(true, "notch", new BigDecimal("250"), true);

    private final FakeAccounts site = new FakeAccounts();

    @Test
    @DisplayName("the catalogue is asked with the version held, and kept, marked stale, when the site is away")
    void catalogueSurvivesAnOutage() {
        CatalogCache cache = new CatalogCache(site, Clock.systemUTC());
        Catalog shelves = new Catalog("c-1", List.of());
        site.catalogs.add(() -> Optional.of(shelves));
        site.catalogs.add(Optional::empty);
        site.catalogs.add(() -> {
            throw new SiteUnavailable("down");
        });

        cache.refresh();
        cache.refresh();
        cache.refresh();

        assertThat(site.catalogVersions).containsExactly(null, "c-1", "c-1");
        assertThat(cache.view().catalog()).isSameAs(shelves);
        assertThat(cache.view().stale()).isTrue();
    }

    @Test
    @DisplayName("a profile is read from memory while fresh, and asked again once old")
    void profileIsAskedWhenOld() {
        MutableClock clock = new MutableClock();
        Profiles profiles = new Profiles(site, clock, Duration.ofSeconds(30));
        site.profiles.add(() -> LINKED);
        site.profiles.add(() -> LINKED);

        profiles.of(NOTCH);
        profiles.of(NOTCH);
        clock.now = clock.now.plusSeconds(31);
        profiles.of(NOTCH);

        assertThat(site.profiles).isEmpty();
    }

    @Test
    @DisplayName("while the site is away the last balance it gave is still there to show")
    void lastBalanceSurvivesAnOutage() {
        Profiles profiles = new Profiles(site, Clock.systemUTC(), Duration.ZERO);
        site.profiles.add(() -> LINKED);
        site.profiles.add(() -> {
            throw new SiteUnavailable("down");
        });

        profiles.of(NOTCH);
        Answer<Profile> away = profiles.of(NOTCH);
        profiles.balance(NOTCH, new BigDecimal("150"));

        assertThat(away).isInstanceOf(Answer.Away.class);
        assertThat(profiles.lastKnown(NOTCH)).map(Profile::balance).contains(new BigDecimal("150"));
    }

    @Test
    @DisplayName("a balance asked while the site is away is the last one it gave, marked as remembered")
    void balanceShownFromMemoryWhileAway() {
        Profiles profiles = new Profiles(site, Clock.systemUTC(), Duration.ZERO);
        site.profiles.add(() -> LINKED);
        site.profiles.add(() -> {
            throw new SiteUnavailable("down");
        });
        site.profiles.add(() -> {
            throw new SiteUnavailable("down");
        });

        Answer<Profiles.Shown> live = profiles.shown(NOTCH);
        Answer<Profiles.Shown> remembered = profiles.shown(NOTCH);
        Answer<Profiles.Shown> nobody = profiles.shown(new PlayerRef("v", "Steve"));

        assertThat(live).isEqualTo(new Answer.Given<>(new Profiles.Shown(LINKED, false)));
        assertThat(remembered).isEqualTo(new Answer.Given<>(new Profiles.Shown(LINKED, true)));
        assertThat(nobody).isInstanceOf(Answer.Away.class);
    }

    private static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-18T12:00:00Z");

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
