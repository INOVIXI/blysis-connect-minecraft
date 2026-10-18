package com.uxplima.blysis.paper.adapter.inbound.command;

import java.util.Objects;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;

import com.uxplima.blysis.application.Answer;
import com.uxplima.blysis.domain.PlayerRef;
import com.uxplima.blysis.paper.adapter.inbound.Replies;
import com.uxplima.blysis.paper.application.message.Keys;
import com.uxplima.uxmlib.command.Sender;
import com.uxplima.uxmlib.command.annotation.annotations.FromConfig;
import com.uxplima.uxmlib.command.annotation.annotations.Permission;
import com.uxplima.uxmlib.command.annotation.annotations.PlayerOnly;
import com.uxplima.uxmlib.command.annotation.annotations.Subcommand;
import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.message.Messages;

/**
 * Linking from the game: a code the player confirms on the site, signed in. The site shows the member the
 * account the code would link before it links anything.
 */
@FromConfig(value = LinkCommand.KEY, fallbackName = "link", description = "Link this account to the site")
public final class LinkCommand {

    /** The key this command is named under in {@code commands.conf}. */
    public static final String KEY = "link";

    private final Replies replies;
    private final Messages messages;

    public LinkCommand(Replies replies, Messages messages) {
        this.replies = Objects.requireNonNull(replies, "replies");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Subcommand("")
    @Permission("blysis.link")
    @PlayerOnly
    public void link(Sender sender) {
        Player player = sender.player().orElseThrow();
        PlayerRef ref = Replies.refOf(player);
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
}
