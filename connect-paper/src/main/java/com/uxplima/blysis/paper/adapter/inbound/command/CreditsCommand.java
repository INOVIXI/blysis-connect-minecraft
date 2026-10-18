package com.uxplima.blysis.paper.adapter.inbound.command;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import com.uxplima.blysis.application.Answer;
import com.uxplima.blysis.application.Once;
import com.uxplima.blysis.domain.PlayerRef;
import com.uxplima.blysis.domain.account.Amounts;
import com.uxplima.blysis.domain.account.Profile;
import com.uxplima.blysis.domain.account.TopEntry;
import com.uxplima.blysis.paper.adapter.inbound.Replies;
import com.uxplima.blysis.paper.application.message.Keys;
import com.uxplima.uxmlib.command.Sender;
import com.uxplima.uxmlib.command.annotation.PlayerNames;
import com.uxplima.uxmlib.command.annotation.SuggestUsing;
import com.uxplima.uxmlib.command.annotation.annotations.Arg;
import com.uxplima.uxmlib.command.annotation.annotations.FromConfig;
import com.uxplima.uxmlib.command.annotation.annotations.Permission;
import com.uxplima.uxmlib.command.annotation.annotations.PlayerOnly;
import com.uxplima.uxmlib.command.annotation.annotations.Subcommand;
import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.message.Messages;

/**
 * Credits from the chat: the balance, sending to another member, the rankings, and the staff branches. The
 * money lives on the site; every branch asks it, and a branch that moves credits does so once.
 */
@FromConfig(value = CreditsCommand.KEY, fallbackName = "credits", description = "Your credits on the site")
public final class CreditsCommand {

    /** The key this command is named under in {@code commands.conf}. */
    public static final String KEY = "credits";

    private static final int TOP_SIZE = 10;

    private final Replies replies;
    private final Messages messages;

