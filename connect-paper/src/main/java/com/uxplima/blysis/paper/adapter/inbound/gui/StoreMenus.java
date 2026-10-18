package com.uxplima.blysis.paper.adapter.inbound.gui;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.bukkit.entity.Player;

import com.uxplima.blysis.application.CatalogCache;
import com.uxplima.blysis.domain.account.Catalog;
import com.uxplima.blysis.paper.adapter.inbound.Replies;
import com.uxplima.blysis.paper.adapter.inbound.command.Amounts;
import com.uxplima.blysis.paper.application.message.Keys;
import com.uxplima.uxmlib.menu.binding.MenuBindings;
import com.uxplima.uxmlib.menu.runtime.MenuActionContext;
import com.uxplima.uxmlib.menu.runtime.MenuContext;
import com.uxplima.uxmlib.text.message.Messages;

/**
 * The store: its shelves, the products on a shelf, and the window that lists the answers a product offers.
 *
 * <p>The windows are files. This class gives them the rows and what a click does. Nothing here asks the site
 * while a window draws: the catalogue a window opens on is the one the plugin kept, and it is handed to the
 * window as its subject.
 */
public final class StoreMenus {

    /** The window of shelves and products. */
    public static final String STORE = "store";

    /** The window that lists the answers one question of a product allows. */
    public static final String OPTIONS = "options";

    /** Between the shelves of a path. A mark, not a word, so no language has to translate it. */
    private static final String STEP = " › ";

    private final Windows windows;
    private final Replies replies;
    private final Messages messages;
    private final Words words;

    public StoreMenus(MenuBindings bindings, Windows windows, Replies replies, Messages messages, Words words) {
        this.windows = Objects.requireNonNull(windows, "windows");
        this.replies = Objects.requireNonNull(replies, "replies");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.words = Objects.requireNonNull(words, "words");

        Objects.requireNonNull(bindings, "bindings");
        bindings.list("blysis:shelves", context -> context.subject(Shelf.class).shelves());
        bindings.list("blysis:products", context -> context.subject(Shelf.class).products());
        bindings.list(
                "blysis:options",
                context -> context.subject(FieldChoice.class).field().options());

        bindings.action("blysis:shelf", this::shelfClicked);
        bindings.action("blysis:back", this::back);
        bindings.action("blysis:open-store", context -> openStore(context.player()));

        bindings.placeholder("name", StoreMenus::name);
        bindings.placeholder("about", StoreMenus::about);
        bindings.placeholder("path", StoreMenus::path);
        bindings.placeholder(
                "price", context -> Amounts.shown(productOf(context).price()));
        bindings.placeholder("servers", this::servers);
        bindings.placeholder("stock", this::stock);
        bindings.placeholder(
                "question",
                context -> context.subject(FieldChoice.class).field().label());
        bindings.placeholder("choice", context -> context.entry(String.class));
        bindings.placeholder("shelf-item", this::shelfItem);
        bindings.placeholder(
                "product-item", context -> words.productIcon(productOf(context).icon()));
    }

    /** Opens the top of the store, once the catalogue and the player's balance are at hand. */
    public void openStore(Player viewer) {
        Objects.requireNonNull(viewer, "viewer");
        replies.session()
                .ifPresentOrElse(
                        site -> replies.onAsync(() -> {
                            CatalogCache.View view = site.catalog().view();
                            if (view.fetchedAt().isEmpty()) {
                                site.catalog().refresh();
                                view = site.catalog().view();
                            }
                            // The balance a purchase window shows is the one kept, so it is asked now, while nothing
                            // waits on it.
                            site.profiles().of(Replies.refOf(viewer));
                            Shelf top = new Shelf(view.catalog(), null, view.stale());
                            replies.onPlayer(viewer, () -> openOn(viewer, top));
                        }),
                        () -> messages.send(viewer, Keys.NOT_CONNECTED));
    }

    /** Opens the store on one shelf, or says why there is nothing to show. */
    void openOn(Player viewer, Shelf shelf) {
        if (shelf.catalog().categories().isEmpty()) {
            messages.send(viewer, shelf.stale() ? Keys.SITE_AWAY : Keys.STORE_EMPTY);
            return;
        }
        if (shelf.stale() && shelf.category() == null) {
            messages.send(viewer, Keys.STORE_STALE);
        }
        windows.open(viewer, STORE, shelf);
    }

    private void shelfClicked(MenuActionContext context) {
        if (context.context().entry().orElse(null) instanceof Catalog.Category category) {
            openOn(context.player(), context.subject(Shelf.class).on(category));
        }
    }

    /** One step up: the answers to the basket, the basket to its shelf, a shelf to the one above it. */
    private void back(MenuActionContext context) {
        Player viewer = context.player();
        Object subject = context.context().subjectRaw().orElse(null);
        switch (subject) {
            case FieldChoice choice -> windows.open(viewer, Purchasing.BASKET, choice.basket());
            case Basket basket ->
                openOn(
                        viewer,
                        basket.shelf()
                                .on(basket.shelf().shelfOf(basket.product()).orElse(null)));
            case Shelf shelf
            when shelf.category() != null ->
                openOn(viewer, shelf.on(shelf.parent().orElse(null)));
            case null, default -> viewer.closeInventory();
        }
    }

    /** The name of the row a tile is drawn for, or of what the window is about. */
    private static String name(MenuContext context) {
        Object entry = context.entry().orElse(null);
        if (entry instanceof Catalog.Category category) {
            return category.title();
        }
        if (entry instanceof Catalog.Product product) {
            return product.title();
        }
        return productOf(context).title();
    }

    private static String about(MenuContext context) {
        Object entry = context.entry().orElse(null);
        if (entry instanceof Catalog.Category category) {
            return category.description();
        }
        return productOf(context).description();
    }

    /** Where the player is: the shelves from the top down, each after a step mark. */
    private static String path(MenuContext context) {
        Object subject = context.subjectRaw().orElse(null);
        Shelf shelf =
                switch (subject) {
                    case Shelf here -> here;
                    case Basket basket ->
                        basket.shelf()
                                .on(basket.shelf().shelfOf(basket.product()).orElse(null));
                    case FieldChoice choice -> choice.basket().shelf();
                    case null, default -> null;
                };
        if (shelf == null) {
            return "";
        }
        List<Catalog.Category> steps = shelf.path();
        return steps.stream().map(step -> STEP + step.title()).collect(Collectors.joining());
    }

    private String servers(MenuContext context) {
        List<String> servers = productOf(context).servers();
        return servers.isEmpty() ? words.of(context.viewer(), Keys.STORE_EVERY_SERVER) : String.join(", ", servers);
    }

    private String stock(MenuContext context) {
        Integer stock = productOf(context).stock();
        return stock == null ? words.of(context.viewer(), Keys.STORE_UNLIMITED) : Integer.toString(stock);
    }

    private String shelfItem(MenuContext context) {
        Object entry = context.entry().orElse(null);
        return words.shelfIcon(entry instanceof Catalog.Category category ? category.icon() : null);
    }

    /** The product a tile is drawn for, or the one the window is about. */
    static Catalog.Product productOf(MenuContext context) {
        if (context.entry().orElse(null) instanceof Catalog.Product product) {
            return product;
        }
        Object subject = context.subjectRaw().orElse(null);
        if (subject instanceof FieldChoice choice) {
            return choice.basket().product();
        }
        return context.subject(Basket.class).product();
    }
}
