package com.uxplima.blysis.adapter.outbound;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.uxplima.blysis.application.port.Accounts;
import com.uxplima.blysis.application.port.SiteRefused;
import com.uxplima.blysis.application.port.SiteUnavailable;
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
 * The player's calls, over the same client and token as the delivery side. Each answer is read into a record;
 * an answer that is not the protocol's reads as the site being unavailable, never as a value.
 */
public final class HttpAccounts implements Accounts {

    /** The actor id of a change made from a console rather than by a player. */
    private static final String CONSOLE = "console";

    /** The longest name, code and answer the protocol carries; past them the site can only call the body malformed. */
    private static final int MOST_NAME = 64;

    private static final int LEAST_CODE = 4;
    private static final int MOST_CODE = 32;
    private static final int MOST_ANSWER = 200;

    private final HttpSite site;
    private final String instance;

    public HttpAccounts(HttpSite site, String instance) {
        this.site = Objects.requireNonNull(site, "site");
        this.instance = Objects.requireNonNull(instance, "instance");
    }

    @Override
    public Profile profile(PlayerRef player) {
        JsonObject answer = post("player/profile", withPlayer(player));
        return read("player/profile", () -> {
            if (!answer.get("linked").getAsBoolean()) {
                return Profile.UNLINKED;
            }
            JsonObject member = answer.getAsJsonObject("member");
            return new Profile(
                    true,
                    member.get("username").getAsString(),
                    member.get("balance").getAsBigDecimal(),
                    member.get("receiveGifts").getAsBoolean());
        });
    }

    @Override
    public LinkCode startLink(PlayerRef player) {
        JsonObject answer = post("link/start", withPlayer(player));
        return read(
                "link/start",
                () -> new LinkCode(
                        answer.get("code").getAsString(),
                        answer.get("url").getAsString(),
                        Instant.parse(answer.get("expiresAt").getAsString())));
    }

    @Override
    public String verifyLink(PlayerRef player, String code) {
        if (code.strip().length() < LEAST_CODE || code.strip().length() > MOST_CODE) {
            throw new SiteRefused(409, "code_invalid", "No code is that long or that short.");
        }
        JsonObject body = withPlayer(player);
        body.addProperty("code", code);
        JsonObject answer = post("link/verify", body);
        return read(
                "link/verify",
                () -> answer.getAsJsonObject("member").get("username").getAsString());
    }

    @Override
    public Optional<Catalog> catalog(@Nullable String knownVersion) {
        Optional<JsonObject> answer = site.getIfChanged("catalog", knownVersion);
        return answer.map(json -> read("catalog", () -> catalogOf(json)));
    }

    @Override
    public Purchase purchase(
            PlayerRef player,
            String productId,
            int quantity,
            Map<String, String> fields,
            @Nullable String recipient,
            String key) {
        JsonObject body = withPlayer(player);
        body.addProperty("productId", productId);
        body.addProperty("quantity", quantity);
        if (recipient != null) {
            requireName(recipient);
        }
        fields.forEach((field, typed) -> {
            if (typed.length() > MOST_ANSWER) {
                throw new SiteRefused(409, "field_invalid", "That answer is longer than any product takes.", field);
            }
        });
        JsonObject answers = new JsonObject();
        fields.forEach(answers::addProperty);
        body.add("fields", answers);
        if (recipient != null) {
            body.addProperty("recipient", recipient);
        }
        body.addProperty("idempotencyKey", key);
        JsonObject answer = post("store/purchase", body);
        return read(
                "store/purchase",
                () -> new Purchase(
                        answer.get("orderNumber").getAsString(),
                        answer.get("total").getAsBigDecimal(),
                        answer.get("balance").getAsBigDecimal(),
                        answer.get("gift").getAsBoolean()));
    }

    @Override
    public List<ChestItem> chest(PlayerRef player) {
        JsonObject answer = post("chest/list", withPlayer(player));
        return read("chest/list", () -> {
            List<ChestItem> items = new ArrayList<>();
            for (JsonElement element : array(answer, "items")) {
                JsonObject item = element.getAsJsonObject();
                items.add(new ChestItem(
                        item.get("id").getAsString(),
                        item.get("title").getAsString(),
                        text(item, "icon"),
                        item.get("quantity").getAsInt(),
                        "pending_gift".equals(text(item, "state")),
                        text(item, "from")));
            }
            return items;
        });
    }

    @Override
    public void claim(PlayerRef player, String itemId, String key) {
        post("chest/claim", withItem(player, itemId, key));
    }

