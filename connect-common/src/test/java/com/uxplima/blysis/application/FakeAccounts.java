package com.uxplima.blysis.application;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import com.uxplima.blysis.application.port.Accounts;
import com.uxplima.blysis.domain.PlayerRef;
import com.uxplima.blysis.domain.account.Catalog;
import com.uxplima.blysis.domain.account.ChestItem;
import com.uxplima.blysis.domain.account.Goal;
import com.uxplima.blysis.domain.account.LinkCode;
import com.uxplima.blysis.domain.account.Profile;
import com.uxplima.blysis.domain.account.Purchase;
import com.uxplima.blysis.domain.account.SentGift;
import com.uxplima.blysis.domain.account.TopEntry;
import com.uxplima.blysis.domain.account.Voucher;
import org.jspecify.annotations.Nullable;

/**
 * A site that answers what each test scripts. A call nobody scripted fails the test, so a use case that
 * calls more than it should is caught rather than answered.
 */
final class FakeAccounts implements Accounts {

    final Deque<Supplier<Profile>> profiles = new ArrayDeque<>();
    final Deque<Supplier<Optional<Catalog>>> catalogs = new ArrayDeque<>();
    final Deque<Supplier<Purchase>> purchases = new ArrayDeque<>();
    final List<@Nullable String> catalogVersions = new ArrayList<>();
    final List<String> purchaseKeys = new ArrayList<>();
    final Deque<Supplier<List<TopEntry>>> tops = new ArrayDeque<>();
    final Deque<Supplier<Goal>> goals = new ArrayDeque<>();
    final List<String> topsAsked = new ArrayList<>();

    private static <T> T next(Deque<Supplier<T>> queue, String call) {
        Supplier<T> answer = queue.poll();
        if (answer == null) {
            throw new AssertionError("nobody scripted a call to " + call);
        }
        return answer.get();
    }

    @Override
    public Profile profile(PlayerRef player) {
        return next(profiles, "profile");
    }

    @Override
    public Optional<Catalog> catalog(@Nullable String knownVersion) {
        catalogVersions.add(knownVersion);
        return next(catalogs, "catalog");
    }

    @Override
    public Purchase purchase(
            PlayerRef player,
            String productId,
            int quantity,
            Map<String, String> fields,
            @Nullable String recipient,
            String key) {
        purchaseKeys.add(key);
        return next(purchases, "purchase");
    }

    @Override
    public LinkCode startLink(PlayerRef player) {
        throw new AssertionError("startLink");
    }

    @Override
    public String verifyLink(PlayerRef player, String code) {
        throw new AssertionError("verifyLink");
    }

    @Override
    public List<ChestItem> chest(PlayerRef player) {
        throw new AssertionError("chest");
    }

    @Override
    public void claim(PlayerRef player, String itemId, String key) {
        throw new AssertionError("claim");
    }

    @Override
    public void activate(PlayerRef player, String itemId, String key) {
        throw new AssertionError("activate");
    }

    @Override
    public List<SentGift> sentGifts(PlayerRef player) {
        throw new AssertionError("sentGifts");
    }

    @Override
    public BigDecimal cancelGift(PlayerRef player, String itemId, String key) {
        throw new AssertionError("cancelGift");
    }

    @Override
    public boolean receiveGifts(PlayerRef player, @Nullable Boolean receive) {
        throw new AssertionError("receiveGifts");
    }

    @Override
    public BigDecimal transfer(PlayerRef player, String to, BigDecimal amount, String key) {
        throw new AssertionError("transfer");
    }

    @Override
    public BigDecimal adminCredits(
            PlayerRef actor, String target, String operation, @Nullable BigDecimal amount, String key) {
        throw new AssertionError("adminCredits");
    }

    @Override
    public List<TopEntry> top(String kind, String period, int limit) {
        topsAsked.add(kind + "/" + period + "/" + limit);
        return next(tops, "top");
    }

    @Override
    public Goal goal() {
        return next(goals, "goal");
    }

    @Override
    public Voucher createVoucher(PlayerRef player, BigDecimal amount, String key) {
        throw new AssertionError("createVoucher");
    }

    @Override
    public Voucher giveVoucher(PlayerRef actor, BigDecimal amount, String key) {
        throw new AssertionError("giveVoucher");
    }

    @Override
    public BigDecimal redeemVoucher(PlayerRef player, String voucherId) {
        throw new AssertionError("redeemVoucher");
    }
}
