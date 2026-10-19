package com.uxplima.blysis.paper.adapter.inbound;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

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
import com.uxplima.uxmlib.scheduler.TaskHandle;
import com.uxplima.uxmlib.text.message.Messages;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A site that does not answer is described by what the player asked for.
 *
 * <p>Found live with the site stopped: "/link" and opening the chest said "Nothing was taken", which is about
 * money and these move none. A move of money that was sent and not answered may have gone through, so it is told
 * to look at the balance; only one that was never sent may say nothing was taken.
 */
final class WhatAnAwaySiteSaysTest {

    private final Messages messages = mock(Messages.class);
    private final CommandSender asker = mock(CommandSender.class);
    private final Scheduler scheduler = mock(Scheduler.class);
    private final SiteSession site = new SiteSession(
            mock(Accounts.class),
            mock(Profiles.class),
            mock(CatalogCache.class),
            mock(Boards.class),
            mock(Once.class),
            () -> LinkState.CONNECTED);
    private final Replies replies;

    WhatAnAwaySiteSaysTest() {
        when(scheduler.async(any(Runnable.class))).thenAnswer(call -> {
            ((Runnable) call.getArgument(0)).run();
            return mock(TaskHandle.class);
        });
        replies = new Replies(messages, scheduler, () -> Optional.of(site));
    }

    @Test
    @DisplayName("a question that moves no money and gets no answer says only that the site does not answer")
    void noMoneyNoMentionOfIt() {
        replies.ask(asker, false, open -> new Answer.Away<String>("down"), answer -> {});

        verify(messages).send(eq(asker), eq(Keys.SITE_SILENT), any(TagResolver[].class));
        verify(messages, never()).send(eq(asker), eq(Keys.SITE_AWAY), any(TagResolver[].class));
    }

    @Test
    @DisplayName("a move of money that was sent and not answered is told to look at the balance")
    void aSentMoveIsUnsure() {
        replies.ask(asker, true, open -> new Answer.Away<String>("down"), answer -> {});

        verify(messages).send(eq(asker), eq(Keys.SITE_UNSURE), any(TagResolver[].class));
    }
}
