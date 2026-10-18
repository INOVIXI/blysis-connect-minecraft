package com.uxplima.blysis.domain;

import java.time.Duration;
import java.util.Objects;

/** The timings the site asks for, from its answer to hello. */
public record SiteSettings(Duration pollHold, Duration statusInterval, Duration lease, String catalogVersion) {

    public SiteSettings {
        Objects.requireNonNull(pollHold, "pollHold");
        Objects.requireNonNull(statusInterval, "statusInterval");
        Objects.requireNonNull(lease, "lease");
        Objects.requireNonNull(catalogVersion, "catalogVersion");
    }
}
