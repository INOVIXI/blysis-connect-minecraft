package com.uxplima.blysis.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import com.uxplima.blysis.application.port.SiteRefused;
import com.uxplima.blysis.application.port.SiteUnavailable;
import com.uxplima.blysis.domain.Hello;
import com.uxplima.blysis.domain.JobResult;
import com.uxplima.blysis.domain.Outcome;
import com.uxplima.blysis.domain.Poll;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The client against a site on this machine, built from the protocol page: what it sends, what it reads, and
 * what it makes of a site that refuses, breaks, or never answers.
 */
final class HttpSiteTest {

    private static final String TOKEN = "gc_" + "a".repeat(43);

    private @Nullable HttpServer server;
    private final List<String> sockets = new ArrayList<>();
    private final AtomicReference<JsonObject> lastBody = new AtomicReference<>(new JsonObject());
    private final AtomicReference<String> lastAuth = new AtomicReference<>("");
    private final AtomicReference<String> lastProtocol = new AtomicReference<>("");

    private JsonObject body() {
        return Objects.requireNonNull(lastBody.get(), "no request arrived");
    }

    @AfterEach
    void stop() {
        HttpServer running = server;
        if (running != null) {
            running.stop(0);
        }
    }

    private HttpSite site(int status, String answer) throws IOException {
        HttpServer started = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        started.createContext("/api/v1/game-connect/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            lastBody.set(
                    body.isBlank()
                            ? new JsonObject()
                            : JsonParser.parseString(body).getAsJsonObject());
            lastAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            lastProtocol.set(exchange.getRequestHeaders().getFirst("X-Game-Connect-Protocol"));
            sockets.add(exchange.getRequestURI().getPath());
            byte[] bytes = answer.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, status == 204 ? -1 : bytes.length);
            if (status != 204) {
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            }
            exchange.close();
        });
        started.start();
        server = started;
        return siteAt("http://127.0.0.1:" + started.getAddress().getPort(), Duration.ofSeconds(5));
    }

    private static HttpSite siteAt(String address, Duration timeout) {
        return new HttpSite(
                HttpSite.client(Duration.ofSeconds(2)),
                new HttpSite.Connection(
                        address, TOKEN, "inst-1", "blysis-connect-minecraft", "1.0.0", "minecraft", timeout));
    }

    @Test
    @DisplayName("hello sends who this is and reads the site's timings and a takeover")
    void readsHello() throws IOException {
        HttpSite site = site(200, """
                {"server":{"id":"s1","name":"Survival"},"site":{"name":"Example","url":"https://example.com"},
                 "settings":{"pollHoldSeconds":20,"statusIntervalSeconds":30,"leaseSeconds":120,"catalogVersion":"c-1"},
                 "takeover":{"previousInstance":"old","lastSeen":"2026-10-18T12:00:00Z"}}""");

        Hello hello = site.hello("Paper", "26.2", "server");

        assertThat(hello.serverName()).isEqualTo("Survival");
        assertThat(hello.settings().pollHold()).isEqualTo(Duration.ofSeconds(20));
        assertThat(hello.takeoverOf()).isEqualTo("old");
        assertThat(body().get("game").getAsString()).isEqualTo("minecraft");
        assertThat(body().get("instance").getAsString()).isEqualTo("inst-1");
        assertThat(lastAuth.get()).isEqualTo("Bearer " + TOKEN);
        assertThat(lastProtocol.get()).isEqualTo("1");
        assertThat(sockets).containsExactly("/api/v1/game-connect/hello");
    }

    @Test
    @DisplayName("a poll reads its jobs, and its events with their fields flattened")
    void readsPoll() throws IOException {
        HttpSite site = site(200, """
                {"jobs":[{"id":"j1","groupKey":"order:o1:line:s1","sequence":0,"command":"give Notch diamond",
                          "player":{"id":null,"name":"Notch"},"whenOnline":true,
                          "reference":{"kind":"order","id":"o1","label":"Diamonds"}}],
                 "events":[{"id":"e-5","type":"purchase","at":"2026-10-18T12:00:00Z",
                            "data":{"player":"Notch","product":"VIP","quantity":1,"more":0}}],
                 "eventCursor":"e-5","catalogVersion":"c-2"}""");

        Poll poll = site.poll("e-4");

        assertThat(poll.jobs()).singleElement().satisfies(job -> {
            assertThat(job.player()).isNotNull();
            assertThat(job.label()).isEqualTo("Diamonds");
            assertThat(job.whenOnline()).isTrue();
        });
        assertThat(poll.events().getFirst().fields())
                .containsEntry("product", "VIP")
                .containsEntry("quantity", "1");
        assertThat(poll.eventCursor()).isEqualTo("e-5");
        assertThat(body().get("eventCursor").getAsString()).isEqualTo("e-4");
    }

    @Test
    @DisplayName("a report sends every outcome and reads what the site accepted")
    void report() throws IOException {
        HttpSite site = site(200, "{\"accepted\":[\"j1\"]}");

        var accepted = site.report(List.of(
                new JobResult("j1", Outcome.DONE, null), new JobResult("j2", Outcome.FAILED, "Unknown command")));

        assertThat(accepted).containsExactly("j1");
        assertThat(body().getAsJsonArray("results")).hasSize(2);
    }

    @Test
    @DisplayName("a superseded process is told to stop, with the site's code")
    void readsARefusal() throws IOException {
        HttpSite site = site(409, "{\"error\":\"Another process took over\",\"code\":\"superseded\"}");

        assertThatThrownBy(() -> site.poll(null)).isInstanceOfSatisfying(SiteRefused.class, refused -> {
            assertThat(refused.code()).isEqualTo("superseded");
            assertThat(refused.stopsTheConnection()).isTrue();
        });
    }

    @Test
    @DisplayName("a server error, a rate limit and an answer that is not JSON all read as the site being away")
    void readsTroubleAsUnavailable() throws IOException {
        assertThatThrownBy(() -> site(500, "<html>").poll(null)).isInstanceOf(SiteUnavailable.class);
        stop();
        assertThatThrownBy(() -> site(429, "{\"error\":\"slow down\",\"code\":\"rate_limited\"}")
                        .poll(null))
                .isInstanceOf(SiteUnavailable.class);
        stop();
        assertThatThrownBy(() -> site(200, "not json").poll(null)).isInstanceOf(SiteUnavailable.class);
        stop();
        assertThatThrownBy(() -> site(200, "{\"jobs\":[{\"id\":\"j1\"}]}").poll(null))
                .isInstanceOf(SiteUnavailable.class);
    }

    @Test
    @DisplayName("a site that accepts the connection and never answers costs one timeout")
    void timesOutOnASilentSite() throws IOException {
        try (ServerSocket silent = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            Thread.ofVirtual().start(() -> {
                try (Socket accepted = silent.accept()) {
                    accepted.getInputStream().readAllBytes();
                } catch (IOException closed) {
                    // The test closed the socket: nothing to do.
                }
            });
            HttpSite site = siteAt("http://127.0.0.1:" + silent.getLocalPort(), Duration.ofMillis(500));
            long start = System.nanoTime();

            assertThatThrownBy(() ->
                            site.status(new com.uxplima.blysis.domain.StatusPush(0, 10, List.of(), "26.2", 20, 5)))
                    .isInstanceOf(SiteUnavailable.class);
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(5));
        }
    }

    @Test
    @DisplayName("a closed port reads as the site being away, not as an error to throw at a player")
    void refusedConnectionIsUnavailable() throws IOException {
        int port;
        try (ServerSocket free = new ServerSocket(0)) {
            port = free.getLocalPort();
        }
        HttpSite site = siteAt("http://127.0.0.1:" + port, Duration.ofSeconds(1));

        assertThatThrownBy(() -> site.poll(null)).isInstanceOf(SiteUnavailable.class);
    }

    @Test
    @DisplayName("the token never reaches a log line through the connection")
    void hidesTheToken() {
        HttpSite.Connection connection = new HttpSite.Connection(
                "https://example.com", TOKEN, "inst", "c", "1", "minecraft", Duration.ofSeconds(1));

        assertThat(connection.toString()).doesNotContain(TOKEN);
    }
}
