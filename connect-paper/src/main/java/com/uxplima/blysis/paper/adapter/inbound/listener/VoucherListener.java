package com.uxplima.blysis.paper.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import com.uxplima.blysis.paper.adapter.inbound.Redeeming;

/** A right click with a voucher in hand redeems it, and does nothing else the item would have done. */
public final class VoucherListener implements Listener {

    private final Redeeming redeeming;

    public VoucherListener(Redeeming redeeming) {
        this.redeeming = Objects.requireNonNull(redeeming, "redeeming");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        EquipmentSlot hand = event.getHand();
        if (hand == null) {
            return;
        }
        if (redeeming.redeem(event.getPlayer(), hand)) {
            event.setCancelled(true);
        }
    }
}
