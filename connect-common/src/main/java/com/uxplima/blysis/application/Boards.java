package com.uxplima.blysis.application;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.blysis.application.port.Accounts;
import com.uxplima.blysis.application.port.SiteRefused;
import com.uxplima.blysis.application.port.SiteUnavailable;
import com.uxplima.blysis.domain.account.Goal;
import com.uxplima.blysis.domain.account.TopEntry;

/**
 * The rankings and the community goal, kept for a scoreboard to read.
 *
 * <p>A placeholder is asked on the main thread on every scoreboard tick, so it reads only what is held here.
 * A ranking is fetched once something has asked for it, and from then on on every refresh, which runs off the
 * server threads. A site that is away keeps the last answer in place rather than blanking a scoreboard.
 */
public final class Boards {

    /** How many places a ranking holds. */
    public static final int PLACES = 10;

    private static final Set<String> KINDS = Set.of("balance", "topups", "latest");
    private static final Set<String> PERIODS = Set.of("day", "month", "year", "all");

    /** One ranking: what it ranks, over which time. */
    public record Board(String kind, String period) {

        public Board {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(period, "period");
        }
    }

    private final Accounts accounts;
    private final Map<Board, List<TopEntry>> held = new ConcurrentHashMap<>();
    private volatile Goal goal = Goal.NONE;
    private volatile boolean goalWanted;

    public Boards(Accounts accounts) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
    }

    /** The board for a kind and a period the site knows, or empty for a pair it does not. */
    public static Optional<Board> boardOf(String kind, String period) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(period, "period");
        return KINDS.contains(kind) && PERIODS.contains(period)
                ? Optional.of(new Board(kind, period))
                : Optional.empty();
    }

    /** What is held for a board, empty until the next refresh when it was never asked before. */
    public List<TopEntry> top(Board board) {
        Objects.requireNonNull(board, "board");
        return held.computeIfAbsent(board, wanted -> List.of());
    }

    /** The goal as last read; asking marks it wanted, so the next refresh reads it. */
    public Goal goal() {
        goalWanted = true;
        return goal;
    }

    /** Reads every board something asked for, and the goal. Call it off the server threads. */
    public void refresh() {
        for (Board board : List.copyOf(held.keySet())) {
            try {
                held.put(board, List.copyOf(accounts.top(board.kind(), board.period(), PLACES)));
            } catch (SiteUnavailable | SiteRefused away) {
                // The last answer stays: a scoreboard that blanks every time the site blinks is worse.
            }
        }
        if (goalWanted) {
            try {
                goal = accounts.goal();
            } catch (SiteUnavailable | SiteRefused away) {
                // As above.
            }
        }
    }
}
