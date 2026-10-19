package com.uxplima.blysis.paper.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.command.CommandSender;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import com.uxplima.blysis.application.Answer;
import com.uxplima.blysis.application.Boards;
import com.uxplima.blysis.application.CatalogCache;
import com.uxplima.blysis.application.Once;
import com.uxplima.blysis.application.Profiles;
import com.uxplima.blysis.application.SiteSession;
import com.uxplima.blysis.application.port.Accounts;
import com.uxplima.blysis.domain.LinkState;
import com.uxplima.blysis.paper.application.message.Keys;
import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.text.message.Messages;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A server the site stopped answers its players as a server with no site, and asks the site nothing.
 *
 * <p>Found live: with the token refused, "/credits" and "/link" still called the site and showed the player
 * "The site refused that: token_invalid", a word meant for the operator, once per command.
 */
final class AStoppedSiteIsNoSiteTest {

    private final Messages messages = mock(Messages.class);
    private final CommandSender asker = mock(CommandSender.class);
    private final SiteSession stopped = new SiteSession(
            mock(Accounts.class),
            mock(Profiles.class),
            mock(CatalogCache.class),
            mock(Boards.class),
            mock(Once.class),
            () -> LinkState.STOPPED);
    private final Replies replies = new Replies(messages, mock(Scheduler.class), () -> Optional.of(stopped));

    @Test
    @DisplayName("a question for a stopped site is answered as not connected, and the site is never called")
    void notConnected() {
        AtomicInteger called = new AtomicInteger();

        replies.ask(
                asker,
                false,
                site -> {
                    called.incrementAndGet();
                    return Answer.of(() -> "");
                },
                answer -> {});

        assertThat(called).hasValue(0);
        verify(messages).send(eq(asker), eq(Keys.NOT_CONNECTED), any(TagResolver[].class));
    }

    @Test
    @DisplayName("a surface that reads the session finds none while the site has stopped this server")
    void noSession() {
        assertThat(replies.session()).isEmpty();
    }
}
