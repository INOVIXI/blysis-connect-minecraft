package com.uxplima.blysis.paper.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.uxplima.blysis.paper.adapter.outbound.OnlinePlayers;

/**
 * Keeps the list of who is online, and asks for a status push when it changes: the site hands a job that
 * waits for its player to this server only once a push says the player is here.
 */
public final class PresenceListener implements Listener {

    private final OnlinePlayers online;
    private final Runnable changed;

    public PresenceListener(OnlinePlayers online, Runnable changed) {
        this.online = Objects.requireNonNull(online, "online");
        this.changed = Objects.requireNonNull(changed, "changed");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        online.joined(event.getPlayer().getUniqueId(), event.getPlayer().getName());
        changed.run();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        online.left(event.getPlayer().getUniqueId());
        changed.run();
    }
}
