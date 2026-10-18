package com.uxplima.blysis.velocity.adapter.inbound.command;

import java.util.Objects;

import com.uxplima.blysis.application.Answer;
import com.uxplima.blysis.domain.PlayerRef;
import com.uxplima.blysis.velocity.adapter.inbound.Replies;
import com.uxplima.blysis.velocity.adapter.outbound.ProxyPlayers;
import com.uxplima.blysis.velocity.application.message.Keys;
import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.message.Messages;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;

/** {@code /verify <code>}: links with a code a member made on the site, signed in. */
public final class VerifyCommand implements SimpleCommand {

    private final Replies replies;
    private final Messages messages;

    public VerifyCommand(Replies replies, Messages messages) {
        this.replies = Objects.requireNonNull(replies, "replies");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) {
            messages.send(invocation.source(), Keys.COMMAND_PLAYER_ONLY);
            return;
        }
        String[] args = invocation.arguments();
        if (args.length != 1) {
            messages.send(player, Keys.REFUSED_CODE_INVALID);
            return;
        }
        PlayerRef ref = ProxyPlayers.refOf(player);
        replies.ask(player, false, site -> Answer.of(() -> site.accounts().verifyLink(ref, args[0])), member -> {
            replies.session().ifPresent(site -> site.profiles().forget(ref));
            messages.send(player, Keys.LINK_DONE, Text.placeholder("member", member));
        });
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return Nodes.player(invocation.source(), LinkCommand.NODE);
    }
}
