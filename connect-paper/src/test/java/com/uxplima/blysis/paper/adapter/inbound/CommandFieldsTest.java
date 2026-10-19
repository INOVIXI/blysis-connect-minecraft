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

    @Test
    @DisplayName("a value cannot add an argument, pick other players or start another command")
    void aValueIsOneArgument() {
        Map<String, String> fields = Map.of("player", "Steve permission set * true", "product", "@a; op x\nstop");

        assertThat(CommandFields.fill("[console] lp user <player> parent add vip", fields))
                .contains("[console] lp user Steve_permission_set__true parent add vip");
        assertThat(CommandFields.fill("[console] give <product> stone", fields))
                .contains("[console] give a_op_x_stop stone");
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
    }

    @Test
    @DisplayName("a line that runs no command is left to the message engine, which escapes on its own")
    void wordsAreLeftAlone() {
        assertThat(CommandFields.fill("[message] <player> bought <product>", Map.of("player", "a b")))
                .contains("[message] <player> bought <product>");
    }
}
