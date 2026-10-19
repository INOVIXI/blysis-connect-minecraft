package com.uxplima.blysis.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import com.uxplima.blysis.domain.LinkState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A reload builds the connection again when it points somewhere new, and when the site had stopped it.
 *
 * <p>Found live: the site refused the token, the console said the server "stops calling until /blysis
 * reload", the token was put right on the site, and the reload did nothing. A reload kept any connection whose
 * address and token were unchanged, a stopped one included, so the instruction the console gave could not work.
 */
final class AReloadReconnectsTest {

    private static final String TOKEN = "gc_" + "a".repeat(43);

    private static ConnectSettings at(String address, String token) {
        Duration second = Duration.ofSeconds(1);
        return new ConnectSettings(address, token, List.of(), List.of(), second, second, second, second, second);
    }

    private final ConnectSettings running = at("https://site.example", TOKEN);

    @Test
    @DisplayName("a reload that changed nothing leaves a working connection alone")
    void aWorkingConnectionStays() {
        for (LinkState state : List.of(LinkState.CONNECTING, LinkState.CONNECTED, LinkState.UNREACHABLE)) {
            assertThat(running.reconnectsFor(at("https://site.example", TOKEN), state))
                    .as(state.name())
                    .isFalse();
        }
    }

    @Test
    @DisplayName("a reload connects again a connection the site stopped, with the same address and token")
    void aStoppedConnectionStartsAgain() {
        assertThat(running.reconnectsFor(at("https://site.example", TOKEN), LinkState.STOPPED))
                .isTrue();
    }

    @Test
    @DisplayName("a reload with a new address or a new token connects again")
    void aNewSiteOrTokenConnectsAgain() {
        assertThat(running.reconnectsFor(at("https://other.example", TOKEN), LinkState.CONNECTED))
                .isTrue();
        assertThat(running.reconnectsFor(at("https://site.example", "gc_" + "b".repeat(43)), LinkState.CONNECTED))
                .isTrue();
    }
}
