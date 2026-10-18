package com.uxplima.blysis.paper.adapter.inbound.gui;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import org.bukkit.entity.Player;

import com.uxplima.blysis.application.Answer;
import com.uxplima.blysis.domain.PlayerRef;
import com.uxplima.blysis.domain.account.Amounts;
import com.uxplima.blysis.domain.account.ChestItem;
import com.uxplima.blysis.domain.account.SentGift;
import com.uxplima.blysis.paper.adapter.inbound.Replies;
import com.uxplima.blysis.paper.application.message.Keys;
import com.uxplima.uxmlib.menu.binding.MenuBindings;
import com.uxplima.uxmlib.menu.runtime.MenuActionContext;
import com.uxplima.uxmlib.menu.runtime.MenuContext;
import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.message.Messages;
import org.jspecify.annotations.Nullable;

/**
 * The chest and the gifts: what a player bought and has not taken, the gifts that wait for them, the gifts
 * they sent, and the switch that says whether they take gifts at all.
 *
 * <p>Every list is asked of the site when its window opens, and every click that changes one asks the site
 * and opens the window again on the new answer, so a window never shows what was true a click ago.
 */
public final class ChestMenus {

    /** The window of what waits for the player. */
    public static final String CHEST = "chest";

    /** The window of the gifts the player sent. */
    public static final String SENT = "sent";

    private final Windows windows;
    private final Replies replies;
    private final Messages messages;
    private final Words words;
    private final Supplier<String> siteAddress;

    public ChestMenus(
            MenuBindings bindings,
            Windows windows,
            Replies replies,
            Messages messages,
            Words words,
            Supplier<String> siteAddress) {
        this.windows = Objects.requireNonNull(windows, "windows");
        this.replies = Objects.requireNonNull(replies, "replies");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.words = Objects.requireNonNull(words, "words");
        this.siteAddress = Objects.requireNonNull(siteAddress, "siteAddress");

        Objects.requireNonNull(bindings, "bindings");
        bindings.list("blysis:chest", context -> context.subject(Chest.class).items());
        bindings.list("blysis:sent", context -> context.subject(Sent.class).gifts());

        bindings.action("blysis:open-chest", context -> openChest(context.player()));
        bindings.action("blysis:open-sent", context -> openSent(context.player()));
        bindings.action("blysis:gifts", context -> toggleGifts(context.player()));
        bindings.action("blysis:claim", this::claim);
        bindings.action("blysis:take-back", this::takeBack);

        bindings.placeholder("thing", ChestMenus::thing);
        bindings.placeholder("count", ChestMenus::count);
        bindings.placeholder("sender", context -> senderOf(context));
        bindings.placeholder("to", context -> context.entry(SentGift.class).to());
        bindings.placeholder("gift-state", this::giftState);
        bindings.placeholder("receiving", this::receiving);
        bindings.placeholder("site", context -> this.siteAddress.get());
        bindings.placeholder("chest_icon", this::chestIcon);
    }

    /** Asks the site what waits for the player and opens the chest on it. */
    public void openChest(Player viewer) {
        Objects.requireNonNull(viewer, "viewer");
        PlayerRef ref = Replies.refOf(viewer);
        replies.ask(
                viewer,
                false,
                site -> Answer.of(() ->
                        new Chest(site.accounts().chest(ref), site.accounts().receiveGifts(ref, null))),
                chest -> replies.onPlayer(viewer, () -> windows.open(viewer, CHEST, chest)));
    }

    /** Asks the site for the gifts the player sent and opens the list. */
    public void openSent(Player viewer) {
        Objects.requireNonNull(viewer, "viewer");
        PlayerRef ref = Replies.refOf(viewer);
        replies.ask(
                viewer,
                false,
                site -> Answer.of(() -> new Sent(site.accounts().sentGifts(ref))),
                sent -> replies.onPlayer(viewer, () -> windows.open(viewer, SENT, sent)));
    }

    private void toggleGifts(Player viewer) {
        PlayerRef ref = Replies.refOf(viewer);
        replies.ask(
                viewer,
                false,
                site -> Answer.of(() -> {
                    boolean now = site.accounts().receiveGifts(ref, null);
                    boolean flipped = site.accounts().receiveGifts(ref, !now);
                    site.profiles().receiveGifts(ref, flipped);
                    return flipped;
                }),
                receiving -> {
                    messages.send(viewer, receiving ? Keys.GIFTS_ON : Keys.GIFTS_OFF);
                    openChest(viewer);
                });
    }

