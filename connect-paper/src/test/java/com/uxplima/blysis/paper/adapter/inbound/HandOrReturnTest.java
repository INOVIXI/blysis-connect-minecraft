package com.uxplima.blysis.paper.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.scheduler.TaskHandle;
import com.uxplima.uxmlib.text.message.Messages;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Something the site already took credits for reaches the player's hand, or goes back, and never both.
 *
 * <p>A voucher is made on the site before its item exists. The item was handed on the player's own thread,
 * and a player who left in between was simply skipped: the credits were gone and no item ever came. The
 * scheduler drops a task for an entity that is gone and says nothing, so the way back is a second task off
 * the server threads, and whichever of the two runs first is the only one that counts.
 */
final class HandOrReturnTest {

    private final List<Runnable> onEntity = new ArrayList<>();
    private final List<Runnable> later = new ArrayList<>();
    private final Scheduler scheduler = mock(Scheduler.class);
    private final Player player = mock(Player.class);
    private final AtomicInteger handed = new AtomicInteger();
    private final AtomicInteger returned = new AtomicInteger();
    private final Replies replies;

    HandOrReturnTest() {
        when(scheduler.entity(any(Entity.class), any(Runnable.class))).thenAnswer(call -> {
            onEntity.add(call.getArgument(1));
            return mock(TaskHandle.class);
        });
        when(scheduler.asyncLater(any(Duration.class), any(Runnable.class))).thenAnswer(call -> {
            later.add(call.getArgument(1));
            return mock(TaskHandle.class);
        });
        replies = new Replies(mock(Messages.class), scheduler, Optional::empty);
    }

    private void handOrReturn() {
        replies.handOrReturn(player, handed::incrementAndGet, returned::incrementAndGet);
    }

    @Test
    @DisplayName("a player who stayed gets it in hand, and nothing goes back")
    void aPlayerWhoStayedGetsIt() {
        when(player.isOnline()).thenReturn(true);
        handOrReturn();
        onEntity.forEach(Runnable::run);
        later.forEach(Runnable::run);

        assertThat(handed).hasValue(1);
        assertThat(returned).hasValue(0);
    }

    @Test
    @DisplayName("a player who left, whose task the server dropped, gets it back")
    void aDroppedTaskGoesBack() {
        handOrReturn();
        later.forEach(Runnable::run);

        assertThat(handed).hasValue(0);
        assertThat(returned).hasValue(1);
    }

    @Test
    @DisplayName("a player found gone on their own thread gets it back once")
    void aPlayerFoundGoneGetsItBackOnce() {
        when(player.isOnline()).thenReturn(false);
        handOrReturn();
        onEntity.forEach(Runnable::run);
        later.forEach(Runnable::run);

        assertThat(handed).hasValue(0);
        assertThat(returned).hasValue(1);
    }
}