    @Override
    public void activate(PlayerRef player, String itemId, String key) {
        post("chest/activate", withItem(player, itemId, key));
    }

    @Override
    public List<SentGift> sentGifts(PlayerRef player) {
        JsonObject answer = post("gifts/sent", withPlayer(player));
        return read("gifts/sent", () -> {
            List<SentGift> gifts = new ArrayList<>();
            for (JsonElement element : array(answer, "items")) {
                JsonObject gift = element.getAsJsonObject();
                gifts.add(new SentGift(
                        gift.get("id").getAsString(),
                        gift.get("title").getAsString(),
                        text(gift, "icon"),
                        gift.get("quantity").getAsInt(),
                        gift.get("to").getAsString(),
                        "pending_gift".equals(text(gift, "state"))));
            }
            return gifts;
        });
    }

    @Override
    public BigDecimal cancelGift(PlayerRef player, String itemId, String key) {
        JsonObject answer = post("gifts/cancel", withItem(player, itemId, key));
        return read("gifts/cancel", () -> answer.get("refunded").getAsBigDecimal());
    }

    @Override
    public boolean receiveGifts(PlayerRef player, @Nullable Boolean receive) {
        if (receive == null) {
            // The switch route only writes. The profile carries the switch, so a read is a profile read.
            return profile(player).receiveGifts();
        }
        JsonObject body = withPlayer(player);
        body.addProperty("receive", receive);
        JsonObject answer = post("gifts/receive", body);
        return read("gifts/receive", () -> answer.get("receiveGifts").getAsBoolean());
    }

    @Override
    public BigDecimal transfer(PlayerRef player, String to, BigDecimal amount, String key) {
        requireName(to);
        JsonObject body = withPlayer(player);
        body.addProperty("to", to);
        body.addProperty("amount", amount);
        body.addProperty("idempotencyKey", key);
        JsonObject answer = post("credits/transfer", body);
        return read("credits/transfer", () -> answer.get("balance").getAsBigDecimal());
    }

    @Override
    public BigDecimal adminCredits(
            PlayerRef actor, String target, String operation, @Nullable BigDecimal amount, String key) {
        requireName(target);
        JsonObject body = new JsonObject();
        body.addProperty("instance", instance);
        body.add("actor", actor(actor));
        body.addProperty("target", target);
        body.addProperty("operation", operation);
        if (amount != null) {
            body.addProperty("amount", amount);
        }
        body.addProperty("idempotencyKey", key);
        JsonObject answer = post("credits/admin", body);
        return read("credits/admin", () -> answer.get("balance").getAsBigDecimal());
    }

    @Override
    public List<TopEntry> top(String kind, String period, int limit) {
        JsonObject answer = site.call(
                "GET",
                "credits/top?kind=" + kind + "&period=" + period + "&limit=" + limit,
                null,
                site.requestTimeout());
        return read("credits/top", () -> {
            List<TopEntry> entries = new ArrayList<>();
            for (JsonElement element : array(answer, "entries")) {
                JsonObject entry = element.getAsJsonObject();
                entries.add(new TopEntry(
                        entry.get("rank").getAsInt(),
                        text(entry, "name"),
                        entry.get("amount").getAsBigDecimal()));
            }
            return entries;
        });
    }

    @Override
    public Goal goal() {
        JsonObject answer = site.call("GET", "community-goal", null, site.requestTimeout());
        return read("community-goal", () -> {
            if (!answer.get("active").getAsBoolean()) {
                return Goal.NONE;
            }
            return new Goal(
                    true,
                    answer.get("title").getAsString(),
                    answer.get("raised").getAsBigDecimal(),
                    answer.get("goal").getAsBigDecimal(),
                    answer.get("percent").getAsInt());
        });
    }

    @Override
    public Voucher createVoucher(PlayerRef player, BigDecimal amount, String key) {
        JsonObject body = withPlayer(player);
        body.addProperty("amount", amount);
        body.addProperty("idempotencyKey", key);
        JsonObject answer = post("vouchers/create", body);
        return read("vouchers/create", () -> voucherOf(answer));
    }

    @Override
    public Voucher giveVoucher(PlayerRef actor, BigDecimal amount, String key) {
        JsonObject body = new JsonObject();
        body.addProperty("instance", instance);
        body.add("actor", actor(actor));
        body.addProperty("amount", amount);
        body.addProperty("idempotencyKey", key);
        JsonObject answer = post("vouchers/give", body);
        return read("vouchers/give", () -> voucherOf(answer));
    }

