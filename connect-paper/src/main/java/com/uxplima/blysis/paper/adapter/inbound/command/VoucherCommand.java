package com.uxplima.blysis.paper.adapter.inbound.command;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import com.uxplima.blysis.domain.PlayerRef;
import com.uxplima.blysis.domain.account.Voucher;
import com.uxplima.blysis.paper.adapter.inbound.Redeeming;
import com.uxplima.blysis.paper.adapter.inbound.Replies;
import com.uxplima.blysis.paper.adapter.outbound.VoucherItems;
import com.uxplima.blysis.paper.application.message.Keys;
import com.uxplima.uxmlib.command.Sender;
import com.uxplima.uxmlib.command.annotation.PlayerNames;
import com.uxplima.uxmlib.command.annotation.SuggestUsing;
import com.uxplima.uxmlib.command.annotation.annotations.Arg;
import com.uxplima.uxmlib.command.annotation.annotations.FromConfig;
import com.uxplima.uxmlib.command.annotation.annotations.Permission;
import com.uxplima.uxmlib.command.annotation.annotations.PlayerOnly;
import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.message.Messages;

/**
 * Vouchers: credits turned into an item a player can hand over. Creating one takes the credits on the site
 * first; the item only carries the id. Staff may mint one nobody paid for, on a server the site allows to.
 */
@FromConfig(value = VoucherCommand.KEY, fallbackName = "voucher", description = "Turn credits into a voucher")
public final class VoucherCommand {

    /** The key this command is named under in {@code commands.conf}. */
    public static final String KEY = "voucher";

    private final Replies replies;
    private final Messages messages;
    private final VoucherItems items;
    private final Redeeming redeeming;

    public VoucherCommand(Replies replies, Messages messages, VoucherItems items, Redeeming redeeming) {
        this.replies = Objects.requireNonNull(replies, "replies");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.items = Objects.requireNonNull(items, "items");
        this.redeeming = Objects.requireNonNull(redeeming, "redeeming");
    }

    @FromConfig(value = "create", fallbackName = "create", description = "Make a voucher out of your own credits")
    @Permission("blysis.voucher.create")
    @PlayerOnly
    public void create(Sender sender, @Arg("amount") String typed) {
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
                site -> site.once().run(key -> site.accounts().createVoucher(ref, amount.get(), key)),
                voucher -> {
                    replies.session().ifPresent(site -> site.profiles().forget(ref));
                    hand(player, voucher);
                    messages.send(
                            player, Keys.VOUCHER_CREATED, Text.placeholder("amount", Amounts.shown(voucher.amount())));
                });
    }

    @FromConfig(value = "give", fallbackName = "give", description = "Mint a voucher for a player, for staff")
    @Permission("blysis.admin.voucher")
    public void give(
            Sender sender, @Arg("player") @SuggestUsing(PlayerNames.KEY) String name, @Arg("amount") String typed) {
        CommandSender asker = sender.bukkit();
        Player target = asker.getServer().getPlayerExact(name);
        if (target == null) {
            messages.send(asker, Keys.PLAYER_UNKNOWN, Text.placeholder("input", name));
            return;
        }
        Optional<BigDecimal> amount = Amounts.parse(typed);
        if (amount.isEmpty()) {
            messages.send(asker, Keys.REFUSED_AMOUNT_INVALID);
            return;
        }
        PlayerRef actor = asker instanceof Player player ? Replies.refOf(player) : new PlayerRef(null, asker.getName());
        replies.ask(
                asker,
                true,
                site -> site.once().run(key -> site.accounts().giveVoucher(actor, amount.get(), key)),
                voucher -> {
                    hand(target, voucher);
                    String shown = Amounts.shown(voucher.amount());
                    messages.send(
                            asker,
                            Keys.VOUCHER_GIVEN,
                            Text.placeholder("amount", shown),
                            Text.placeholder("player", target.getName()));
                    messages.send(target, Keys.VOUCHER_RECEIVED, Text.placeholder("amount", shown));
                });
    }

    @FromConfig(value = "redeem", fallbackName = "redeem", description = "Redeem the voucher in your hand")
    @Permission("blysis.voucher.redeem")
    @PlayerOnly
    public void redeem(Sender sender) {
        Player player = sender.player().orElseThrow();
        if (!redeeming.redeem(player, EquipmentSlot.HAND)) {
            messages.send(player, Keys.VOUCHER_NOT_HELD);
        }
    }

    /** Puts the voucher in the inventory on the player's own thread, or at their feet when it is full. */
    private void hand(Player player, Voucher voucher) {
        replies.onPlayer(player, () -> {
            ItemStack item = items.itemFor(player, voucher);
            if (!player.getInventory().addItem(item).isEmpty()) {
                player.getWorld().dropItemNaturally(player.getLocation(), item);
                messages.send(player, Keys.VOUCHER_DROPPED);
            }
        });
    }
}
