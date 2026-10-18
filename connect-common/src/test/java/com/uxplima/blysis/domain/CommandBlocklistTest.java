package com.uxplima.blysis.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class CommandBlocklistTest {

    private final CommandBlocklist blocklist = new CommandBlocklist(List.of("op", " /Stop ", "minecraft:deop"));

    @Test
    @DisplayName("a blocked word is blocked however the command spells it")
    void blocksEverySpelling() {
        assertThat(blocklist.blocks("op Steve")).isTrue();
        assertThat(blocklist.blocks("/OP Steve")).isTrue();
        assertThat(blocklist.blocks("//minecraft:op Steve")).isTrue();
        assertThat(blocklist.blocks("stop")).isTrue();
        assertThat(blocklist.blocks("deop Steve")).isTrue();
    }

    @Test
    @DisplayName("a command that only starts with the same letters is not blocked")
    void leavesOtherCommandsAlone() {
        assertThat(blocklist.blocks("opme")).isFalse();
        assertThat(blocklist.blocks("give Steve op_sword")).isFalse();
        assertThat(new CommandBlocklist(List.of()).blocks("op Steve")).isFalse();
    }
}
