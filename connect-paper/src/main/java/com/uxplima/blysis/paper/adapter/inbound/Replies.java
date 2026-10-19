package com.uxplima.blysis.paper.adapter.inbound;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.uxplima.blysis.application.Answer;
import com.uxplima.blysis.application.SiteSession;
import com.uxplima.blysis.domain.LinkState;
import com.uxplima.blysis.domain.PlayerRef;
import com.uxplima.blysis.paper.application.message.Keys;
import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.message.Messages;

/**
 * Asks the site off the server threads and tells the asker what came of it.
 *
 * <p>Every surface that talks to the site goes through here, so a refusal reads the same from a command and
 * from a menu, and a site that is away is said once in the same words everywhere.
 */
public final class Replies {

    private static final String REFUSED = "refused.";

    /** How long a hand-over may wait for the player's own thread before it is taken back. */
    private static final Duration HAND_WITHIN = Duration.ofSeconds(10);

    private final Messages messages;
    private final Scheduler scheduler;
    private final Supplier<Optional<SiteSession>> session;
    private final Map<String, Keys> refusals = new HashMap<>();

    public Replies(Messages messages, Scheduler scheduler, Supplier<Optional<SiteSession>> session) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.session = Objects.requireNonNull(session, "session");
        for (Keys key : Keys.values()) {
            if (key.path().startsWith(REFUSED)) {
                refusals.put(key.path().substring(REFUSED.length()), key);
            }
        }
    }

    /** The player as the protocol names them. */
    public static PlayerRef refOf(Player player) {
        return new PlayerRef(player.getUniqueId().toString(), player.getName());
    }

    /**
     * Asks the site on the async lane and hands a given answer to {@code then}, there too. A refusal and an
     * absent site are answered here. {@code movesCredits} refuses at once while the site is away, so a
     * player is not left waiting on a timeout for a purchase that cannot happen.
     */
    public <T> void ask(
            CommandSender asker, boolean movesCredits, Function<SiteSession, Answer<T>> call, Consumer<T> then) {
        Optional<SiteSession> open = session();
        if (open.isEmpty()) {
            messages.send(asker, Keys.NOT_CONNECTED);
            return;
        }
        SiteSession site = open.get();
        if (movesCredits && !site.reachable()) {
            messages.send(asker, Keys.SITE_AWAY);
            return;
        }
        scheduler.async(() -> {
            Answer<T> answer = call.apply(site);
            switch (answer) {
                case Answer.Given<T> given -> then.accept(given.value());
                case Answer.Refused<T> refused -> refused(asker, refused);
                case Answer.Away<T> away -> messages.send(asker, movesCredits ? Keys.SITE_UNSURE : Keys.SITE_SILENT);
            }
        });
    }

    /** Says a refusal in the words this catalogue has for its code. */
    public void refused(CommandSender to, Answer.Refused<?> refused) {
        Keys key = refusals.get(refused.code());
        String field = refused.field();
        if (key == null) {
            messages.send(to, Keys.REFUSED_OTHER, Text.placeholder("code", refused.code()));
        } else {
            messages.send(to, key, Text.placeholder("field", field == null ? "" : field));
        }
    }

    /** Runs off the server threads, where a call to the site may wait. */
    public void onAsync(Runnable work) {
        scheduler.async(work);
    }

    /** Runs on the player's own thread, which is where their inventory and their windows may be touched. */
    public void onPlayer(Player player, Runnable work) {
        scheduler.entity(player, () -> {
            if (player.isOnline()) {
                work.run();
            }
        });
    }

    /**
     * Hands a player something the site already settled, on their own thread, or runs {@code back} off the
     * server threads when they left first. Exactly one of the two runs.
     *
     * <p>The scheduler drops a task for an entity that is gone without a word, so the way back is a second
     * task, and whichever finds the hand-over still open is the one that counts.
     */
    public void handOrReturn(Player player, Runnable hand, Runnable back) {
        AtomicBoolean settled = new AtomicBoolean();
        scheduler.entity(player, () -> {
            if (player.isOnline() && settled.compareAndSet(false, true)) {
                hand.run();
            }
        });
        scheduler.asyncLater(HAND_WITHIN, () -> {
            if (settled.compareAndSet(false, true)) {
                back.run();
            }
        });
    }

    /**
     * The current session, for a surface that only reads what is kept. None while the site has stopped this
     * server: it calls nothing until an operator reloads, and a player is told the server is not connected rather
     * than the code the site refused the server with.
     */
    public Optional<SiteSession> session() {
        return session.get().filter(site -> site.state().get() != LinkState.STOPPED);
    }
}