    /** Takes a thing out of the chest, or accepts a gift so it becomes one. */
    private void claim(MenuActionContext context) {
        Player viewer = context.player();
        ChestItem item = context.entry(ChestItem.class);
        PlayerRef ref = Replies.refOf(viewer);
        viewer.closeInventory();
        replies.ask(
                viewer,
                true,
                site -> site.once().run(key -> {
                    if (item.pendingGift()) {
                        site.accounts().activate(ref, item.id(), key);
                    } else {
                        site.accounts().claim(ref, item.id(), key);
                    }
                    return Boolean.TRUE;
                }),
                done -> {
                    messages.send(
                            viewer,
                            item.pendingGift() ? Keys.CHEST_ACCEPTED : Keys.CHEST_CLAIMED,
                            Text.placeholder("thing", item.title()));
                    openChest(viewer);
                });
    }

    /** Takes back a gift nobody accepted yet, after the player says yes. */
    private void takeBack(MenuActionContext context) {
        Player viewer = context.player();
        SentGift gift = context.entry(SentGift.class);
        if (!gift.waiting()) {
            messages.send(viewer, Keys.GIFT_ALREADY_TAKEN);
            return;
        }
        Sent sent = context.subject(Sent.class);
        windows.confirm(
                viewer, Keys.GIFT_TAKE_BACK_TITLE, () -> cancel(viewer, gift), () -> windows.open(viewer, SENT, sent));
    }

    private void cancel(Player viewer, SentGift gift) {
        PlayerRef ref = Replies.refOf(viewer);
        viewer.closeInventory();
        replies.ask(
                viewer,
                true,
                site -> site.once().run(key -> site.accounts().cancelGift(ref, gift.id(), key)),
                refunded -> {
                    replies.session().ifPresent(site -> site.profiles().forget(ref));
                    messages.send(
                            viewer,
                            Keys.GIFT_TAKEN_BACK,
                            Text.placeholder("thing", gift.title()),
                            Text.placeholder("amount", Amounts.shown(refunded)));
                    openSent(viewer);
                });
    }

    private static String thing(MenuContext context) {
        Object entry = context.entry().orElse(null);
        if (entry instanceof ChestItem item) {
            return item.title();
        }
        return entry instanceof SentGift gift ? gift.title() : "";
    }

    private static String count(MenuContext context) {
        Object entry = context.entry().orElse(null);
        if (entry instanceof ChestItem item) {
            return Integer.toString(item.quantity());
        }
        return entry instanceof SentGift gift ? Integer.toString(gift.quantity()) : "";
    }

    private static String senderOf(MenuContext context) {
        String from = context.entry(ChestItem.class).from();
        return from == null ? "" : from;
    }

    private String giftState(MenuContext context) {
        Object entry = context.entry().orElse(null);
        Keys said;
        if (entry instanceof ChestItem item) {
            said = item.pendingGift() ? Keys.STATE_GIFT_WAITING : Keys.STATE_READY;
        } else {
            said = entry instanceof SentGift gift && gift.waiting() ? Keys.STATE_GIFT_WAITING : Keys.STATE_ACCEPTED;
        }
        return words.of(context.viewer(), said);
    }

    private String receiving(MenuContext context) {
        boolean on = context.subjectRaw().orElse(null) instanceof Chest chest
                ? chest.receiving()
                : replies.session()
                        .flatMap(site -> site.profiles().lastKnown(Replies.refOf(context.viewer())))
                        .map(profile -> profile.receiveGifts())
                        .orElse(false);
        return words.of(context.viewer(), on ? Keys.STATE_ON : Keys.STATE_OFF);
    }

    private static @Nullable String chestItem(MenuContext context) {
        Object entry = context.entry().orElse(null);
        if (entry instanceof ChestItem item) {
            return item.icon();
        }
        return entry instanceof SentGift gift ? gift.icon() : null;
    }

    /** The item a chest row is drawn on: the site's icon when this server knows it, else the file's. */
    private String chestIcon(MenuContext context) {
        return words.productIcon(chestItem(context));
    }

    /** Every name the window files write that this plugin does not answer. */
    public List<String> problems(MenuBindings bindings) {
        return bindings.validate(windows.specs());
    }
}
