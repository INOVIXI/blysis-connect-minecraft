package com.uxplima.blysis.velocity.adapter.inbound.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.uxplima.blysis.application.port.Lanes;
import com.uxplima.blysis.velocity.adapter.inbound.Replies;
import com.uxplima.blysis.velocity.application.message.Keys;
import com.uxplima.uxmlib.text.message.Messages;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * On the proxy too, the rankings are offered as a player types, and a word that names none is answered.
 *
 * <p>Found on the server first: "/credits top balance forever" showed the all-time list as though "forever"
 * were a period, and nothing after "top" was offered. The proxy read the words the same way.
 */
final class TheRankingsAreOfferedTest {

    private final Messages messages = mock(Messages.class);
    private final Lanes lanes = mock(Lanes.class);
    private final CreditsCommand credits = new CreditsCommand(new Replies(messages, lanes, Optional::empty), messages);
    private final CommandSource player = mock(CommandSource.class);

    private SimpleCommand.Invocation typed(String... words) {
        SimpleCommand.Invocation invocation = mock(SimpleCommand.Invocation.class);
        when(invocation.source()).thenReturn(player);
        when(invocation.arguments()).thenReturn(words);
        return invocation;
    }

    @Test
    @DisplayName("credits top offers each list, and each period after it")
    void theListsAndPeriodsAreOffered() {
        assertThat(credits.suggest(typed("top", ""))).contains("balance", "topups", "latest");
        assertThat(credits.suggest(typed("top", "balance", ""))).contains("day", "month", "year", "all");
    }

    @Test
    @DisplayName("a list or a period that does not exist is answered with the usage, and the site is not asked")
    void anUnknownWordIsAnswered() {
        credits.execute(typed("top", "balance", "forever"));
        credits.execute(typed("top", "../x"));

        verify(messages, times(2)).send(player, Keys.CREDITS_USAGE);
        verifyNoInteractions(lanes);
    }
}
