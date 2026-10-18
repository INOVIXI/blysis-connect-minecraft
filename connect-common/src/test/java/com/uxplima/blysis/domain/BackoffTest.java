package com.uxplima.blysis.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class BackoffTest {

    @Test
    @DisplayName("the wait doubles up to the longest, and starts again once the site answers")
    void doublesAndResets() {
        Backoff backoff = new Backoff(Duration.ofSeconds(1), Duration.ofSeconds(8), () -> 0.5);

        assertThat(backoff.next()).isEqualTo(Duration.ofSeconds(1));
        assertThat(backoff.next()).isEqualTo(Duration.ofSeconds(2));
        assertThat(backoff.next()).isEqualTo(Duration.ofSeconds(4));
        assertThat(backoff.next()).isEqualTo(Duration.ofSeconds(8));
        assertThat(backoff.next()).isEqualTo(Duration.ofSeconds(8));
        backoff.reset();
        assertThat(backoff.next()).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("the jitter moves a wait by a fifth at most, and never past the longest")
    void jitterStaysInBounds() {
        Backoff low = new Backoff(Duration.ofSeconds(10), Duration.ofSeconds(60), () -> 0.0);
        Backoff high = new Backoff(Duration.ofSeconds(10), Duration.ofSeconds(10), () -> 1.0);

        assertThat(low.next()).isEqualTo(Duration.ofSeconds(8));
        assertThat(high.next()).isEqualTo(Duration.ofSeconds(10));
    }
}
