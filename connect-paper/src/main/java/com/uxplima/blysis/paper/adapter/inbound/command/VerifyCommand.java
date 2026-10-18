package com.uxplima.blysis.paper.adapter.inbound.command;

import java.util.Objects;

import org.bukkit.entity.Player;

import com.uxplima.blysis.application.Answer;
import com.uxplima.blysis.domain.PlayerRef;
import com.uxplima.blysis.paper.adapter.inbound.Replies;
import com.uxplima.blysis.paper.application.message.Keys;
import com.uxplima.uxmlib.command.Sender;
import com.uxplima.uxmlib.command.annotation.annotations.Arg;
import com.uxplima.uxmlib.command.annotation.annotations.FromConfig;
import com.uxplima.uxmlib.command.annotation.annotations.Permission;
import com.uxplima.uxmlib.command.annotation.annotations.PlayerOnly;
import com.uxplima.uxmlib.command.annotation.annotations.Subcommand;
import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.message.Messages;

/** Linking from the site: a member makes a code there, signed in, and types it here. */
@FromConfig(value = VerifyCommand.KEY, fallbackName = "verify", description = "Type a code made on the site")
public final class VerifyCommand {

    /** The key this command is named under in {@code commands.conf}. */
    public static final String KEY = "verify";

    private final Replies replies;
    private final Messages messages;

    public VerifyCommand(Replies replies, Messages messages) {
        this.replies = Objects.requireNonNull(replies, "replies");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Subcommand("")
    @Permission("blysis.link")
    @PlayerOnly
    public void verify(Sender sender, @Arg("code") String code) {
        Player player = sender.player().orElseThrow();
        PlayerRef ref = Replies.refOf(player);
        replies.ask(player, false, site -> Answer.of(() -> site.accounts().verifyLink(ref, code)), member -> {
            replies.session().ifPresent(site -> site.profiles().forget(ref));
            messages.send(player, Keys.LINK_DONE, Text.placeholder("member", member));
        });
    }
}
