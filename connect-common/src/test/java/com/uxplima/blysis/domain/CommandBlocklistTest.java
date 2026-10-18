package com.uxplima.blysis.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;

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
        assertThat(blocklist.blocks("op\tSteve")).isTrue();
        assertThat(blocklist.blocks("stop")).isTrue();
        assertThat(blocklist.blocks("deop Steve")).isTrue();
    }

    @Test
    @DisplayName("a blocked command wrapped in execute or return is still blocked, however deep")
    void looksInsideWrappers() {
        assertThat(blocklist.blocks("execute as @a run op Steve")).isTrue();
        assertThat(blocklist.blocks("minecraft:execute if entity @p RUN execute run minecraft:op Steve"))
                .isTrue();
        assertThat(blocklist.blocks("return run stop")).isTrue();
        assertThat(blocklist.blocks("execute as @a run give @s diamond")).isFalse();
    }

    @Test
    @DisplayName("an alias of a blocked command is blocked under the name the server knows it by")
    void resolvesAliases() {
        Map<String, Set<String>> aliases = Map.of("makeadmin", Set.of("op"));

        assertThat(blocklist.blocks("makeadmin Steve", word -> aliases.getOrDefault(word, Set.of())))
                .isTrue();
        assertThat(blocklist.blocks("makeadmin Steve")).isFalse();
    }

    @Test
    @DisplayName("with an allow list, only the listed commands run, wrapped or not")
    void allowsOnlyWhatIsListed() {
        CommandBlocklist strict = new CommandBlocklist(List.of(), List.of("lp", "give", "execute"));

        assertThat(strict.blocks("lp user Steve parent add vip")).isFalse();
        assertThat(strict.blocks("execute as Steve run give @s diamond")).isFalse();
        assertThat(strict.blocks("execute as Steve run eco give Steve 5")).isTrue();
        assertThat(strict.blocks("ban Steve")).isTrue();
    }

    @Test
    @DisplayName("a command that only starts with the same letters is not blocked, and an empty one is")
    void leavesOtherCommandsAlone() {
        assertThat(blocklist.blocks("opme")).isFalse();
        assertThat(blocklist.blocks("give Steve op_sword")).isFalse();
        assertThat(new CommandBlocklist(List.of()).blocks("op Steve")).isFalse();
        assertThat(blocklist.blocks("   ")).isTrue();
    }
}
