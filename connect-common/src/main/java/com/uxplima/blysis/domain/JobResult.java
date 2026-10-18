package com.uxplima.blysis.domain;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/** The outcome of one job, as it is written to the journal and reported to the site. */
public record JobResult(
        String jobId, Outcome outcome, @Nullable String detail) {

    /** The detail of a job that was not run because an earlier one of its purchase failed. */
    public static final String SKIPPED = "skipped";

    /** The detail of a job whose command the operator blocked. */
    public static final String BLOCKED = "blocked";

    public JobResult {
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(outcome, "outcome");
    }
}
