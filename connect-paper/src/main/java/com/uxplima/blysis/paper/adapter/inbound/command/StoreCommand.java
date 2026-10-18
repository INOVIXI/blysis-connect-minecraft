package com.uxplima.blysis.paper.adapter.inbound.command;

import java.util.Objects;

import com.uxplima.blysis.paper.adapter.inbound.gui.StoreMenus;
import com.uxplima.uxmlib.command.Sender;
import com.uxplima.uxmlib.command.annotation.annotations.FromConfig;
import com.uxplima.uxmlib.command.annotation.annotations.Permission;
import com.uxplima.uxmlib.command.annotation.annotations.PlayerOnly;
import com.uxplima.uxmlib.command.annotation.annotations.Subcommand;

/** The store window, the site's shelves as the operator marked them visible in game. */
@FromConfig(value = StoreCommand.KEY, fallbackName = "store", description = "Open the site's store")
public final class StoreCommand {

    /** The key this command is named under in {@code commands.conf}. */
    public static final String KEY = "store";

    private final StoreMenus store;

    public StoreCommand(StoreMenus store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Subcommand("")
    @Permission("blysis.store")
    @PlayerOnly
    public void open(Sender sender) {
        store.openStore(sender.player().orElseThrow());
    }
}
