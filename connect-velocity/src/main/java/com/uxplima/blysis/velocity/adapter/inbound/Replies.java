package com.uxplima.blysis.velocity.adapter.inbound;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import net.kyori.adventure.audience.Audience;

import com.uxplima.blysis.application.Answer;
import com.uxplima.blysis.application.SiteSession;
import com.uxplima.blysis.application.port.Lanes;
import com.uxplima.blysis.domain.LinkState;
import com.uxplima.blysis.velocity.application.message.Keys;
import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.message.Messages;

/**
 * Asks the site off the proxy's network threads and tells the asker what came of it. A refusal reads the same
 * as on a server, and a site that is away is said in the same words.
 */
public final class Replies {

    private static final String REFUSED = "refused.";

    private final Messages messages;
    private final Lanes lanes;
    private final Supplier<Optional<SiteSession>> session;
    private final Map<String, Keys> refusals = new HashMap<>();

    public Replies(Messages messages, Lanes lanes, Supplier<Optional<SiteSession>> session) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.lanes = Objects.requireNonNull(lanes, "lanes");
        this.session = Objects.requireNonNull(session, "session");
        for (Keys key : Keys.values()) {
            if (key.path().startsWith(REFUSED)) {
                refusals.put(key.path().substring(REFUSED.length()), key);
            }
        }
    }

    /**
     * Asks the site on a lane and hands a given answer to {@code then}, there too. {@code movesCredits} refuses
     * at once while the site is away, so nobody waits on a timeout for a move that cannot happen.
     */
    public <T> void ask(Audience asker, boolean movesCredits, Function<SiteSession, Answer<T>> call, Consumer<T> then) {
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
        lanes.now(() -> {
            switch (call.apply(site)) {
                case Answer.Given<T> given -> then.accept(given.value());
                case Answer.Refused<T> refused -> refused(asker, refused);
                case Answer.Away<T> away -> messages.send(asker, movesCredits ? Keys.SITE_UNSURE : Keys.SITE_AWAY);
            }
        });
    }

    private void refused(Audience to, Answer.Refused<?> refused) {
        Keys key = refusals.get(refused.code());
        String field = refused.field();
        if (key == null) {
            messages.send(to, Keys.REFUSED_OTHER, Text.placeholder("code", refused.code()));
        } else {
            messages.send(to, key, Text.placeholder("field", field == null ? "" : field));
        }
    }

    /** The current session, or none while the site has stopped this proxy: it calls nothing until a reload. */
    public Optional<SiteSession> session() {
        return session.get().filter(site -> site.state().get() != LinkState.STOPPED);
    }
}
