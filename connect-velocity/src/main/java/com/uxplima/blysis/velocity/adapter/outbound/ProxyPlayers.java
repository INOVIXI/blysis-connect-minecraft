package com.uxplima.blysis.velocity.adapter.outbound;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import com.uxplima.blysis.application.port.Presence;
import com.uxplima.blysis.domain.PlayerRef;
import com.velocitypowered.api.proxy.Player;

/** Who is on the network: by id when the site knows it, by name when it does not. */
public final class ProxyPlayers implements Presence {

    private final Supplier<Collection<Player>> online;

    public ProxyPlayers(Supplier<Collection<Player>> online) {
        this.online = Objects.requireNonNull(online, "online");
    }

    @Override
    public boolean isOnline(PlayerRef player) {
        Objects.requireNonNull(player, "player");
        String id = player.id();
        if (id != null) {
            try {
                return find(UUID.fromString(id)).isPresent();
            } catch (IllegalArgumentException notAnId) {
                // A site that sends a name where an id belongs still reaches the player by the name.
            }
        }
        String name = player.name().toLowerCase(Locale.ROOT);
        return online.get().stream()
                .anyMatch(here -> here.getUsername().toLowerCase(Locale.ROOT).equals(name));
    }

    /** The player with this id, when they are on the network. */
    public Optional<Player> find(UUID id) {
        Objects.requireNonNull(id, "id");
        return online.get().stream()
                .filter(here -> here.getUniqueId().equals(id))
                .findFirst();
    }

    /** Everyone on the network now, as the protocol names them. */
    public List<PlayerRef> all() {
        return online.get().stream().map(ProxyPlayers::refOf).toList();
    }

    public static PlayerRef refOf(Player player) {
        return new PlayerRef(player.getUniqueId().toString(), player.getUsername());
    }
}
