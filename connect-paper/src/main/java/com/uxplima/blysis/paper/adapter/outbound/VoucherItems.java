package com.uxplima.blysis.paper.adapter.outbound;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import com.uxplima.blysis.domain.account.Voucher;
import com.uxplima.blysis.paper.application.message.Keys;
import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.message.Messages;

/**
 * The item a voucher travels as. It carries the voucher's id and nothing that pays: the site pays once per
 * id, so a copied item is a copied reference to a voucher that redeems once. The words are drawn in the
 * language of the player who holds it first.
 */
public final class VoucherItems {

    private final NamespacedKey idKey;
    private final Messages messages;
    private final Supplier<Material> material;

    public VoucherItems(NamespacedKey idKey, Messages messages, Supplier<Material> material) {
        this.idKey = Objects.requireNonNull(idKey, "idKey");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.material = Objects.requireNonNull(material, "material");
    }

    /** A new item for a voucher, in the words of the player it is made for. */
    public ItemStack itemFor(Player holder, Voucher voucher) {
        ItemStack item = new ItemStack(material.get());
        ItemMeta meta = item.getItemMeta();
        String amount = voucher.amount().stripTrailingZeros().toPlainString();
        meta.itemName(messages.render(holder, Keys.VOUCHER_ITEM_NAME, Text.placeholder("amount", amount)));
        meta.lore(List.of(
                messages.render(holder, Keys.VOUCHER_ITEM_LORE, Text.placeholder("amount", amount)),
                messages.render(holder, Keys.VOUCHER_ITEM_HINT, Text.placeholder("amount", amount))));
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, voucher.id());
        item.setItemMeta(meta);
        return item;
    }

    /** The voucher id an item carries, if it is a voucher at all. */
    public Optional<String> idOf(ItemStack item) {
        if (item.isEmpty() || !item.hasItemMeta()) {
            return Optional.empty();
        }
        String id = item.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
        return id == null || !id.startsWith("gv_") ? Optional.empty() : Optional.of(id);
    }

    /** An amount as the item reads it. */
    static String shown(BigDecimal amount) {
        return amount.stripTrailingZeros().toPlainString();
    }
}
