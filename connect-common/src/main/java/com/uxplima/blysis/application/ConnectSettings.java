package com.uxplima.blysis.application;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.blysis.domain.CommandBlocklist;

/**
 * Where the site is, how this server proves itself to it, and how patiently it calls.
 *
 * <p>An empty address or token is a server that has not been set up yet: the plugin starts, says so once,
 * and connects to nothing. A malformed one is refused when the file is read, so a reload that broke the
 * address keeps the last good snapshot.
 */
public record ConnectSettings(
        String address,
        String token,
        List<String> blocked,
        List<String> allowOnly,
        Duration commandTimeout,
        Duration connectTimeout,
        Duration requestTimeout,
        Duration retryFirst,
        Duration retryLongest) {

    /** {@code gc_} and 43 base64url characters, as the site issues them. */
    private static final String TOKEN_SHAPE = "gc_[A-Za-z0-9_-]{43}";

    public ConnectSettings {
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(token, "token");
        blocked = List.copyOf(blocked);
        allowOnly = List.copyOf(allowOnly);
        address = address.strip();
        token = token.strip();
        if (!address.isEmpty()) {
            URI uri = URI.create(address);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if ((!scheme.equals("https") && !scheme.equals("http")) || uri.getHost() == null) {
                throw new IllegalArgumentException("site.address must be the site's https address, got: " + address);
            }
        }
        if (!token.isEmpty() && !token.matches(TOKEN_SHAPE)) {
            throw new IllegalArgumentException("site.token is not a token the site issued: it starts with gc_");
        }
        positive(commandTimeout, "delivery.command-timeout-seconds");
        positive(connectTimeout, "connection.connect-timeout-seconds");
        positive(requestTimeout, "connection.request-timeout-seconds");
        positive(retryFirst, "connection.retry-first-seconds");
        positive(retryLongest, "connection.retry-longest-seconds");
        if (retryLongest.compareTo(retryFirst) < 0) {
            throw new IllegalArgumentException("connection.retry-longest-seconds is shorter than the first wait");
        }
    }

    /** True when both the address and the token are there. */
    public boolean configured() {
        return !address.isEmpty() && !token.isEmpty();
    }

    /** The blocklist these settings describe. */
    public CommandBlocklist blocklist() {
        return new CommandBlocklist(blocked, allowOnly);
    }

    /** The address, when it is plain http to a host that is not this machine: a token would cross in clear. */
    public Optional<String> insecureAddress() {
        if (address.isEmpty()) {
            return Optional.empty();
        }
        URI uri = URI.create(address);
        String host = uri.getHost() == null ? "" : uri.getHost();
        boolean local = host.equals("localhost") || host.equals("127.0.0.1") || host.equals("::1");
        return "http".equalsIgnoreCase(uri.getScheme()) && !local ? Optional.of(address) : Optional.empty();
    }

    @Override
    public String toString() {
        // The token is the server's whole identity on the site. It never reaches a log line through this record.
        return "ConnectSettings[address=" + address + ", configured=" + configured() + "]";
    }

    private static void positive(Duration value, String key) {
        Objects.requireNonNull(value, key);
        if (value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException(key + " must be more than zero");
        }
    }
}
