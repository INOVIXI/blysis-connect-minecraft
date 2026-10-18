package com.uxplima.blysis.adapter.outbound;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import com.uxplima.blysis.application.ConnectSettings;
import com.uxplima.uxmlib.config.HoconConfig;

/**
 * Reads the {@code site}, {@code delivery} and {@code connection} blocks of {@code config.conf}, the same on
 * Paper and on Velocity. The platform's settings class calls it, so one file holds one reader.
 */
public final class ConnectSettingsFile {

    /** The commands no shop needs, refused unless an operator removes them. */
    public static final List<String> SHIPPED_BLOCKED =
            List.of("op", "deop", "stop", "restart", "reload", "whitelist", "ban-ip", "pardon-ip", "sudo");

    private ConnectSettingsFile() {}

    public static ConnectSettings read(HoconConfig config) {
        Objects.requireNonNull(config, "config");
        List<String> blocked = config.nodeAt("delivery", "blocked").virtual()
                ? SHIPPED_BLOCKED
                : config.getList("delivery.blocked", String.class);
        return new ConnectSettings(
                config.getString("site.address", ""),
                config.getString("site.token", ""),
                blocked,
                config.getList("delivery.allow-only", String.class),
                seconds(config, "delivery.command-timeout-seconds", 10),
                seconds(config, "connection.connect-timeout-seconds", 5),
                seconds(config, "connection.request-timeout-seconds", 15),
                seconds(config, "connection.retry-first-seconds", 2),
                seconds(config, "connection.retry-longest-seconds", 120));
    }

    private static Duration seconds(HoconConfig config, String path, int fallback) {
        return Duration.ofSeconds(config.getInt(path, fallback));
    }
}
