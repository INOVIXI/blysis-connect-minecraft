package com.uxplima.blysis.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.blysis.application.port.Accounts;
import com.uxplima.blysis.domain.PlayerRef;
import com.uxplima.blysis.domain.account.Profile;

/**
 * Each online player's account as the site last described it: linked or not, the balance, the gift switch.
 *
 * <p>A menu reads it without a call. It is asked again when it is older than the menu may show, and an
 * answer to a purchase or a transfer brings the balance with it. While the site is away a player sees the
 * last balance it gave, marked as such, and no purchase is taken.
 */
public final class Profiles {

    private final Accounts accounts;
    private final Clock clock;
    private final Duration fresh;
    private final Map<String, Known> known = new ConcurrentHashMap<>();

    public Profiles(Accounts accounts, Clock clock, Duration fresh) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.fresh = Objects.requireNonNull(fresh, "fresh");
    }

    /** The profile, asked again when it is old. Call it off the server threads. */
    public Answer<Profile> of(PlayerRef player) {
        Objects.requireNonNull(player, "player");
        Known held = known.get(keyOf(player));
        if (held != null && held.at().plus(fresh).isAfter(clock.instant())) {
            return new Answer.Given<>(held.profile());
        }
        return ask(player);
    }

    /** The profile, asked now whatever is held. */
    public Answer<Profile> ask(PlayerRef player) {
        Answer<Profile> answer = Answer.of(() -> accounts.profile(player));
        if (answer instanceof Answer.Given<Profile> given) {
            known.put(keyOf(player), new Known(given.value(), clock.instant()));
        }
        return answer;
    }

    /** What was last known, however old, for a screen drawn while the site is away. */
    public Optional<Profile> lastKnown(PlayerRef player) {
        return Optional.ofNullable(known.get(keyOf(player))).map(Known::profile);
    }

    /** A balance the site just gave in an answer. */
    public void balance(PlayerRef player, BigDecimal balance) {
        known.computeIfPresent(
                keyOf(player),
                (key, held) -> new Known(
                        new Profile(
                                true,
                                held.profile().username(),
                                balance,
                                held.profile().receiveGifts()),
                        clock.instant()));
    }

    /** The gift switch the site just confirmed. */
    public void receiveGifts(PlayerRef player, boolean receive) {
        known.computeIfPresent(
                keyOf(player),
                (key, held) -> new Known(
                        new Profile(
                                true, held.profile().username(), held.profile().balance(), receive),
                        clock.instant()));
    }

    /** Forgets a player, so the next read asks: they left, or linked, or unlinked on the site. */
    public void forget(PlayerRef player) {
        known.remove(keyOf(player));
    }

    private static String keyOf(PlayerRef player) {
        String id = player.id();
        return id == null ? "name:" + player.name() : id;
    }

    private record Known(Profile profile, Instant at) {}
}
