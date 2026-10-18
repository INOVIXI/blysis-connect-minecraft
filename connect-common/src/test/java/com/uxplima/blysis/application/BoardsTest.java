package com.uxplima.blysis.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import com.uxplima.blysis.application.port.SiteUnavailable;
import com.uxplima.blysis.domain.account.Goal;
import com.uxplima.blysis.domain.account.TopEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class BoardsTest {

    private final FakeAccounts site = new FakeAccounts();
    private final Boards boards = new Boards(site);

    @Test
    @DisplayName("a board is read only once something asked for it, and then on every refresh")
    void readsWhatWasAsked() {
        boards.refresh();
        assertThat(site.topsAsked).isEmpty();

        Boards.Board latest = Boards.boardOf("latest", "month").orElseThrow();
        assertThat(boards.top(latest)).isEmpty();
        site.tops.add(() -> List.of(new TopEntry(1, "alex", new BigDecimal("50"))));
        boards.refresh();

        assertThat(site.topsAsked).containsExactly("latest/month/10");
        assertThat(boards.top(latest)).extracting(TopEntry::name).containsExactly("alex");
    }

    @Test
    @DisplayName("a site that is away keeps the last ranking and the last goal in place")
    void keepsTheLastAnswer() {
        Boards.Board rich = Boards.boardOf("balance", "all").orElseThrow();
        boards.top(rich);
        boards.goal();
        site.tops.add(() -> List.of(new TopEntry(1, "steve", BigDecimal.TEN)));
        site.goals.add(() -> new Goal(true, "Lights", BigDecimal.ONE, BigDecimal.TEN, 10));
        boards.refresh();
        site.tops.add(() -> {
            throw new SiteUnavailable("down");
        });
        site.goals.add(() -> {
            throw new SiteUnavailable("down");
        });
        boards.refresh();

        assertThat(boards.top(rich)).extracting(TopEntry::name).containsExactly("steve");
        assertThat(boards.goal().title()).isEqualTo("Lights");
    }

    @Test
    @DisplayName("a kind or a period the site does not know is no board at all")
    void refusesUnknownPairs() {
        assertThat(Boards.boardOf("richest", "all")).isEmpty();
        assertThat(Boards.boardOf("balance", "week")).isEmpty();
    }
}
