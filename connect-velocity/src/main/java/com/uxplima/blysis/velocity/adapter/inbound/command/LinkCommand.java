package com.uxplima.blysis.velocity.adapter.inbound.command;

import java.util.Objects;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;

import com.uxplima.blysis.application.Answer;
import com.uxplima.blysis.domain.PlayerRef;
import com.uxplima.blysis.velocity.adapter.inbound.Replies;
import com.uxplima.blysis.velocity.adapter.outbound.ProxyPlayers;
import com.uxplima.blysis.velocity.application.message.Keys;
import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.message.Messages;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;

/** {@code /link}: a short-lived code, and a link to the page that confirms it on the site. */
public final class LinkCommand implements SimpleCommand {

    /** The node a player needs. */
    public static final String NODE = "blysis.link";

    private final Replies replies;
    private final Messages messages;

    public LinkCommand(Replies replies, Messages messages) {
        this.replies = Objects.requireNonNull(replies, "replies");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) {
            messages.send(invocation.source(), Keys.COMMAND_PLAYER_ONLY);
            return;
        }
        PlayerRef ref = ProxyPlayers.refOf(player);
        replies.ask(player, false, site -> site.profiles().ask(ref), profile -> {
            if (profile.linked()) {
                messages.send(player, Keys.LINK_ALREADY, Text.placeholder("member", profile.username()));
                return;
            }
            replies.ask(
                    player,
                    false,
                    site -> Answer.of(() -> site.accounts().startLink(ref)),
                    code -> messages.send(
                            player,
                            Keys.LINK_CODE,
                            Text.component(
                                    "link", Component.text(code.url()).clickEvent(ClickEvent.openUrl(code.url()))),
                            Text.placeholder("code", code.code())));
        });
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return Nodes.player(invocation.source(), NODE);
    }
}
