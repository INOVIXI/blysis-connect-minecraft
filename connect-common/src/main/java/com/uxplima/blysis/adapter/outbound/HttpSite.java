package com.uxplima.blysis.adapter.outbound;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.uxplima.blysis.application.port.Site;
import com.uxplima.blysis.application.port.SiteRefused;
import com.uxplima.blysis.application.port.SiteUnavailable;
import com.uxplima.blysis.domain.Hello;
import com.uxplima.blysis.domain.Job;
import com.uxplima.blysis.domain.JobResult;
import com.uxplima.blysis.domain.PlayerRef;
import com.uxplima.blysis.domain.Poll;
import com.uxplima.blysis.domain.Protocol;
import com.uxplima.blysis.domain.SiteEvent;
import com.uxplima.blysis.domain.SiteSettings;
import com.uxplima.blysis.domain.StatusPush;
import org.jspecify.annotations.Nullable;

/**
 * The site over HTTPS, with {@link HttpClient}.
 *
 * <p>Every request has a connect timeout and a request timeout, and the long-poll's is the site's hold time
 * plus a margin, so a site that accepts the connection and never answers costs one timeout, not a thread.
 * Every answer is read as JSON; anything that is not the answer this page expects reads as the site being
 * unavailable, never as a value.
 */
public final class HttpSite implements Site {

    /** Added to the site's hold time, so the site always answers a poll before this side gives up. */
    private static final Duration POLL_MARGIN = Duration.ofSeconds(10);

    private final HttpClient client;
    private final Connection connection;
    private volatile Duration pollTimeout;

    public HttpSite(HttpClient client, Connection connection) {
        this.client = Objects.requireNonNull(client, "client");
        this.connection = Objects.requireNonNull(connection, "connection");
        this.pollTimeout = Duration.ofSeconds(25).plus(POLL_MARGIN);
    }

