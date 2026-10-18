package com.uxplima.blysis.domain;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * One command the site handed this server to run, already claimed for it.
 *
 * <p>Jobs that share a {@code groupKey} are one purchase on one server, and run in {@code sequence} order.
 */
public record Job(
        String id,
        String groupKey,
        int sequence,
        String command,
        @Nullable PlayerRef player,
        boolean whenOnline,
        String label) {

    public Job {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(groupKey, "groupKey");
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(label, "label");
        if (id.isBlank()) {
            throw new IllegalArgumentException("a job needs an id");
        }
    }
}
