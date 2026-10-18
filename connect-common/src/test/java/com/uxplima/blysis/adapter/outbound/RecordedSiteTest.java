package com.uxplima.blysis.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import com.uxplima.blysis.application.port.SiteRefused;
import com.uxplima.blysis.domain.JobResult;
import com.uxplima.blysis.domain.Outcome;
import com.uxplima.blysis.domain.PlayerRef;
import com.uxplima.blysis.domain.StatusPush;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Every exchange the site's conformance run recorded, answered again by a fake site, read by this client.
 *
 * <p>The recordings are what a real site said, so a client that reads them the way the site meant them reads the
 * site. An answer reads as its value; a refusal reads as a {@link SiteRefused} with the site's status and code.
 */
final class RecordedSiteTest {

    private static final PlayerRef SOMEONE = new PlayerRef("069a79f4-44e9-4726-a5be-fca90e38aaf5", "Notch");

    private @Nullable HttpServer server;
    private final AtomicReference<JsonObject> answering = new AtomicReference<>(new JsonObject());
    private final AtomicReference<String> asked = new AtomicReference<>("");

    @AfterEach
    void stop() {
        HttpServer running = server;
        if (running != null) {
            running.stop(0);
        }
    }

    @TestFactory
    Stream<DynamicTest> everyRecordedExchangeReads() throws IOException, URISyntaxException {
        URL folder = Objects.requireNonNull(getClass().getResource("/conformance"), "no recordings");
        List<Path> files;
        try (Stream<Path> listed = Files.list(Path.of(folder.toURI()))) {
            files = listed.filter(file -> file.toString().endsWith(".json"))
                    .sorted()
                    .toList();
        }
        assertThat(files).as("the recordings").hasSizeGreaterThanOrEqualTo(18);
        List<DynamicTest> tests = new ArrayList<>();
        for (Path file : files) {
            JsonObject recording =
                    JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            String scenario = recording.get("scenario").getAsString();
            JsonArray exchanges = recording.getAsJsonArray("exchanges");
            for (int at = 0; at < exchanges.size(); at++) {
                JsonObject exchange = exchanges.get(at).getAsJsonObject();
                String name = scenario + " #" + at + " " + exchange.get("path").getAsString() + " "
                        + exchange.get("status").getAsInt();
                tests.add(DynamicTest.dynamicTest(name, () -> replay(exchange)));
            }
        }
        return tests.stream();
    }

    private void replay(JsonObject exchange) throws IOException {
        Client client = client();
        answering.set(exchange);
        int status = exchange.get("status").getAsInt();
        String path = exchange.get("path").getAsString();
        JsonObject request = objectOr(exchange.get("request"));
        JsonObject response = objectOr(exchange.get("response"));
        if (status >= 400) {
            assertThatThrownBy(() -> call(client, path, request, status))
                    .isInstanceOfSatisfying(SiteRefused.class, refused -> {
                        assertThat(refused.status()).isEqualTo(status);
                        assertThat(refused.code())
                                .isEqualTo(response.get("code").getAsString());
                    });
        } else {
            Object read = call(client, path, request, status);
            assertThat(read).as("what %s reads", path).isEqualTo(expected(path, response, status));
        }
        assertThat(asked.get()).as("the path the client asked").isEqualTo(path);
    }

