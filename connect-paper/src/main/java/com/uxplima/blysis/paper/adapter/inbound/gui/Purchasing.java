package com.uxplima.blysis.paper.adapter.inbound.gui;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import com.uxplima.blysis.application.Answer;
import com.uxplima.blysis.application.SiteSession;
import com.uxplima.blysis.domain.PlayerRef;
import com.uxplima.blysis.domain.account.Amounts;
import com.uxplima.blysis.domain.account.Catalog;
import com.uxplima.blysis.domain.account.Profile;
import com.uxplima.blysis.domain.account.Purchase;
import com.uxplima.blysis.domain.account.Quote;
import com.uxplima.blysis.paper.adapter.inbound.Replies;
import com.uxplima.blysis.paper.application.message.Keys;
import com.uxplima.uxmlib.gui.input.TextInput;
import com.uxplima.uxmlib.menu.binding.MenuBindings;
import com.uxplima.uxmlib.menu.runtime.MenuActionContext;
import com.uxplima.uxmlib.menu.runtime.MenuContext;
import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.message.Messages;
import org.jspecify.annotations.Nullable;

/**
 * The purchase window: how many, for whom, the questions the product asks, and the purchase itself.
 *
 * <p>Every button opens the window again on a new basket, so the confirm tile always sums up one finished
 * state. The site prices, checks and charges; this window only gathers what the site needs to be asked.
 */
public final class Purchasing {

    /** The purchase window. */
    public static final String BASKET = "basket";

    /** The window that offers the players online as the one a gift is for. */
    public static final String RECIPIENTS = "recipients";

    /** The text input points, each with its own mode in {@code text-input.conf}. */
    static final String QUANTITY = "quantity";

    static final String RECIPIENT = "recipient";
    static final String FIELD = "field";

    private static final java.time.Duration HELD = java.time.Duration.ofMinutes(1);

    private final Windows windows;
    private final Replies replies;
    private final Messages messages;
    private final TextInput input;
    private final Words words;
    // When each player's purchase started. A purchase that never reached the site, because the link dropped
    // between the check and the call, would hold its place for ever, so a place older than a minute is free.
    private final Map<UUID, Long> buying = new ConcurrentHashMap<>();

    public Purchasing(
            MenuBindings bindings, Windows windows, Replies replies, Messages messages, TextInput input, Words words) {
        this.windows = Objects.requireNonNull(windows, "windows");
        this.replies = Objects.requireNonNull(replies, "replies");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.input = Objects.requireNonNull(input, "input");
        this.words = Objects.requireNonNull(words, "words");

        Objects.requireNonNull(bindings, "bindings");
        bindings.action("blysis:product", this::productClicked);
        bindings.action("blysis:more", context -> move(context, step(context)));
        bindings.action("blysis:less", context -> move(context, -step(context)));
        bindings.action("blysis:amount", this::askQuantity);
        bindings.list("blysis:people", Purchasing::people);
        bindings.action("blysis:recipient", this::chooseRecipient);
        bindings.action("blysis:type-name", this::askRecipient);
        bindings.action(
                "blysis:pick",
                context -> reopen(
                        context.player(),
                        basketOf(context)
                                .forRecipient(context.entry(Someone.class).name())));
        bindings.action("blysis:basket", context -> reopen(context.player(), basketOf(context)));
        bindings.placeholder("someone", context -> context.entry(Someone.class).name());
        bindings.action(
                "blysis:self",
                context -> reopen(context.player(), basketOf(context).forRecipient(null)));
        bindings.action("blysis:confirm", context -> proceed(context.player(), basketOf(context)));
        bindings.action(
                "blysis:describe",
                context -> describe(context.player(), basketOf(context).product()));
        bindings.action("blysis:choose", this::chosen);

        bindings.placeholder(
                "quantity", context -> Integer.toString(basketOf(context).quantity()));
        bindings.placeholder(
                "most", context -> Integer.toString(basketOf(context).most()));
        bindings.placeholder("total", context -> Amounts.shown(basketOf(context).total()));
        bindings.placeholder("recipient", this::recipient);
        bindings.placeholder("balance", context -> shownOrNothing(balance(context)));
        bindings.placeholder(
                "after", context -> afterOf(balance(context), basketOf(context).total()));
        bindings.placeholder(
                "short", context -> shortOf(balance(context), basketOf(context).total()));
    }

    private void productClicked(MenuActionContext context) {
        if (context.context().entry().orElse(null) instanceof Catalog.Product product) {
            reopen(context.player(), Basket.of(context.subject(Shelf.class), product));
        }
    }