    @Override
    public BigDecimal redeemVoucher(PlayerRef player, String voucherId) {
        JsonObject body = withPlayer(player);
        body.addProperty("voucherId", voucherId);
        JsonObject answer = post("vouchers/redeem", body);
        return read("vouchers/redeem", () -> answer.get("amount").getAsBigDecimal());
    }

    /** A name nobody can have is nobody, which is what a player is told. */
    private static void requireName(String typed) {
        if (typed.isBlank() || typed.length() > MOST_NAME) {
            throw new SiteRefused(409, "recipient_unknown", "Nobody has a name like that.");
        }
    }

    private static Voucher voucherOf(JsonObject answer) {
        return new Voucher(
                answer.get("voucherId").getAsString(), answer.get("amount").getAsBigDecimal());
    }

    private static Catalog catalogOf(JsonObject json) {
        List<Catalog.Category> categories = new ArrayList<>();
        for (JsonElement element : array(json, "categories")) {
            JsonObject category = element.getAsJsonObject();
            List<Catalog.Product> products = new ArrayList<>();
            for (JsonElement productElement : array(category, "products")) {
                products.add(productOf(productElement.getAsJsonObject()));
            }
            categories.add(new Catalog.Category(
                    category.get("id").getAsString(),
                    text(category, "parentId"),
                    category.get("title").getAsString(),
                    orEmpty(text(category, "description")),
                    text(category, "icon"),
                    category.has("order") ? category.get("order").getAsInt() : 0,
                    products));
        }
        return new Catalog(json.get("version").getAsString(), categories);
    }

    private static Catalog.Product productOf(JsonObject product) {
        List<String> servers = new ArrayList<>();
        for (JsonElement server : array(product, "servers")) {
            servers.add(server.getAsString());
        }
        List<Catalog.Field> fields = new ArrayList<>();
        for (JsonElement fieldElement : array(product, "fields")) {
            JsonObject field = fieldElement.getAsJsonObject();
            List<String> options = new ArrayList<>();
            for (JsonElement option : array(field, "options")) {
                options.add(option.getAsString());
            }
            fields.add(new Catalog.Field(
                    field.get("name").getAsString(),
                    field.get("label").getAsString(),
                    field.get("type").getAsString(),
                    field.get("required").getAsBoolean(),
                    text(field, "placeholder"),
                    options));
        }
        JsonElement stock = product.get("stock");
        return new Catalog.Product(
                product.get("id").getAsString(),
                product.get("title").getAsString(),
                orEmpty(text(product, "description")),
                text(product, "icon"),
                product.get("price").getAsBigDecimal(),
                product.get("giftable").getAsBoolean(),
                product.get("maxQuantity").getAsInt(),
                stock == null || stock.isJsonNull() ? null : stock.getAsInt(),
                servers,
                fields);
    }

    private JsonObject post(String path, JsonObject body) {
        return site.call("POST", path, body, site.requestTimeout());
    }

    private JsonObject withPlayer(PlayerRef player) {
        JsonObject body = new JsonObject();
        body.addProperty("instance", instance);
        body.add("player", player(player));
        return body;
    }

    private JsonObject withItem(PlayerRef player, String itemId, String key) {
        JsonObject body = withPlayer(player);
        body.addProperty("itemId", itemId);
        body.addProperty("idempotencyKey", key);
        return body;
    }

    /**
     * Who did a staff change. The site reads the name; the id is required, and the console has none in any
     * game, so it goes out as the console.
     */
    private static JsonObject actor(PlayerRef actor) {
        return player(actor.id() == null ? new PlayerRef(CONSOLE, actor.name()) : actor);
    }

    private static JsonObject player(PlayerRef player) {
        JsonObject json = new JsonObject();
        json.addProperty("id", player.id());
        json.addProperty("name", player.name());
        return json;
    }

    /** Reads an answer, or calls the site unavailable when the answer is not the protocol's. */
    private static <T> T read(String path, java.util.function.Supplier<T> reader) {
        try {
            return reader.get();
        } catch (RuntimeException malformed) {
            if (malformed instanceof SiteUnavailable unavailable) {
                throw unavailable;
            }
            throw new SiteUnavailable("the site's " + path + " answer is not the protocol's", malformed);
        }
    }

    private static JsonArray array(JsonObject object, String name) {
        JsonElement element = object.get(name);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
    }

    private static @Nullable String text(JsonObject object, String name) {
        JsonElement element = object.get(name);
        return element == null || element.isJsonNull() ? null : element.getAsString();
    }

    private static String orEmpty(@Nullable String value) {
        return value == null ? "" : value;
    }
}
