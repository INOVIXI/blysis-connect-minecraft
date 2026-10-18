package com.uxplima.blysis.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class ProtocolTest {

    @Test
    @DisplayName("an endpoint reads the same whether the operator wrote the site with a slash or without")
    void endpointIgnoresTheTrailingSlash() {
        assertThat(Protocol.endpoint("https://example.com", "poll"))
                .isEqualTo("https://example.com/api/v1/game-connect/poll");
        assertThat(Protocol.endpoint("https://example.com/", "/poll"))
                .isEqualTo("https://example.com/api/v1/game-connect/poll");
    }
}