    public CreditsCommand(Replies replies, Messages messages) {
        this.replies = Objects.requireNonNull(replies, "replies");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Subcommand("")
    @Permission("blysis.credits")
    @PlayerOnly
    public void balance(Sender sender) {
        Player player = sender.player().orElseThrow();
        PlayerRef ref = Replies.refOf(player);
        replies.ask(player, false, site -> site.profiles().ask(ref), profile -> sayBalance(player, profile));
    }

    @FromConfig(value = "send", fallbackName = "send", description = "Send credits to another member")
    @Permission("blysis.credits.send")
    @PlayerOnly
    public void send(Sender sender, @Arg("to") @SuggestUsing(PlayerNames.KEY) String to, @Arg("amount") String typed) {
        Player player = sender.player().orElseThrow();
        Optional<BigDecimal> amount = Amounts.parse(typed);
        if (amount.isEmpty()) {
            messages.send(player, Keys.REFUSED_AMOUNT_INVALID);
            return;
        }
        PlayerRef ref = Replies.refOf(player);
        replies.ask(
                player,
                true,
                site -> site.once().run(key -> site.accounts().transfer(ref, to, amount.get(), key)),
                balance -> {
                    replies.session().ifPresent(site -> site.profiles().balance(ref, balance));
                    messages.send(
                            player,
                            Keys.CREDITS_SENT,
                            Text.placeholder("amount", Amounts.shown(amount.get())),
                            Text.placeholder("to", to),
                            Text.placeholder("balance", Amounts.shown(balance)));
                });
    }

    @FromConfig(value = "top", fallbackName = "top", description = "The members who hold or bought the most")
    @Permission("blysis.credits.top")
    public void top(
            Sender sender,
            @Arg(value = "kind", optional = true, def = "balance") String kind,
            @Arg(value = "period", optional = true, def = "all") String period) {
        CommandSender asker = sender.bukkit();
        String readKind = List.of("topups", "latest").contains(kind) ? kind : "balance";
        String readPeriod = List.of("day", "month", "year").contains(period) ? period : "all";
        replies.ask(
                asker,
                false,
                site -> Answer.of(() -> site.accounts().top(readKind, readPeriod, TOP_SIZE)),
                entries -> sayTop(asker, entries));
    }

    @FromConfig(value = "add", fallbackName = "add", description = "Add credits to a member, for staff")
    @Permission("blysis.admin.credits")
    public void add(
            Sender sender, @Arg("player") @SuggestUsing(PlayerNames.KEY) String target, @Arg("amount") String typed) {
        staff(sender, target, "add", typed);
    }

    @FromConfig(value = "remove", fallbackName = "remove", description = "Take credits from a member, for staff")
    @Permission("blysis.admin.credits")
    public void remove(
            Sender sender, @Arg("player") @SuggestUsing(PlayerNames.KEY) String target, @Arg("amount") String typed) {
        staff(sender, target, "remove", typed);
    }

    @FromConfig(value = "set", fallbackName = "set", description = "Set a member's credits, for staff")
    @Permission("blysis.admin.credits")
    public void set(
            Sender sender, @Arg("player") @SuggestUsing(PlayerNames.KEY) String target, @Arg("amount") String typed) {
        staff(sender, target, "set", typed);
    }

    @FromConfig(value = "see", fallbackName = "see", description = "Read a member's credits, for staff")
    @Permission("blysis.admin.credits")
    public void see(Sender sender, @Arg("player") @SuggestUsing(PlayerNames.KEY) String target) {
        CommandSender asker = sender.bukkit();
        replies.ask(
                asker,
                false,
                site -> Answer.of(() -> site.accounts().adminCredits(actorOf(asker), target, "show", null, Once.key())),
                balance -> sayStaff(asker, target, balance));
    }

    private void staff(Sender sender, String target, String operation, String typed) {
        CommandSender asker = sender.bukkit();
        Optional<BigDecimal> amount = operation.equals("set") ? Amounts.parseOrZero(typed) : Amounts.parse(typed);
        if (amount.isEmpty()) {
            messages.send(asker, Keys.REFUSED_AMOUNT_INVALID);
            return;
        }
        replies.ask(
                asker,
                true,
                site -> site.once()
                        .run(key -> site.accounts().adminCredits(actorOf(asker), target, operation, amount.get(), key)),
                balance -> sayStaff(asker, target, balance));
    }

    private void sayBalance(Player player, Profile profile) {
        if (!profile.linked()) {
            messages.send(player, Keys.REFUSED_NOT_LINKED);
            return;
        }
        messages.send(player, Keys.CREDITS_BALANCE, Text.placeholder("balance", Amounts.shown(profile.balance())));
    }

    private void sayStaff(CommandSender asker, String target, BigDecimal balance) {
        messages.send(
                asker,
                Keys.CREDITS_ADMIN_DONE,
                Text.placeholder("target", target),
                Text.placeholder("balance", Amounts.shown(balance)));
    }

    private void sayTop(CommandSender asker, List<TopEntry> entries) {
        if (entries.isEmpty()) {
            messages.send(asker, Keys.CREDITS_TOP_EMPTY);
            return;
        }
        messages.send(asker, Keys.CREDITS_TOP_HEADER);
        for (TopEntry entry : entries) {
            String name = entry.name();
            Component shownName =
                    name == null ? messages.render(asker, Keys.CREDITS_TOP_ANONYMOUS) : Component.text(name);
            messages.send(
                    asker,
                    Keys.CREDITS_TOP_LINE,
                    Text.placeholder("place", Integer.toString(entry.rank())),
                    Text.component("name", shownName),
                    Text.placeholder("amount", Amounts.shown(entry.amount())));
        }
    }

    /** Staff act under their own name; the console under the server's. */
    private static PlayerRef actorOf(CommandSender asker) {
        return asker instanceof Player player ? Replies.refOf(player) : new PlayerRef(null, asker.getName());
    }
}
