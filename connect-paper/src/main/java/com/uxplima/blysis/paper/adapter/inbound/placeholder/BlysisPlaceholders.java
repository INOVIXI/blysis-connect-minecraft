package com.uxplima.blysis.paper.adapter.inbound.placeholder;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

import org.bukkit.OfflinePlayer;

import com.uxplima.blysis.application.Boards;
import com.uxplima.blysis.application.SiteSession;
import com.uxplima.blysis.domain.PlayerRef;
import com.uxplima.blysis.domain.account.Amounts;
import com.uxplima.blysis.domain.account.Goal;
import com.uxplima.blysis.domain.account.Profile;
import com.uxplima.blysis.domain.account.TopEntry;
import com.uxplima.uxmlib.hook.placeholder.PlaceholderProvider;
import com.uxplima.uxmlib.scheduler.Scheduler;
import org.jspecify.annotations.Nullable;

/**
 * What this plugin answers when another plugin asks, under {@code %blysis_...%}.
 *
 * <p>Every answer is read from memory: PlaceholderAPI asks on the main thread on every scoreboard tick. A
 * player's balance is the one last read, and a player nobody read yet is read once off the server threads, so
 * the scoreboard shows it from the next tick on.
 *
 * <p>The keys:
 *
 * <ul>
 *   <li>{@code balance}, {@code linked}, {@code receiving_gifts}: the player's account
 *   <li>{@code connection}: connected, connecting, unreachable, stopped or offline
 *   <li>{@code top_<kind>_<period>_<place>_name} and {@code ..._amount}: kind is balance, topups or latest;
 *       period is day, month, year or all; place is one to ten
 *   <li>{@code goal_title}, {@code goal_raised}, {@code goal_goal}, {@code goal_percent}, {@code goal_active}
 * </ul>
 *
 * <p>A key this plugin does not own answers null, so the operator reads their own token back and finds the
 * typo.
 */
public final class BlysisPlaceholders implements PlaceholderProvider {

    /** The identifier this plugin registers under. */
    public static final String IDENTIFIER = "blysis";

    private static final java.util.regex.Pattern UNDERSCORE = java.util.regex.Pattern.compile("_");

    /** How long a player read once waits before it is read again, when no answer was kept. */
    private static final Duration RETRY = Duration.ofSeconds(30);

    private final Supplier<Optional<SiteSession>> session;
    private final Scheduler scheduler;
    private final Map<UUID, Long> asked = new ConcurrentHashMap<>();

    public BlysisPlaceholders(Supplier<Optional<SiteSession>> session, Scheduler scheduler) {
        this.session = Objects.requireNonNull(session, "session");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public @Nullable String onRequest(@Nullable OfflinePlayer player, String params) {
        Objects.requireNonNull(params, "params");
        String key = params.toLowerCase(Locale.ROOT);
        if (key.equals("connection")) {
            return session.get()
                    .map(site -> site.state().get().name().toLowerCase(Locale.ROOT))
                    .orElse("offline");
        }
        if (key.startsWith("top_")) {
            return ranked(key.substring("top_".length()));
        }
        if (key.startsWith("goal_")) {
            return goal(key.substring("goal_".length()));
        }
        return player == null ? null : account(player, key);
    }

    private @Nullable String account(OfflinePlayer player, String key) {
        Function<Profile, String> read =
                switch (key) {
                    case "balance" -> profile -> Amounts.shown(profile.balance());
                    case "linked" -> profile -> Boolean.toString(profile.linked());
                    case "receiving_gifts" -> profile -> Boolean.toString(profile.receiveGifts());
                    default -> null;
                };
        if (read == null) {
            return null;
        }
        Optional<SiteSession> open = session.get();
        if (open.isEmpty()) {
            return "";
        }
        PlayerRef ref = new PlayerRef(player.getUniqueId().toString(), Objects.toString(player.getName(), ""));
        Optional<Profile> known = open.get().profiles().lastKnown(ref);
        if (known.isEmpty()) {
            warm(open.get(), ref, player.getUniqueId());
            return "";
        }
        asked.remove(player.getUniqueId());
        return read.apply(known.get());
    }

    /**
     * Reads a player nobody read yet, once. A scoreboard asks every tick, and while the site is away no answer
     * is kept, so without the gap every tick of every player would be a call to the site.
     */
    private void warm(SiteSession site, PlayerRef ref, UUID player) {
        long now = System.nanoTime();
        boolean[] due = {false};
        asked.compute(player, (id, last) -> {
            if (last != null && now - last < RETRY.toNanos()) {
                return last;
            }
            due[0] = true;
            return now;
        });
        if (due[0]) {
            scheduler.async(() -> site.profiles().of(ref));
        }
    }

    /** {@code <kind>_<period>_<place>_<name|amount>}. */
    private @Nullable String ranked(String rest) {
        String[] parts = UNDERSCORE.split(rest, -1);
        if (parts.length != 4) {
            return null;
        }
        Optional<Boards.Board> board = Boards.boardOf(parts[0], parts[1]);
        int place;
        try {
            place = Integer.parseInt(parts[2]);
        } catch (NumberFormatException notAPlace) {
            return null;
        }
        if (board.isEmpty()
                || place < 1
                || place > Boards.PLACES
                || !List.of("name", "amount").contains(parts[3])) {
            return null;
        }
        List<TopEntry> entries =
                session.get().map(site -> site.boards().top(board.get())).orElse(List.of());
        if (place > entries.size()) {
            return "";
        }
        TopEntry entry = entries.get(place - 1);
        return parts[3].equals("name") ? Objects.toString(entry.name(), "") : Amounts.shown(entry.amount());
    }

    private @Nullable String goal(String field) {
        Goal goal = session.get().map(site -> site.boards().goal()).orElse(Goal.NONE);
        return switch (field) {
            case "title" -> goal.title();
            case "raised" -> Amounts.shown(goal.raised());
            case "goal" -> Amounts.shown(goal.goal());
            case "percent" -> Integer.toString(goal.percent());
            case "active" -> Boolean.toString(goal.active());
            default -> null;
        };
    }
}
