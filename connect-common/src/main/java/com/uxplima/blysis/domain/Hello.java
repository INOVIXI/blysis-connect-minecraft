package com.uxplima.blysis.domain;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * The site's answer to hello: who this server is there, which site it is, and its timings.
 *
 * <p>{@code takeoverOf} names another process that used this token in the last minute and a half. After a
 * restart that is the old process; otherwise two servers share one token.
 */
public record Hello(
        String serverId,
        String serverName,
        String siteName,
        String siteUrl,
        SiteSettings settings,
        @Nullable String takeoverOf) {

    public Hello {
        Objects.requireNonNull(serverId, "serverId");
        Objects.requireNonNull(serverName, "serverName");
        Objects.requireNonNull(siteName, "siteName");
        Objects.requireNonNull(siteUrl, "siteUrl");
        Objects.requireNonNull(settings, "settings");
    }
}
