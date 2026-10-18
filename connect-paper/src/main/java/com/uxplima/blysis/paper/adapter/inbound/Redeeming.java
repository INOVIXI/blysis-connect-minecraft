package com.uxplima.blysis.paper.adapter.inbound;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import com.uxplima.blysis.application.Answer;
import com.uxplima.blysis.domain.PlayerRef;
import com.uxplima.blysis.paper.adapter.outbound.VoucherItems;
import com.uxplima.blysis.paper.application.message.Keys;
import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.message.Messages;

/**
 * Redeeming the voucher a player holds. The site pays once per id; the item is taken when the site paid, or
 * when the site says the id was spent, because then the paper is worth nothing. One redemption per player is
 * in flight at a time, so a spam of clicks asks the site once.
 */
public final class Redeeming {

    private final Replies replies;
    private final Messages messages;
    private final VoucherItems vouchers;
    private final Map<UUID, Boolean> inFlight = new ConcurrentHashMap<>();

    public Redeeming(Replies replies, Messages messages, VoucherItems vouchers) {
        this.replies = Objects.requireNonNull(replies, "replies");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.vouchers = Objects.requireNonNull(vouchers, "vouchers");
    }

    /** Redeems the voucher in this hand. False when the hand holds none. */
    public boolean redeem(Player player, EquipmentSlot hand) {
        ItemStack held = player.getInventory().getItem(hand);
        String id = vouchers.idOf(held).orElse(null);
        if (id == null) {
            return false;
        }
        if (inFlight.putIfAbsent(player.getUniqueId(), Boolean.TRUE) != null) {
            return true;
        }
        PlayerRef ref = Replies.refOf(player);
        replies.session()
                .ifPresentOrElse(
                        site -> replies.onAsync(() -> {
                            try {
                                settle(
                                        player,
                                        hand,
                                        id,
                                        Answer.of(() -> site.accounts().redeemVoucher(ref, id)));
                            } finally {
                                inFlight.remove(player.getUniqueId());
                            }
                        }),
                        () -> {
                            inFlight.remove(player.getUniqueId());
                            messages.send(player, Keys.NOT_CONNECTED);
                        });
        return true;
    }

    private void settle(Player player, EquipmentSlot hand, String id, Answer<java.math.BigDecimal> answer) {
        switch (answer) {
            case Answer.Given<java.math.BigDecimal> paid -> {
                replies.onPlayer(player, () -> takeOne(player, hand, id));
                replies.session().ifPresent(site -> site.profiles().forget(Replies.refOf(player)));
                messages.send(
                        player,
                        Keys.VOUCHER_REDEEMED,
                        Text.placeholder(
                                "amount", paid.value().stripTrailingZeros().toPlainString()));
            }
            case Answer.Refused<java.math.BigDecimal> refused -> {
                if (refused.code().equals("voucher_used") || refused.code().equals("not_found")) {
                    replies.onPlayer(player, () -> takeOne(player, hand, id));
                }
                replies.refused(player, refused);
            }
            case Answer.Away<java.math.BigDecimal> away -> messages.send(player, Keys.SITE_AWAY);
        }
    }

    /** Takes one voucher of this id from the hand, if it is still there: the player may have moved it. */
    private void takeOne(Player player, EquipmentSlot hand, String id) {
        ItemStack held = player.getInventory().getItem(hand);
        if (vouchers.idOf(held).filter(id::equals).isPresent()) {
            held.setAmount(held.getAmount() - 1);
            player.getInventory().setItem(hand, held.getAmount() <= 0 ? null : held);
        }
    }
}
