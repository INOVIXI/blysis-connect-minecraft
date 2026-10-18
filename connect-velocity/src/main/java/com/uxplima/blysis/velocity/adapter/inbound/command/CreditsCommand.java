package com.uxplima.blysis.velocity.adapter.inbound.command;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;

import com.uxplima.blysis.application.Answer;
import com.uxplima.blysis.application.Once;
import com.uxplima.blysis.domain.PlayerRef;
import com.uxplima.blysis.domain.account.Amounts;
import com.uxplima.blysis.domain.account.TopEntry;
import com.uxplima.blysis.velocity.adapter.inbound.Replies;
import com.uxplima.blysis.velocity.adapter.outbound.ProxyPlayers;
import com.uxplima.blysis.velocity.application.message.Keys;
import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.message.Messages;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;

/**
 * {@code /credits} on the network: the balance, sending, the rankings, and the staff branches, the same as on a
 * server. The words after the command are fixed here because Velocity's simple commands take no tree; the
 * command itself is named in {@code commands.conf}.
 */
public final class CreditsCommand implements SimpleCommand {

    private static final int TOP_SIZE = 10;
    private static final List<String> STAFF = List.of("add", "remove", "set", "see");

    private final Replies replies;
    private final Messages messages;

    public CreditsCommand(Replies replies, Messages messages) {
        this.replies = Objects.requireNonNull(replies, "replies");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();
        String verb = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (verb) {
            case "" -> balance(source);
            case "send" -> send(source, args);
            case "top" -> top(source, args);
            default -> {
                if (STAFF.contains(verb)) {
                    staff(source, verb, args);
                } else {
                    messages.send(source, Keys.CREDITS_USAGE);
                }
            }
        }
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        if (invocation.arguments().length > 1) {
            return List.of();
        }
        return Nodes.staff(invocation.source(), "blysis.admin.credits")
                ? List.of("send", "top", "add", "remove", "set", "see")
                : List.of("send", "top");
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return Nodes.player(invocation.source(), "blysis.credits");
    }

    private void balance(CommandSource source) {
        if (!(source instanceof Player player)) {
            messages.send(source, Keys.COMMAND_PLAYER_ONLY);
            return;
        }
        PlayerRef ref = ProxyPlayers.refOf(player);
        replies.ask(player, false, site -> site.profiles().ask(ref), profile -> {
            if (!profile.linked()) {
                messages.send(player, Keys.REFUSED_NOT_LINKED);
                return;
            }
            messages.send(player, Keys.CREDITS_BALANCE, Text.placeholder("balance", Amounts.shown(profile.balance())));
        });
    }

    private void send(CommandSource source, String[] args) {
        if (!(source instanceof Player player)) {
            messages.send(source, Keys.COMMAND_PLAYER_ONLY);
            return;
        }
        if (!Nodes.player(player, "blysis.credits.send")) {
            messages.send(player, Keys.COMMAND_NO_PERMISSION);
            return;
        }
        Optional<BigDecimal> amount = args.length == 3 ? Amounts.parse(args[2]) : Optional.empty();
        if (amount.isEmpty()) {
            messages.send(player, args.length == 3 ? Keys.REFUSED_AMOUNT_INVALID : Keys.CREDITS_USAGE);
            return;
        }
        String to = args[1];
        PlayerRef ref = ProxyPlayers.refOf(player);
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

    private void top(CommandSource source, String[] args) {
        if (!Nodes.player(source, "blysis.credits.top")) {
            messages.send(source, Keys.COMMAND_NO_PERMISSION);
            return;
        }
        String asked = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "balance";
        String kind = List.of("topups", "latest").contains(asked) ? asked : "balance";
        String period = args.length > 2 && List.of("day", "month", "year").contains(args[2]) ? args[2] : "all";
        replies.ask(
                source,
                false,
                site -> Answer.of(() -> site.accounts().top(kind, period, TOP_SIZE)),
                entries -> sayTop(source, entries));
    }

    private void staff(CommandSource source, String operation, String[] args) {
        if (!Nodes.staff(source, "blysis.admin.credits")) {
            messages.send(source, Keys.COMMAND_NO_PERMISSION);
            return;
        }
        boolean show = operation.equals("see");
        if (args.length != (show ? 2 : 3)) {
            messages.send(source, Keys.CREDITS_USAGE);
            return;
        }
        String target = args[1];
        Optional<BigDecimal> amount = show
                ? Optional.empty()
                : operation.equals("set") ? Amounts.parseOrZero(args[2]) : Amounts.parse(args[2]);
        if (!show && amount.isEmpty()) {
            messages.send(source, Keys.REFUSED_AMOUNT_INVALID);
            return;
        }
        String wire = show ? "show" : operation;
        BigDecimal value = amount.orElse(null);
        replies.ask(
                source,
                !show,
                site -> show
                        ? Answer.of(() -> site.accounts().adminCredits(actorOf(source), target, wire, null, Once.key()))
                        : site.once()
                                .run(key -> site.accounts().adminCredits(actorOf(source), target, wire, value, key)),
                balance -> messages.send(
                        source,
                        Keys.CREDITS_ADMIN_DONE,
                        Text.placeholder("target", target),
                        Text.placeholder("balance", Amounts.shown(balance))));
    }

    private void sayTop(Audience asker, List<TopEntry> entries) {
        if (entries.isEmpty()) {
            messages.send(asker, Keys.CREDITS_TOP_EMPTY);
            return;
        }
        messages.send(asker, Keys.CREDITS_TOP_HEADER);
        for (TopEntry entry : entries) {
            String name = entry.name();
            Component shown = name == null ? messages.render(asker, Keys.CREDITS_TOP_ANONYMOUS) : Component.text(name);
            messages.send(
                    asker,
                    Keys.CREDITS_TOP_LINE,
                    Text.placeholder("place", Integer.toString(entry.rank())),
                    Text.component("name", shown),
                    Text.placeholder("amount", Amounts.shown(entry.amount())));
        }
    }

    /** Staff act under their own name; the console under the proxy's. */
    private static PlayerRef actorOf(CommandSource source) {
        return source instanceof Player player ? ProxyPlayers.refOf(player) : new PlayerRef(null, "console");
    }
}
