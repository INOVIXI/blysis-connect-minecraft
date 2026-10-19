package com.uxplima.blysis.paper.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * An announcement's command effect gets the event's fields, each as one argument.
 *
 * <p>The fields are what somebody typed on the site: the player name a buyer gave at checkout is any fifty
 * characters. Written into {@code [console] lp user <player> parent add vip} as they came, a name with a
 * space in it adds arguments of its own, and one with {@code @a} picks every player. The fields did not reach
 * a command line at all before; now they do, and nobody can widen one.
 */
final class CommandFieldsTest {

    @Test
    @DisplayName("a command line gets the event's fields, under their name and under event-<name>")
    void fieldsReachACommand() {
        Map<String, String> fields = Map.of("player", "Steve", "product", "Hero", "quantity", "2");

        assertThat(CommandFields.fill("[console] give <player> diamond <quantity>", fields))
                .contains("[console] give Steve diamond 2");
        assertThat(CommandFields.fill("[player] say <event-product>", fields)).contains("[player] say Hero");
    }

    /**
     * Any value a command would have to change to carry runs nothing: changed, a value is a guess at what
     * somebody meant, and a guess in a console command is how a rank lands on the wrong account.
     */
    @Test
    @DisplayName("a value cannot add an argument, pick other players, start another command or pass as a flag")
    void aValueIsOneArgumentOrNothing() {
        Map<String, String> fields = Map.of("product", "@a; op x\nstop", "quantity", "--force", "title", "Hero rank");

        assertThat(CommandFields.fill("[console] give Steve <product> stone", fields))
                .isEmpty();
        assertThat(CommandFields.fill("[console] say <quantity>", fields)).isEmpty();
        assertThat(CommandFields.fill("[console] say <title>", fields)).isEmpty();
    }

    /**
     * Stripped of what it may not carry, a typed player name can come out as somebody else's: "St@eve" is
     * Steve. So a name is written in as it came or the line does not run.
     */
    @Test
    @DisplayName("a player name that is not a name runs no command, rather than becoming somebody else's")
    void aNameThatIsNotANameRunsNothing() {
        assertThat(CommandFields.fill("[console] lp user <player> parent add vip", Map.of("player", "St@eve")))
                .isEmpty();
        assertThat(CommandFields.fill("[console] lp user <player> parent add vip", Map.of("player", "Steve x")))
                .isEmpty();
        assertThat(CommandFields.fill("[console] give <buyer> stone", Map.of("buyer", "a b")))
                .isEmpty();
    }

    @Test
    @DisplayName("a value with nothing left of it runs no command, so no argument slides into its place")
    void anEmptyValueRunsNothing() {
        assertThat(CommandFields.fill("[console] give <product> stone", Map.of("product", "@;")))
                .isEmpty();
    }

    @Test
    @DisplayName("a player name of a game account keeps its letters, a Bedrock dot and a dash among them")
    void aRealNameIsUntouched() {
        assertThat(CommandFields.fill("[console] give <player-name> stone", Map.of("player.name", ".Brock_2")))
                .contains("[console] give .Brock_2 stone");
        assertThat(CommandFields.fill("[console] give <player> stone", Map.of("player", "Çağrı")))
                .contains("[console] give Çağrı stone");
    }

    @Test
    @DisplayName("a command for a player does not run when the event names no player")
    void aCommandForNobodyDoesNotRun() {
        assertThat(CommandFields.fill("[console] lp user <player> parent add vip", Map.of("product", "Hero")))
                .isEmpty();
        assertThat(CommandFields.fill("[console] say a purchase of <product>", Map.of("product", "Hero")))
                .contains("[console] say a purchase of Hero");
        assertThat(CommandFields.fill("[console] say <product>", Map.of("product", "Hero", "player", "@a")))
                .contains("[console] say Hero");
    }

    @Test
    @DisplayName("a line that runs no command is left to the message engine, which escapes on its own")
    void wordsAreLeftAlone() {
        assertThat(CommandFields.fill("[message] <player> bought <product>", Map.of("player", "a b")))
                .contains("[message] <player> bought <product>");
    }
}
