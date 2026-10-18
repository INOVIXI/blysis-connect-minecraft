package com.uxplima.blysis.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import com.uxplima.blysis.application.port.SiteRefused;
import com.uxplima.blysis.domain.PlayerRef;
import com.uxplima.blysis.domain.account.Catalog;
import com.uxplima.blysis.domain.account.Profile;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The player's calls against a site on this machine, answering as the protocol page says. */
final class HttpAccountsTest {

    private static final PlayerRef NOTCH = new PlayerRef("069a79f4-44e9-4726-a5be-fca90e38aaf5", "Notch");

    private @Nullable HttpServer server;
    private final AtomicReference<JsonObject> lastBody = new AtomicReference<>(new JsonObject());
    private final AtomicReference<String> lastMatch = new AtomicReference<>("");
    private final AtomicReference<String> lastPath = new AtomicReference<>("");

    @AfterEach
    void stop() {
        HttpServer running = server;
        if (running != null) {
            running.stop(0);
        }
    }

    private HttpAccounts accounts(int status, String answer) throws IOException {
        HttpServer started = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        started.createContext("/api/v1/game-connect/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            lastBody.set(
                    body.isBlank()
                            ? new JsonObject()
                            : JsonParser.parseString(body).getAsJsonObject());
            lastPath.set(exchange.getRequestURI().getPath());
            String match = exchange.getRequestHeaders().getFirst("If-None-Match");
            lastMatch.set(match == null ? "" : match);
            byte[] bytes = answer.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, status == 304 ? -1 : bytes.length);
            if (status != 304) {
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            }
            exchange.close();
        });
        started.start();
        server = started;
        String address = "http://127.0.0.1:" + started.getAddress().getPort();
        HttpSite site = new HttpSite(
                HttpSite.client(Duration.ofSeconds(2), address),
                new HttpSite.Connection(
                        address, "gc_" + "a".repeat(43), "inst-1", "c", "1", "minecraft", Duration.ofSeconds(5)));
        return new HttpAccounts(site, "inst-1");
    }

    private JsonObject body() {
        return Objects.requireNonNull(lastBody.get());
    }

    @Test
    @DisplayName("a linked player's profile carries the member, a balance and the gift switch")
    void readsALinkedProfile() throws IOException {
        Profile profile = accounts(
                        200,
                        "{\"linked\":true,\"member\":{\"username\":\"notch\",\"balance\":250.5,\"receiveGifts\":true}}")
                .profile(NOTCH);

        assertThat(profile.linked()).isTrue();
        assertThat(profile.balance()).isEqualByComparingTo("250.5");
        assertThat(body().get("instance").getAsString()).isEqualTo("inst-1");
        assertThat(body().getAsJsonObject("player").get("name").getAsString()).isEqualTo("Notch");
    }

    @Test
    @DisplayName("reading the gift switch reads the profile, because the switch route only writes")
    void readsTheGiftSwitchFromTheProfile() throws IOException {
        boolean receiving = accounts(
                        200,
                        "{\"linked\":true,\"member\":{\"username\":\"notch\",\"balance\":1,\"receiveGifts\":false}}")
                .receiveGifts(NOTCH, null);

        assertThat(receiving).isFalse();
        assertThat(lastPath.get()).endsWith("/player/profile");
        assertThat(body().has("receive")).isFalse();
    }

    @Test
    @DisplayName("turning the gift switch sends the value the site must write")
    void writesTheGiftSwitch() throws IOException {
        assertThat(accounts(200, "{\"receiveGifts\":true}").receiveGifts(NOTCH, true))
                .isTrue();

        assertThat(lastPath.get()).endsWith("/gifts/receive");
        assertThat(body().get("receive").getAsBoolean()).isTrue();
    }

    @Test
    @DisplayName("an unlinked player reads as unlinked, not as an error")
    void readsAnUnlinkedProfile() throws IOException {
        assertThat(accounts(200, "{\"linked\":false}").profile(NOTCH)).isEqualTo(Profile.UNLINKED);
    }

    @Test
    @DisplayName("the catalogue is read whole, and a 304 answers that the copy held is current")
    void readsTheCatalogue() throws IOException {
        Catalog catalog = accounts(200, """
                        {"version":"c-1","currency":"credits","categories":[
                          {"id":"c1","parentId":null,"title":"Ranks","description":"","icon":"diamond","order":0,
                           "products":[{"id":"p1","title":"VIP","description":"30 days","icon":null,"price":100,
                             "giftable":true,"maxQuantity":10,"stock":null,"servers":["Survival"],
                             "fields":[{"name":"colour","label":"Colour","type":"select","required":true,
                                        "placeholder":null,"options":["red","blue"]}]}]}]}""").catalog(null).orElseThrow();

        assertThat(catalog.roots()).extracting(Catalog.Category::title).containsExactly("Ranks");
        assertThat(catalog.product("p1").orElseThrow().fields().getFirst().options())
                .containsExactly("red", "blue");
        stop();

        assertThat(accounts(304, "").catalog("c-1")).isEmpty();
        assertThat(lastMatch.get()).isEqualTo("\"c-1\"");
    }

    @Test
    @DisplayName("a purchase carries its key, its answers and its recipient, and a refusal carries the site's code")
    void purchases() throws IOException {
        HttpAccounts refusing = accounts(409, "{\"error\":\"Not enough credits\",\"code\":\"insufficient_balance\"}");

        assertThatThrownBy(() -> refusing.purchase(NOTCH, "p1", 2, Map.of("colour", "red"), "Alex", "key-12345"))
                .isInstanceOfSatisfying(
                        SiteRefused.class, refused -> assertThat(refused.code()).isEqualTo("insufficient_balance"));
        assertThat(body().get("idempotencyKey").getAsString()).isEqualTo("key-12345");
        assertThat(body().get("recipient").getAsString()).isEqualTo("Alex");
        assertThat(body().getAsJsonObject("fields").get("colour").getAsString()).isEqualTo("red");
    }

    @Test
    @DisplayName("a redeemed voucher answers what it was worth")
    void redeemsAVoucher() throws IOException {
        BigDecimal worth = accounts(200, "{\"amount\":50,\"balance\":250}").redeemVoucher(NOTCH, "gv_abc");

        assertThat(worth).isEqualByComparingTo("50");
        assertThat(body().get("voucherId").getAsString()).isEqualTo("gv_abc");
    }

    @Test
    @DisplayName("a name, a code or an answer the protocol cannot carry is refused here, in words a player reads")
    void whatTheProtocolCannotCarryIsRefusedBeforeItIsSent() throws IOException {
        HttpAccounts accounts = accounts(422, "{\"code\":\"invalid_request\",\"error\":\"too long\"}");
        String longName = "x".repeat(65);

        assertThatThrownBy(() -> accounts.transfer(NOTCH, longName, BigDecimal.ONE, "key-0001"))
                .isInstanceOfSatisfying(
                        SiteRefused.class, refused -> assertThat(refused.code()).isEqualTo("recipient_unknown"));
        assertThatThrownBy(() -> accounts.transfer(NOTCH, "  ", BigDecimal.ONE, "key-0002"))
                .isInstanceOfSatisfying(
                        SiteRefused.class, refused -> assertThat(refused.code()).isEqualTo("recipient_unknown"));
        assertThatThrownBy(() -> accounts.purchase(NOTCH, "p1", 1, Map.of(), longName, "key-0003"))
                .isInstanceOfSatisfying(
                        SiteRefused.class, refused -> assertThat(refused.code()).isEqualTo("recipient_unknown"));
        assertThatThrownBy(() -> accounts.adminCredits(NOTCH, longName, "add", BigDecimal.ONE, "key-0004"))
                .isInstanceOfSatisfying(
                        SiteRefused.class, refused -> assertThat(refused.code()).isEqualTo("recipient_unknown"));
        assertThatThrownBy(() -> accounts.verifyLink(NOTCH, "Z".repeat(33)))
                .isInstanceOfSatisfying(
                        SiteRefused.class, refused -> assertThat(refused.code()).isEqualTo("code_invalid"));
        assertThatThrownBy(() -> accounts.verifyLink(NOTCH, "abc"))
                .isInstanceOfSatisfying(
                        SiteRefused.class, refused -> assertThat(refused.code()).isEqualTo("code_invalid"));
        assertThatThrownBy(() -> accounts.purchase(NOTCH, "p1", 1, Map.of("realm", "r".repeat(201)), null, "key-0005"))
                .isInstanceOfSatisfying(SiteRefused.class, refused -> {
                    assertThat(refused.code()).isEqualTo("field_invalid");
                    assertThat(refused.field()).isEqualTo("realm");
                });
        assertThat(lastPath.get()).isEmpty();
    }
}
