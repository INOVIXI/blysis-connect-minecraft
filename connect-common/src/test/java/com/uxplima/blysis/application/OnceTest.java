package com.uxplima.blysis.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.uxplima.blysis.application.port.SiteRefused;
import com.uxplima.blysis.application.port.SiteUnavailable;
import com.uxplima.blysis.domain.PlayerRef;
import com.uxplima.blysis.domain.account.Purchase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class OnceTest {

    private static final PlayerRef NOTCH = new PlayerRef("u", "Notch");
    private static final Purchase BOUGHT = new Purchase("ORD-1", BigDecimal.TEN, BigDecimal.ONE, false);

    private final FakeAccounts site = new FakeAccounts();
    private final List<Duration> waited = new ArrayList<>();
    private final Once once = new Once(duration -> waited.add(duration));

    private Answer<Purchase> buy() {
        return once.run(key -> site.purchase(NOTCH, "p1", 1, Map.of(), null, key));
    }

    @Test
    @DisplayName("a call whose answer was lost is asked again with the same key, never a new one")
    void retriesWithTheSameKey() {
        site.purchases.add(() -> {
            throw new SiteUnavailable("timed out");
        });
        site.purchases.add(() -> BOUGHT);

        assertThat(buy()).isEqualTo(new Answer.Given<>(BOUGHT));
        assertThat(site.purchaseKeys).hasSize(2).allMatch(key -> key.equals(site.purchaseKeys.getFirst()));
        assertThat(waited).containsExactly(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("a site still busy with the first attempt is asked again after a wait")
    void waitsOutAnAttemptInFlight() {
        site.purchases.add(() -> {
            throw new SiteRefused(409, "in_progress", "busy");
        });
        site.purchases.add(() -> BOUGHT);

        assertThat(buy()).isInstanceOf(Answer.Given.class);
    }

    @Test
    @DisplayName("a refusal is the answer and is not asked again")
    void aRefusalIsFinal() {
        site.purchases.add(() -> {
            throw new SiteRefused(409, "insufficient_balance", "short");
        });

        assertThat(buy()).isEqualTo(new Answer.Refused<Purchase>("insufficient_balance", null));
        assertThat(site.purchaseKeys).hasSize(1);
    }

    @Test
    @DisplayName("three attempts at most, then the player is told the site is away")
    void givesUpAfterThreeAttempts() {
        for (int i = 0; i < 3; i++) {
            site.purchases.add(() -> {
                throw new SiteUnavailable("down");
            });
        }

        assertThat(buy()).isInstanceOf(Answer.Away.class);
        assertThat(site.purchaseKeys).hasSize(3);
    }

    @Test
    @DisplayName("each action has a key of its own")
    void everyActionHasItsOwnKey() {
        site.purchases.add(() -> BOUGHT);
        site.purchases.add(() -> BOUGHT);

        buy();
        buy();

        assertThat(site.purchaseKeys.get(0)).isNotEqualTo(site.purchaseKeys.get(1));
    }
}
