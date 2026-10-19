package com.uxplima.blysis.paper.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import com.uxplima.blysis.application.SiteSession;
import com.uxplima.blysis.paper.adapter.inbound.Redeeming;
import com.uxplima.blysis.paper.adapter.inbound.Replies;
import com.uxplima.blysis.paper.adapter.outbound.VoucherItems;
import com.uxplima.blysis.paper.application.message.Keys;
import com.uxplima.uxmlib.menu.binding.MenuBindings;
import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.text.message.Messages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * A window or a voucher reached some other way than its command asks the same permission the command does.
 *
 * <p>Found live: with {@code blysis.store} taken away, {@code /store} refused Steve, and the chest's store tile
 * opened the store for him all the same. With {@code blysis.voucher.redeem} taken away, a right click on the
 * voucher in his hand paid it into his account.
 */
final class AWindowKeepsItsPermissionTest {

    private final NamespacedKey key = new NamespacedKey("blysis", "voucher");
    private final Messages messages = mock(Messages.class);
    private final Windows windows = mock(Windows.class);
    private final Scheduler scheduler = mock(Scheduler.class);
    private final SiteSession site = mock(SiteSession.class);
    private PlayerMock steve;
    private Replies replies;

    @BeforeEach
    void setUp() {
        steve = MockBukkit.mock().addPlayer("Steve");
        replies = new Replies(messages, scheduler, () -> Optional.of(site));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private void without(String node) {
        steve.addAttachment(MockBukkit.createMockPlugin(), node, false);
    }

    @Test
    @DisplayName("the store opened from another window refuses a player without blysis.store")
    void theStore() {
        without(StoreMenus.NODE);
        StoreMenus store = new StoreMenus(mock(MenuBindings.class), windows, replies, messages, mock(Words.class));

        store.openStore(steve);

        verify(messages).send(steve, Keys.COMMAND_NO_PERMISSION);
        verifyNoInteractions(scheduler, windows);
    }

    @Test
    @DisplayName("the chest and the sent gifts opened from another window refuse a player without blysis.chest")
    void theChest() {
        without(ChestMenus.NODE);
        ChestMenus chest = new ChestMenus(
                mock(MenuBindings.class), windows, replies, messages, mock(Words.class), () -> "example.com");

        chest.openChest(steve);
        chest.openSent(steve);

        verify(messages, times(2)).send(steve, Keys.COMMAND_NO_PERMISSION);
        verifyNoInteractions(scheduler, windows);
    }

    @Test
    @DisplayName("a voucher used in hand refuses a player without blysis.voucher.redeem, and stays")
    void theVoucher() {
        without(Redeeming.NODE);
        ItemStack voucher = new ItemStack(Material.PAPER);
        voucher.editMeta(meta -> meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, "gv_test"));
        steve.getInventory().setItem(0, voucher);
        steve.getInventory().setHeldItemSlot(0);
        Redeeming redeeming = new Redeeming(replies, messages, new VoucherItems(key, messages, () -> Material.PAPER));

        boolean handled = redeeming.redeem(steve, EquipmentSlot.HAND);

        assertThat(handled).isTrue();
        assertThat(steve.getInventory().getItem(0)).isEqualTo(voucher);
        verify(messages).send(steve, Keys.COMMAND_NO_PERMISSION);
        verify(scheduler, never()).async(any(Runnable.class));
    }
}
