package com.uxplima.blysis.domain.account;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/** What the site sells in game: shelves of products, as the operator marked them visible in game. */
public record Catalog(String version, List<Category> categories) {

    /** A catalogue with nothing on it, what a site without a store answers. */
    public static final Catalog EMPTY = new Catalog("", List.of());

    public Catalog {
        Objects.requireNonNull(version, "version");
        categories = List.copyOf(categories);
    }

    /** The shelves at the top, in the operator's order. */
    public List<Category> roots() {
        return categories.stream()
                .filter(category -> category.parentId() == null)
                .sorted((a, b) -> Integer.compare(a.order(), b.order()))
                .toList();
    }

    /** The shelves under one shelf, in the operator's order. */
    public List<Category> childrenOf(String id) {
        return categories.stream()
                .filter(category -> id.equals(category.parentId()))
                .sorted((a, b) -> Integer.compare(a.order(), b.order()))
                .toList();
    }

    public Optional<Category> category(String id) {
        return categories.stream().filter(category -> category.id().equals(id)).findFirst();
    }

    public Optional<Product> product(String id) {
        return categories.stream()
                .flatMap(category -> category.products().stream())
                .filter(product -> product.id().equals(id))
                .findFirst();
    }

    /** One shelf. */
    public record Category(
            String id,
            @Nullable String parentId,
            String title,
            String description,
            @Nullable String icon,
            int order,
            List<Product> products) {

        public Category {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(description, "description");
            products = List.copyOf(products);
        }
    }

    /** One thing for sale, priced in credits. */
    public record Product(
            String id,
            String title,
            String description,
            @Nullable String icon,
            BigDecimal price,
            boolean giftable,
            int maxQuantity,
            @Nullable Integer stock,
            List<String> servers,
            List<Field> fields) {

        public Product {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(description, "description");
            Objects.requireNonNull(price, "price");
            servers = List.copyOf(servers);
            fields = List.copyOf(fields);
        }
    }

    /** A question a product asks the buyer, such as the colour of a name. */
    public record Field(
            String name,
            String label,
            String type,
            boolean required,
            @Nullable String placeholder,
            List<String> options) {

        public Field {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(type, "type");
            options = List.copyOf(options);
        }
    }
}
