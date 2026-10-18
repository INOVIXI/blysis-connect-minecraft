package com.uxplima.blysis.paper.adapter.inbound.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.blysis.domain.account.Catalog;
import org.jspecify.annotations.Nullable;

/**
 * What a store window is about: the whole store, or one shelf of it, as the catalogue stood when the window
 * opened. A window never asks the site while it draws.
 */
public record Shelf(Catalog catalog, @Nullable String at, boolean stale) {

    /** A site that writes a shelf under itself must not hang a window. */
    private static final int MAX_DEPTH = 16;

    public Shelf {
        Objects.requireNonNull(catalog, "catalog");
    }

    /** The shelf this window is on, or null at the top of the store. */
    public Catalog.@Nullable Category category() {
        String id = at;
        return id == null ? null : catalog.category(id).orElse(null);
    }

    /** The shelves on this one, or the shelves at the top. */
    public List<Catalog.Category> shelves() {
        Catalog.Category here = category();
        return here == null ? catalog.roots() : catalog.childrenOf(here.id());
    }

    /** The products on this shelf. The top of the store holds shelves only. */
    public List<Catalog.Product> products() {
        Catalog.Category here = category();
        return here == null ? List.of() : here.products();
    }

    /** The shelves from the top down to this one, for the path a title shows. */
    public List<Catalog.Category> path() {
        List<Catalog.Category> path = new ArrayList<>();
        Catalog.Category step = category();
        while (step != null && path.size() < MAX_DEPTH) {
            path.addFirst(step);
            String parent = step.parentId();
            step = parent == null ? null : catalog.category(parent).orElse(null);
        }
        return List.copyOf(path);
    }

    /** The shelf above this one, if it has one. */
    public Optional<Catalog.Category> parent() {
        Catalog.Category here = category();
        String parent = here == null ? null : here.parentId();
        return parent == null ? Optional.empty() : catalog.category(parent);
    }

    /** The same catalogue, on another shelf. */
    public Shelf on(Catalog.@Nullable Category other) {
        return new Shelf(catalog, other == null ? null : other.id(), stale);
    }

    /** The shelf a product is on. */
    public Optional<Catalog.Category> shelfOf(Catalog.Product product) {
        return catalog.categories().stream()
                .filter(category -> category.products().stream().anyMatch(p -> p.id().equals(product.id())))
                .findFirst();
    }
}