    private void move(MenuActionContext context, int step) {
        reopen(context.player(), basketOf(context).movedBy(step));
    }

    private void askQuantity(MenuActionContext context) {
        Player viewer = context.player();
        Basket basket = basketOf(context);
        prompt(
                viewer,
                QUANTITY,
                messages.render(viewer, Keys.INPUT_QUANTITY),
                basket,
                typed -> basket.typed(typed).ifPresentOrElse(chosen -> reopen(viewer, chosen), () -> {
                    messages.send(viewer, Keys.REFUSED_QUANTITY_INVALID);
                    reopen(viewer, basket);
                }));
    }

    /** Offers the players online, with a button to type a name for a member who is not. */
    private void chooseRecipient(MenuActionContext context) {
        Basket basket = basketOf(context);
        if (!basket.product().giftable()) {
            messages.send(context.player(), Keys.STORE_NOT_GIFTABLE);
            return;
        }
        windows.open(context.player(), RECIPIENTS, basket);
    }

    private static java.util.List<Someone> people(MenuContext context) {
        Player viewer = context.viewer();
        return viewer.getServer().getOnlinePlayers().stream()
                .filter(player -> !player.getUniqueId().equals(viewer.getUniqueId()))
                .filter(viewer::canSee)
                .map(player -> new Someone(player.getName()))
                .sorted(java.util.Comparator.comparing(Someone::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private void askRecipient(MenuActionContext context) {
        Player viewer = context.player();
        Basket basket = basketOf(context);
        if (!basket.product().giftable()) {
            messages.send(viewer, Keys.STORE_NOT_GIFTABLE);
            return;
        }
        prompt(
                viewer,
                RECIPIENT,
                messages.render(viewer, Keys.INPUT_RECIPIENT),
                basket,
                typed -> reopen(viewer, basket.forRecipient(typed)));
    }

    /** Asks the next question the product has, or buys when every one is answered. */
    void proceed(Player viewer, Basket basket) {
        Optional<Catalog.Field> next = basket.nextQuestion();
        if (next.isEmpty()) {
            buy(viewer, basket);
            return;
        }
        Catalog.Field field = next.get();
        if (!field.options().isEmpty()) {
            windows.open(viewer, StoreMenus.OPTIONS, new FieldChoice(basket, field));
            return;
        }
        Component label = messages.render(viewer, Keys.INPUT_FIELD, Text.placeholder("field", field.label()));
        prompt(viewer, FIELD, label, basket, typed -> answered(viewer, basket, field, typed));
    }

    private void answered(Player viewer, Basket basket, Catalog.Field field, String typed) {
        if (typed.isBlank() && field.required()) {
            messages.send(viewer, Keys.REFUSED_FIELD_INVALID, Text.placeholder("field", field.label()));
            reopen(viewer, basket);
            return;
        }
        proceed(viewer, basket.answered(field.name(), typed.strip()));
    }

    private void chosen(MenuActionContext context) {
        FieldChoice choice = context.subject(FieldChoice.class);
        proceed(context.player(), choice.basket().answered(choice.field().name(), context.entry(String.class)));
    }

    /** One purchase in flight per player, so a second click never charges twice. */
    private void buy(Player viewer, Basket basket) {
        if (!claimed(viewer.getUniqueId())) {
            return;
        }
        viewer.closeInventory();
        PlayerRef ref = Replies.refOf(viewer);
        Catalog.Product product = basket.product();
        replies.ask(
                viewer,
                true,
                site -> {
                    try {
                        return site.once()
                                .run(key -> site.accounts()
                                        .purchase(
                                                ref,
                                                product.id(),
                                                basket.quantity(),
                                                basket.answers(),
                                                basket.recipient(),
                                                key));
                    } finally {
                        buying.remove(viewer.getUniqueId());
                    }
                },
                purchase -> bought(viewer, ref, basket, purchase));
    }

    private boolean claimed(UUID player) {
        long now = System.nanoTime();
        long[] taken = {0};
        buying.compute(player, (id, since) -> {
            if (since != null && now - since < HELD.toNanos()) {
                return since;
            }
            taken[0] = 1;
            return now;
        });
        if (taken[0] == 0) {
            return false;
        }
        if (replies.session().filter(site -> site.reachable()).isEmpty()) {
            buying.remove(player);
        }
        return true;
    }

    private void bought(Player viewer, PlayerRef ref, Basket basket, Purchase purchase) {
        replies.session().ifPresent(site -> site.profiles().balance(ref, purchase.balance()));
        messages.send(
                viewer,
                purchase.gift() ? Keys.STORE_GIFTED : Keys.STORE_BOUGHT,
                Text.placeholder("product", basket.product().title()),
                Text.placeholder("quantity", Integer.toString(basket.quantity())),
                Text.placeholder("to", basket.recipient() == null ? "" : basket.recipient()),
                Text.placeholder("total", Amounts.shown(purchase.total())),
                Text.placeholder("balance", Amounts.shown(purchase.balance())),
                Text.placeholder("order", purchase.orderNumber()));
    }

    /** Asks for a line of text in the mode the operator chose, and comes back to the basket on a cancel. */
    private void prompt(Player viewer, String point, Component label, Basket basket, Consumer<String> then) {
        viewer.closeInventory();
        input.promptResolved(viewer, point, label, null, then, () -> reopen(viewer, basket));
    }

    /**
     * Opens the purchase window on this basket, priced for this player. The catalogue is one list for every
     * player, so its price leaves out what this member's own purchases and the quantity take off; the site's
     * quote is asked first, and the catalogue's price stands when the site cannot answer or the player has no
     * account there.
     */
    private void reopen(Player viewer, Basket basket) {
        PlayerRef ref = Replies.refOf(viewer);
        Optional<SiteSession> site = replies.session().filter(SiteSession::reachable);
        boolean unlinked = site.flatMap(open -> open.profiles().lastKnown(ref))
                .filter(profile -> !profile.linked())
                .isPresent();
        if (site.isEmpty() || unlinked) {
            windows.open(viewer, BASKET, basket);
            return;
        }
        replies.onAsync(() -> {
            Answer<Quote> quote = Answer.of(() ->
                    site.get().accounts().quote(ref, basket.product().id(), basket.quantity(), basket.recipient()));
            Basket shown = quote instanceof Answer.Given<Quote> given ? basket.quoted(given.value()) : basket;
            replies.onPlayer(viewer, () -> windows.open(viewer, BASKET, shown));
        });
    }

    private String recipient(MenuContext context) {
        String name = basketOf(context).recipient();
        return name == null ? words.of(context.viewer(), Keys.STORE_YOURSELF) : name;
    }

    private Optional<BigDecimal> balance(MenuContext context) {
        PlayerRef ref = Replies.refOf(context.viewer());
        return replies.session()
                .flatMap(site -> site.profiles().lastKnown(ref))
                .filter(Profile::linked)
                .map(Profile::balance);
    }

    /** The balance a purchase leaves, or nothing when the balance is unknown or does not cover it. */
    static String afterOf(Optional<BigDecimal> held, BigDecimal total) {
        return shownOrNothing(held.map(balance -> balance.subtract(total)).filter(left -> left.signum() >= 0));
    }

    /**
     * How much a purchase is missing, or nothing when it is covered. A balance below zero is not one a member
     * can have, so the tile says what is short instead.
     */
    static String shortOf(Optional<BigDecimal> held, BigDecimal total) {
        return shownOrNothing(held.map(balance -> total.subtract(balance)).filter(missing -> missing.signum() > 0));
    }

    /** An amount as it reads, or nothing, which leaves its row off the tile rather than printing a question mark. */
    static String shownOrNothing(Optional<BigDecimal> amount) {
        return amount.map(Amounts::shown).orElse("");
    }

    private static int step(MenuActionContext context) {
        try {
            return Math.max(1, Integer.parseInt(context.arg()));
        } catch (NumberFormatException notANumber) {
            return 1;
        }
    }

    /** The whole of a product's words, in chat: what a Bedrock form's button has no room for. */
    private void describe(Player viewer, Catalog.Product product) {
        if (product.description().isBlank()) {
            messages.send(viewer, Keys.STORE_UNDESCRIBED, Text.placeholder("product", product.title()));
        } else {
            messages.send(
                    viewer,
                    Keys.STORE_DESCRIBED,
                    Text.placeholder("product", product.title()),
                    Text.placeholder("description", product.description()));
        }
    }

    private static Basket basketOf(MenuActionContext context) {
        return basketOf(context.context());
    }

    private static Basket basketOf(MenuContext context) {
        @Nullable Object subject = context.subjectRaw().orElse(null);
        if (subject instanceof FieldChoice choice) {
            return choice.basket();
        }
        return context.subject(Basket.class);
    }
}
