package com.uxplima.blysis.domain.account;

import java.time.Instant;
import java.util.Objects;

/** A code made in game for a member to confirm on the site, and the page that confirms it. */
public record LinkCode(String code, String url, Instant expiresAt) {

    public LinkCode {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }
}
