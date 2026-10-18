package com.uxplima.blysis.velocity.adapter.inbound;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.blysis.application.port.LinkWatcher;
import com.uxplima.blysis.domain.LinkState;
import com.uxplima.blysis.domain.PlayerRef;
import com.uxplima.blysis.domain.SiteEvent;
import com.uxplima.blysis.velocity.adapter.outbound.ProxyPlayers;
import com.uxplima.blysis.velocity.application.message.Keys;
import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.message.Messages;

/**
 * What the proxy does with the site's events. The servers behind it announce purchases, top-ups and goals
 * themselves, so the proxy saying them too would say each one twice. A link confirmed for a player who was on
 * the proxy is the one event that is the proxy's own: the player is told, and their profile is read again.
 */
public final class ProxyWatcher implements LinkWatcher {

    private final ProxyPlayers players;
    private final Messages messages;
    private final Replies replies;

    public ProxyWatcher(ProxyPlayers players, Messages messages, Replies replies) {
        this.players = Objects.requireNonNull(players, "players");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.replies = Objects.requireNonNull(replies, "replies");
    }

    @Override
    public void stateChanged(LinkState state, String detail) {
        // The console line is the ConsoleWatcher's, and the proxy has no staff screen of its own.
    }

    @Override
    public void events(List<SiteEvent> events) {
        for (SiteEvent event : events) {
            if (!event.type().equals("link.confirmed")) {
                continue;
            }
            String id = event.fields().get("player.id");
            String name = event.fields().getOrDefault("player.name", "");
            replies.session().ifPresent(site -> site.profiles().forget(new PlayerRef(id, name)));
            if (id == null) {
                continue;
            }
            try {
                players.find(UUID.fromString(id))
                        .ifPresent(player -> messages.send(
                                player,
                                Keys.LINK_DONE,
                                Text.placeholder("member", event.fields().getOrDefault("member", ""))));
            } catch (IllegalArgumentException notAnId) {
                // A site that sent no id names nobody this proxy can reach.
            }
        }
    }
}
