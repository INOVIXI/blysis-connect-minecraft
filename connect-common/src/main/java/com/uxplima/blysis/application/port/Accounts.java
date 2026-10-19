package com.uxplima.blysis.application.port;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.uxplima.blysis.domain.PlayerRef;
import com.uxplima.blysis.domain.account.Catalog;
import com.uxplima.blysis.domain.account.ChestItem;
import com.uxplima.blysis.domain.account.Goal;
import com.uxplima.blysis.domain.account.LinkCode;
import com.uxplima.blysis.domain.account.Profile;
import com.uxplima.blysis.domain.account.Purchase;
import com.uxplima.blysis.domain.account.Quote;
import com.uxplima.blysis.domain.account.SentGift;
import com.uxplima.blysis.domain.account.TopEntry;
import com.uxplima.blysis.domain.account.Voucher;
import org.jspecify.annotations.Nullable;

/**
 * What a player does with their account through this server. Every call blocks an async thread and ends in
 * an answer, a {@link SiteRefused} carrying the site's code, or a {@link SiteUnavailable}.
 *
 * <p>Every call that moves credits or claims something takes a key the caller made once for the player's
 * action, and sends the same key again when it retries, so a retry never charges twice.
 */
public interface Accounts {

    Profile profile(PlayerRef player);

    LinkCode startLink(PlayerRef player);

    /** Links with a code a member made on the site. Answers the member's username. */
    String verifyLink(PlayerRef player, String code);

    /** The catalogue, or empty when the site's copy is the one given. */
    Optional<Catalog> catalog(@Nullable String knownVersion);

    Purchase purchase(
            PlayerRef player,
            String productId,
            int quantity,
            Map<String, String> fields,
            @Nullable String recipient,
            String key);

    /** What {@link #purchase} would take for this basket, before the player confirms it. Moves no money. */
    Quote quote(PlayerRef player, String productId, int quantity, @Nullable String recipient);

    List<ChestItem> chest(PlayerRef player);

    void claim(PlayerRef player, String itemId, String key);

    void activate(PlayerRef player, String itemId, String key);

    List<SentGift> sentGifts(PlayerRef player);

    /** Takes back a gift nobody accepted. Answers what was refunded. */
    BigDecimal cancelGift(PlayerRef player, String itemId, String key);

    /** Turns gifts on or off, or only reads the switch when {@code receive} is null. */
    boolean receiveGifts(PlayerRef player, @Nullable Boolean receive);

    /** Sends credits. Answers the sender's balance after. */
    BigDecimal transfer(PlayerRef player, String to, BigDecimal amount, String key);

    /** Staff change a balance. Answers the target's balance after. */
    BigDecimal adminCredits(PlayerRef actor, String target, String operation, @Nullable BigDecimal amount, String key);

    List<TopEntry> top(String kind, String period, int limit);

    Goal goal();

    Voucher createVoucher(PlayerRef player, BigDecimal amount, String key);

    Voucher giveVoucher(PlayerRef actor, BigDecimal amount, String key);

    /** Redeems a voucher. Answers the amount it was worth. */
    BigDecimal redeemVoucher(PlayerRef player, String voucherId);
}
