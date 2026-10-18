package com.uxplima.blysis.velocity.adapter.inbound.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.velocitypowered.api.permission.PermissionSubject;
import com.velocitypowered.api.permission.Tristate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class NodesTest {

    private static PermissionSubject holding(Map<String, Tristate> nodes) {
        return node -> nodes.getOrDefault(node, Tristate.UNDEFINED);
    }

    @Test
    @DisplayName("a player node nobody set is allowed, as it is on a server")
    void playerNodesDefaultToAllowed() {
        assertThat(Nodes.player(holding(Map.of()), "blysis.link")).isTrue();
        assertThat(Nodes.player(holding(Map.of("blysis.link", Tristate.FALSE)), "blysis.link"))
                .isFalse();
    }

    @Test
    @DisplayName("a staff node has to be granted")
    void staffNodesNeedAGrant() {
        assertThat(Nodes.staff(holding(Map.of()), "blysis.admin")).isFalse();
        assertThat(Nodes.staff(holding(Map.of("blysis.admin", Tristate.TRUE)), "blysis.admin"))
                .isTrue();
    }
}
