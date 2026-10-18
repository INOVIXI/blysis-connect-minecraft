package com.uxplima.blysis.paper.adapter.outbound;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.blysis.application.port.Presence;
import com.uxplima.blysis.domain.PlayerRef;

/**
 * Who is on this server, kept from the join and quit events so a thread that delivers or pushes status reads
 * it without asking the server from off its own thread.
 */
public final class OnlinePlayers implements Presence {

    private final Map<UUID, PlayerRef> byId = new ConcurrentHashMap<>();

    /** A player joined, or renamed: the name kept is the one shown now. */
    public void joined(UUID id, String name) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        byId.compute(id, (key, previous) -> new PlayerRef(key.toString(), name));
    }

    /** A player left. */
    public void left(UUID id) {
        byId.remove(Objects.requireNonNull(id, "id"));
    }

    /** Everyone online now, in no particular order. */
    public List<PlayerRef> all() {
        return List.copyOf(byId.values());
    }

    @Override
    public boolean isOnline(PlayerRef player) {
        Objects.requireNonNull(player, "player");
        String id = player.id();
        if (id != null) {
            try {
                return byId.containsKey(UUID.fromString(id));
            } catch (IllegalArgumentException notAUuid) {
                return false;
            }
        }
        String name = player.name().toLowerCase(Locale.ROOT);
        return byId.values().stream()
                .anyMatch(online -> online.name().toLowerCase(Locale.ROOT).equals(name));
    }
}