    /**
     * A client with the connect timeout set, the one every request of this class shares.
     *
     * <p>HTTP/2 over https, where the protocol is agreed in the handshake. Plain http stays on HTTP/1.1: the
     * client would otherwise ask to upgrade every request in clear, and a server that does not answer the
     * upgrade the way the client expects closes the connection under it.
     */
    public static HttpClient client(Duration connectTimeout, String address) {
        boolean secure = address.regionMatches(true, 0, "https:", 0, "https:".length());
        return HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .version(secure ? HttpClient.Version.HTTP_2 : HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public Hello hello(String software, String softwareVersion, String role) {
        JsonObject body = new JsonObject();
        body.addProperty("instance", connection.instance());
        JsonObject clientInfo = new JsonObject();
        clientInfo.addProperty("name", connection.clientName());
        clientInfo.addProperty("version", connection.clientVersion());
        body.add("client", clientInfo);
        body.addProperty("game", connection.game());
        JsonObject softwareInfo = new JsonObject();
        softwareInfo.addProperty("name", software);
        softwareInfo.addProperty("version", softwareVersion);
        body.add("software", softwareInfo);
        body.addProperty("role", role);

        JsonObject answer = call("POST", "hello", body, connection.requestTimeout());
        try {
            JsonObject server = answer.getAsJsonObject("server");
            JsonObject site = answer.getAsJsonObject("site");
            JsonObject settings = answer.getAsJsonObject("settings");
            SiteSettings timings = new SiteSettings(
                    Duration.ofSeconds(settings.get("pollHoldSeconds").getAsLong()),
                    Duration.ofSeconds(settings.get("statusIntervalSeconds").getAsLong()),
                    Duration.ofSeconds(settings.get("leaseSeconds").getAsLong()),
                    settings.get("catalogVersion").getAsString());
            pollTimeout = timings.pollHold().plus(POLL_MARGIN);
            JsonElement takeover = answer.get("takeover");
            String previous = takeover == null || !takeover.isJsonObject()
                    ? null
                    : text(takeover.getAsJsonObject(), "previousInstance");
            return new Hello(
                    server.get("id").getAsString(),
                    server.get("name").getAsString(),
                    site.get("name").getAsString(),
                    site.get("url").getAsString(),
                    timings,
                    previous);
        } catch (RuntimeException malformed) {
            throw new SiteUnavailable("the site's hello answer is not the protocol's", malformed);
        }
    }

    @Override
    public Poll poll(@Nullable String eventCursor) {
        JsonObject body = withInstance();
        if (eventCursor != null) {
            body.addProperty("eventCursor", eventCursor);
        }
        JsonObject answer = call("POST", "poll", body, pollTimeout);
        try {
            List<Job> jobs = new ArrayList<>();
            for (JsonElement element : array(answer, "jobs")) {
                jobs.add(job(element.getAsJsonObject()));
            }
            List<SiteEvent> events = new ArrayList<>();
            for (JsonElement element : array(answer, "events")) {
                JsonObject event = element.getAsJsonObject();
                JsonElement data = event.get("data");
                Map<String, String> fields = new LinkedHashMap<>();
                if (data != null && data.isJsonObject()) {
                    flatten("", data.getAsJsonObject(), fields);
                }
                events.add(new SiteEvent(
                        event.get("id").getAsString(), event.get("type").getAsString(), fields));
            }
            return new Poll(
                    jobs,
                    events,
                    answer.get("eventCursor").getAsString(),
                    answer.get("catalogVersion").getAsString());
        } catch (RuntimeException malformed) {
            throw new SiteUnavailable("the site's poll answer is not the protocol's", malformed);
        }
    }

    @Override
    public Set<String> report(List<JobResult> results) {
        JsonObject body = withInstance();
        JsonArray list = new JsonArray();
        for (JobResult result : results) {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", result.jobId());
            entry.addProperty("outcome", result.outcome().wire());
            entry.addProperty("detail", result.detail());
            list.add(entry);
        }
        body.add("results", list);
        JsonObject answer = call("POST", "jobs/report", body, connection.requestTimeout());
        Set<String> accepted = new LinkedHashSet<>();
        try {
            for (JsonElement id : array(answer, "accepted")) {
                accepted.add(id.getAsString());
            }
        } catch (RuntimeException malformed) {
            throw new SiteUnavailable("the site's report answer is not the protocol's", malformed);
        }
        return accepted;
    }

    @Override
    public void status(StatusPush push) {
        JsonObject body = withInstance();
        body.addProperty("online", push.online());
        body.addProperty("max", push.max());
        JsonArray players = new JsonArray();
        for (PlayerRef player : push.players()) {
            players.add(player(player));
        }
        body.add("players", players);
        body.addProperty("version", push.version());
        JsonObject performance = new JsonObject();
        performance.addProperty("tps", push.tps());
        performance.addProperty("mspt", push.mspt());
        body.add("performance", performance);
        call("POST", "status", body, connection.requestTimeout());
    }

    /**
     * A read that answers nothing new when the site's copy is the one this side holds: the request carries
     * {@code If-None-Match}, and a 304 answers empty.
     */
    public Optional<JsonObject> getIfChanged(String path, @Nullable String version) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(Protocol.endpoint(connection.site(), path)))
                .timeout(connection.requestTimeout())
                .header("Authorization", "Bearer " + connection.token())
                .header(Protocol.HEADER, Integer.toString(Protocol.VERSION))
                .header("Accept", "application/json")
                .GET();
        if (version != null && !version.isEmpty()) {
            request.header("If-None-Match", "\"" + version + "\"");
        }
        HttpResponse<String> response = send(request.build());
        if (response.statusCode() == 304) {
            return Optional.empty();
        }
        return Optional.of(answerOf(response.statusCode(), response.body()));
    }

    /** The request timeout the operator set, for a caller of {@link #call}. */
    public Duration requestTimeout() {
        return connection.requestTimeout();
    }

