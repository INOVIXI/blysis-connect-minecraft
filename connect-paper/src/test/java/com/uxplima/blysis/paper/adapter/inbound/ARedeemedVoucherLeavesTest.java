package com.uxplima.blysis.paper.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import com.uxplima.blysis.application.Boards;
import com.uxplima.blysis.application.CatalogCache;
import com.uxplima.blysis.application.Once;
import com.uxplima.blysis.application.Profiles;
import com.uxplima.blysis.application.SiteSession;
import com.uxplima.blysis.application.port.Accounts;
import com.uxplima.blysis.application.port.SiteUnavailable;
import com.uxplima.blysis.domain.LinkState;
import com.uxplima.blysis.domain.PlayerRef;
import com.uxplima.blysis.paper.adapter.outbound.VoucherItems;
import com.uxplima.blysis.paper.application.message.Keys;
import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.scheduler.TaskHandle;
import com.uxplima.uxmlib.text.message.Messages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * A voucher the site paid for leaves the inventory, wherever in it the player put it meanwhile.
 *
 * <p>Found live: Steve redeemed a voucher and moved to the next hotbar slot before the site answered. The
 * item was taken from the hand he held now, which had no voucher, so the paid voucher stayed in his
 * inventory looking as good as new, ready to be sold to somebody who would find it worth nothing.
 */
final class ARedeemedVoucherLeavesTest {

    private static final String ID = "gv_test";

    private final NamespacedKey key = new NamespacedKey("blysis", "voucher");
    private final List<Runnable> onPlayer = new ArrayList<>();
    private final List<Runnable> async = new ArrayList<>();
    private final Accounts accounts = mock(Accounts.class);
    private PlayerMock steve;
    private Messages messages = mock(Messages.class);
    private Redeeming redeeming;

    @BeforeEach
    void setUp() {
        steve = MockBukkit.mock().addPlayer("Steve");
        Scheduler scheduler = mock(Scheduler.class);
        when(scheduler.async(any(Runnable.class))).thenAnswer(call -> {
            async.add(call.getArgument(0));
            return mock(TaskHandle.class);
        });
        when(scheduler.entity(any(Entity.class), any(Runnable.class))).thenAnswer(call -> {
            onPlayer.add(call.getArgument(1));
            return mock(TaskHandle.class);
        });
        when(accounts.redeemVoucher(any(PlayerRef.class), eq(ID))).thenReturn(BigDecimal.ONE);
        SiteSession site = new SiteSession(
                accounts,
                mock(Profiles.class),
                mock(CatalogCache.class),
                mock(Boards.class),
                mock(Once.class),
                () -> LinkState.CONNECTED);
        messages = mock(Messages.class);
        Replies replies = new Replies(messages, scheduler, () -> Optional.of(site));
        redeeming = new Redeeming(replies, messages, new VoucherItems(key, messages, () -> Material.PAPER));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private ItemStack voucher() {
        ItemStack item = new ItemStack(Material.PAPER);
        item.editMeta(meta -> meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, ID));
        return item;
    }

    private void siteAnswers() {
        async.forEach(Runnable::run);
        onPlayer.forEach(Runnable::run);
    }

    private long vouchersLeft() {
        long inInventory = Arrays.stream(steve.getInventory().getContents())
                .filter(item -> item != null && item.getType() == Material.PAPER)
                .count();
        return inInventory + (steve.getItemOnCursor().getType() == Material.PAPER ? 1 : 0);
    }

    @Test
    @DisplayName("a voucher still in the hand is taken")
    void fromTheHand() {
        steve.getInventory().setItem(0, voucher());
        steve.getInventory().setHeldItemSlot(0);

        redeeming.redeem(steve, EquipmentSlot.HAND);
        siteAnswers();

        assertThat(vouchersLeft()).isZero();
    }

    @Test
    @DisplayName("a voucher whose player moved to another hotbar slot before the answer is taken all the same")
    void afterTheHandMoved() {
        steve.getInventory().setItem(0, voucher());
        steve.getInventory().setHeldItemSlot(0);

        redeeming.redeem(steve, EquipmentSlot.HAND);
        steve.getInventory().setHeldItemSlot(1);
        siteAnswers();

        assertThat(vouchersLeft()).isZero();
    }

    @Test
    @DisplayName("a voucher moved into the backpack before the answer is taken all the same")
    void afterTheItemMoved() {
        steve.getInventory().setItem(0, voucher());
        steve.getInventory().setHeldItemSlot(0);

        redeeming.redeem(steve, EquipmentSlot.HAND);
        steve.getInventory().setItem(0, null);
        steve.getInventory().setItem(20, voucher());
        siteAnswers();

        assertThat(vouchersLeft()).isZero();
    }

    @Test
    @DisplayName("a voucher picked up on the cursor before the answer is taken all the same")
    void fromTheCursor() {
        steve.getInventory().setItem(0, voucher());
        steve.getInventory().setHeldItemSlot(0);

        redeeming.redeem(steve, EquipmentSlot.HAND);
        steve.getInventory().setItem(0, null);
        steve.setItemOnCursor(voucher());
        siteAnswers();

        assertThat(vouchersLeft()).isZero();
    }

    /**
     * A redemption sent and not answered may have paid: the voucher stays, since only the site's word takes it,
     * and the player is told to look at the balance rather than that nothing happened. A later try with the same
     * paper is answered "already used" and takes it then.
     */
    @Test
    @DisplayName("a redemption the site did not answer keeps the voucher and asks the player to look at the balance")
    void anUnansweredRedemptionIsUnsure() {
        when(accounts.redeemVoucher(any(PlayerRef.class), eq(ID))).thenThrow(new SiteUnavailable("down"));
        steve.getInventory().setItem(0, voucher());
        steve.getInventory().setHeldItemSlot(0);

        redeeming.redeem(steve, EquipmentSlot.HAND);
        siteAnswers();

        assertThat(vouchersLeft()).isEqualTo(1);
        verify(messages).send(eq(steve), eq(Keys.SITE_UNSURE), any(TagResolver[].class));
    }
}