    /** The one call of this client that asks the recorded path, with the values the recording sent. */
    private static Object call(Client client, String path, JsonObject request, int status) {
        HttpSite site = client.site();
        HttpAccounts accounts = client.accounts();
        PlayerRef player = player(request, "player");
        String key = textOr(request, "idempotencyKey", "a-key-of-length");
        String route = path.contains("?") ? path.substring(0, path.indexOf('?')) : path;
        return switch (route) {
            case "hello" ->
                site.hello("Paper", "26.2", textOr(request, "role", "server")).serverName();
            case "status" -> {
                site.status(new StatusPush(1, 20, List.of(player), "26.2", 20, 1));
                yield "sent";
            }
            case "poll" -> {
                var poll = site.poll(text(request, "eventCursor"));
                yield poll.jobs().size() + " " + poll.eventCursor();
            }
            case "jobs/report" -> site.report(results(request));
            case "catalog" ->
                accounts.catalog(status == 304 ? "known" : null)
                        .map(catalog -> catalog.categories().size())
                        .orElse(-1);
            case "player/profile" -> accounts.profile(player).linked();
            case "link/start" -> accounts.startLink(player).code();
            case "link/verify" -> accounts.verifyLink(player, textOr(request, "code", ""));
            case "credits/admin" ->
                accounts.adminCredits(
                        player(request, "actor"),
                        textOr(request, "target", ""),
                        textOr(request, "operation", "show"),
                        amount(request),
                        key);
            case "credits/transfer" -> accounts.transfer(player, textOr(request, "to", ""), amountOrOne(request), key);
            case "credits/top" -> {
                Map<String, String> query = query(path);
                yield accounts.top(
                                query.getOrDefault("kind", ""),
                                query.getOrDefault("period", ""),
                                Integer.parseInt(query.getOrDefault("limit", "10")))
                        .size();
            }
            case "community-goal" -> accounts.goal().active();
            case "store/purchase" -> {
                var bought = accounts.purchase(
                        player,
                        textOr(request, "productId", ""),
                        request.has("quantity") ? request.get("quantity").getAsInt() : 1,
                        fields(request),
                        text(request, "recipient"),
                        key);
                yield bought.orderNumber() + " " + bought.gift();
            }
            case "chest/list" -> accounts.chest(player).size();
            case "chest/claim" -> {
                accounts.claim(player, textOr(request, "itemId", ""), key);
                yield "ok";
            }
            case "chest/activate" -> {
                accounts.activate(player, textOr(request, "itemId", ""), key);
                yield "ok";
            }
            case "gifts/sent" -> accounts.sentGifts(player).size();
            case "gifts/cancel" -> accounts.cancelGift(player, textOr(request, "itemId", ""), key);
            case "gifts/receive" ->
                accounts.receiveGifts(player, request.get("receive").getAsBoolean());
            case "vouchers/create" ->
                accounts.createVoucher(player, amountOrOne(request), key).id();
            case "vouchers/give" ->
                accounts.giveVoucher(player(request, "actor"), amountOrOne(request), key)
                        .id();
            case "vouchers/redeem" -> accounts.redeemVoucher(player, textOr(request, "voucherId", ""));
            default -> throw new AssertionError("no call of this client asks " + path);
        };
    }

    /** What the client should make of an answer: the same values, read off the recording. */
    private static Object expected(String path, JsonObject response, int status) {
        String route = path.contains("?") ? path.substring(0, path.indexOf('?')) : path;
        return switch (route) {
            case "hello" -> response.getAsJsonObject("server").get("name").getAsString();
            case "status" -> "sent";
            case "poll" ->
                response.getAsJsonArray("jobs").size() + " "
                        + response.get("eventCursor").getAsString();
            case "jobs/report" -> strings(response.getAsJsonArray("accepted"));
            case "catalog" ->
                status == 304 ? -1 : response.getAsJsonArray("categories").size();
            case "player/profile" -> response.get("linked").getAsBoolean();
            case "link/start" -> response.get("code").getAsString();
            case "link/verify" ->
                response.getAsJsonObject("member").get("username").getAsString();
            case "credits/admin", "credits/transfer" -> response.get("balance").getAsBigDecimal();
            case "credits/top" -> response.getAsJsonArray("entries").size();
            case "community-goal" -> response.get("active").getAsBoolean();
            case "store/purchase" ->
                response.get("orderNumber").getAsString() + " "
                        + response.get("gift").getAsBoolean();
            case "chest/list", "gifts/sent" -> response.getAsJsonArray("items").size();
            case "chest/claim", "chest/activate" -> "ok";
            case "gifts/cancel" -> response.get("refunded").getAsBigDecimal();
            case "gifts/receive" -> response.get("receiveGifts").getAsBoolean();
            case "vouchers/create", "vouchers/give" -> response.get("voucherId").getAsString();
            case "vouchers/redeem" -> response.get("amount").getAsBigDecimal();
            default -> throw new AssertionError("no answer is known for " + path);
        };
    }