    /**
     * Any call this page names, for the player-facing endpoints: the answer's body, or a {@link SiteRefused}
     * carrying the site's code.
     */
    public JsonObject call(String method, String path, @Nullable JsonObject body, Duration timeout) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(Protocol.endpoint(connection.site(), path)))
                .timeout(timeout)
                .header("Authorization", "Bearer " + connection.token())
                .header(Protocol.HEADER, Integer.toString(Protocol.VERSION))
                .header("Accept", "application/json");
        if (body == null) {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));
        }
        HttpResponse<String> response = send(request.build());
        return answerOf(response.statusCode(), response.body());
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException unreachable) {
            throw new SiteUnavailable(reasonOf(unreachable), unreachable);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new SiteUnavailable("the call was interrupted", interrupted);
        }
    }

    /** Turns a status and a body into the answer, a refusal, or the site being away. */
    static JsonObject answerOf(int status, @Nullable String body) {
        if (status == 204) {
            return new JsonObject();
        }
        JsonObject parsed = parse(body);
        if (status >= 200 && status < 300) {
            if (parsed == null) {
                throw new SiteUnavailable("the site answered " + status + " without JSON");
            }
            return parsed;
        }
        String code = parsed == null ? null : text(parsed, "code");
        boolean meaningful =
                status == 401 || status == 403 || status == 404 || status == 409 || status == 422 || status == 426;
        if (parsed != null && code != null && meaningful) {
            String message = text(parsed, "error");
            throw new SiteRefused(status, code, message == null ? code : message);
        }
        throw new SiteUnavailable("the site answered " + status + (code == null ? "" : " " + code));
    }

    /** What went wrong on the way, in words an operator can act on: the innermost cause names it best. */
    static String reasonOf(IOException unreachable) {
        Throwable cause = unreachable;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String said = cause.getMessage();
        String kind = cause.getClass().getSimpleName();
        return "the site could not be reached: " + (said == null || said.isBlank() ? kind : kind + ", " + said);
    }

    private JsonObject withInstance() {
        JsonObject body = new JsonObject();
        body.addProperty("instance", connection.instance());
        return body;
    }

    private static Job job(JsonObject json) {
        JsonElement player = json.get("player");
        JsonElement reference = json.get("reference");
        String label = reference != null && reference.isJsonObject()
                ? orEmpty(text(reference.getAsJsonObject(), "label"))
                : "";
        return new Job(
                json.get("id").getAsString(),
                json.get("groupKey").getAsString(),
                json.get("sequence").getAsInt(),
                json.get("command").getAsString(),
                player != null && player.isJsonObject() ? playerOf(player.getAsJsonObject()) : null,
                json.has("whenOnline") && json.get("whenOnline").getAsBoolean(),
                label);
    }

    private static PlayerRef playerOf(JsonObject json) {
        return new PlayerRef(text(json, "id"), orEmpty(text(json, "name")));
    }

    private static JsonObject player(PlayerRef player) {
        JsonObject json = new JsonObject();
        json.addProperty("id", player.id());
        json.addProperty("name", player.name());
        return json;
    }

    private static void flatten(String prefix, JsonObject object, Map<String, String> into) {
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            JsonElement value = entry.getValue();
            if (value.isJsonObject()) {
                flatten(key, value.getAsJsonObject(), into);
            } else if (value.isJsonPrimitive()) {
                into.put(key, value.getAsString());
            }
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

    private static @Nullable JsonObject parse(@Nullable String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonElement element = JsonParser.parseString(body);
            return element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (JsonParseException notJson) {
            return null;
        }
    }

    /**
     * Where and who: the site's address, this server's token, this process's instance id, and what the client
     * says it is.
     */
    public record Connection(
            String site,
            String token,
            String instance,
            String clientName,
            String clientVersion,
            String game,
            Duration requestTimeout) {

        public Connection {
            Objects.requireNonNull(site, "site");
            Objects.requireNonNull(token, "token");
            Objects.requireNonNull(instance, "instance");
            Objects.requireNonNull(clientName, "clientName");
            Objects.requireNonNull(clientVersion, "clientVersion");
            Objects.requireNonNull(game, "game");
            Objects.requireNonNull(requestTimeout, "requestTimeout");
        }

        @Override
        public String toString() {
            // The token is the server's whole identity. It never reaches a log line through this record.
            return "Connection[site=" + site + ", instance=" + instance + "]";
        }
    }
}
