package com.uxplima.blysis.paper.adapter.inbound;

import java.util.Objects;

import org.bukkit.entity.Player;

import com.uxplima.blysis.application.Profiles;
import com.uxplima.blysis.domain.PlayerRef;
import com.uxplima.blysis.domain.account.Amounts;
import com.uxplima.blysis.domain.account.Profile;
import com.uxplima.blysis.paper.application.message.Keys;
import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.message.Messages;

/**
 * Tells a player their balance: the command says it, and so does a tap on the store's credit card, which is
 * the only way a Bedrock player reads the card at all.
 */
public final class BalanceReply {

    private final Replies replies;
    private final Messages messages;

    public BalanceReply(Replies replies, Messages messages) {
        this.replies = Objects.requireNonNull(replies, "replies");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    /** Asks the site, or the memory of it while it does not answer, and says what it knows. */
    public void tell(Player player) {
        Objects.requireNonNull(player, "player");
        PlayerRef ref = Replies.refOf(player);
        replies.ask(player, false, site -> site.profiles().shown(ref), shown -> say(player, shown));
    }

    /** The balance, or, while the site does not answer, the last one it gave and a word that it is old. */
    private void say(Player player, Profiles.Shown shown) {
        Profile profile = shown.profile();
        if (!profile.linked()) {
            messages.send(player, Keys.REFUSED_NOT_LINKED);
            return;
        }
        messages.send(
                player,
                shown.remembered() ? Keys.CREDITS_BALANCE_REMEMBERED : Keys.CREDITS_BALANCE,
                Text.placeholder("balance", Amounts.shown(profile.balance())));
    }
}