    private record Client(HttpSite site, HttpAccounts accounts) {}

    private Client client() throws IOException {
        HttpServer running = server;
        if (running == null) {
            running = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            running.createContext("/api/v1/game-connect/", exchange -> {
                String route = exchange.getRequestURI().getPath().substring("/api/v1/game-connect/".length());
                String query = exchange.getRequestURI().getRawQuery();
                asked.set(query == null ? route : route + "?" + query);
                exchange.getRequestBody().readAllBytes();
                JsonObject recorded = Objects.requireNonNull(answering.get(), "nothing to answer");
                int status = recorded.get("status").getAsInt();
                JsonElement body = recorded.get("response");
                byte[] bytes = body == null || body.isJsonNull()
                        ? new byte[0]
                        : body.toString().getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
                if (bytes.length > 0) {
                    try (OutputStream out = exchange.getResponseBody()) {
                        out.write(bytes);
                    }
                }
                exchange.close();
            });
            running.start();
            server = running;
        }
        String address = "http://127.0.0.1:" + running.getAddress().getPort();
        HttpSite site = new HttpSite(
                HttpSite.client(Duration.ofSeconds(2), address),
                new HttpSite.Connection(
                        address, "gc_" + "a".repeat(43), "inst-1", "c", "1", "minecraft", Duration.ofSeconds(5)));
        return new Client(site, new HttpAccounts(site, "inst-1"));
    }

    private static JsonObject objectOr(@Nullable JsonElement element) {
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
    }

    private static PlayerRef player(JsonObject request, String name) {
        JsonElement element = request.get(name);
        if (element == null || !element.isJsonObject()) {
            return SOMEONE;
        }
        JsonObject player = element.getAsJsonObject();
        return new PlayerRef(text(player, "id"), textOr(player, "name", SOMEONE.name()));
    }

    private static @Nullable String text(JsonObject object, String name) {
        JsonElement element = object.get(name);
        return element == null || element.isJsonNull() ? null : element.getAsString();
    }

    private static String textOr(JsonObject object, String name, String fallback) {
        String text = text(object, name);
        return text == null ? fallback : text;
    }

    private static @Nullable BigDecimal amount(JsonObject request) {
        JsonElement element = request.get("amount");
        return element == null || element.isJsonNull() ? null : element.getAsBigDecimal();
    }

    private static BigDecimal amountOrOne(JsonObject request) {
        BigDecimal amount = amount(request);
        return amount == null ? BigDecimal.ONE : amount;
    }

    private static Map<String, String> fields(JsonObject request) {
        Map<String, String> fields = new HashMap<>();
        objectOr(request.get("fields"))
                .entrySet()
                .forEach(entry -> fields.put(entry.getKey(), entry.getValue().getAsString()));
        return fields;
    }

    private static List<JobResult> results(JsonObject request) {
        List<JobResult> results = new ArrayList<>();
        for (JsonElement element : request.getAsJsonArray("results")) {
            JsonObject result = element.getAsJsonObject();
            results.add(new JobResult(result.get("id").getAsString(), Outcome.DONE, null));
        }
        return results;
    }

    private static Set<String> strings(JsonArray array) {
        Set<String> out = new HashSet<>();
        array.forEach(element -> out.add(element.getAsString()));
        return out;
    }

    private static Map<String, String> query(String path) {
        Map<String, String> query = new HashMap<>();
        for (String pair : path.substring(path.indexOf('?') + 1).split("&", -1)) {
            int at = pair.indexOf('=');
            query.put(pair.substring(0, at), pair.substring(at + 1));
        }
        return query;
    }
}
