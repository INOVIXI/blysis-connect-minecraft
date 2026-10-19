package com.uxplima.blysis.paper.adapter.inbound.command;

import java.util.Objects;

import com.uxplima.blysis.paper.adapter.inbound.gui.ChestMenus;
import com.uxplima.uxmlib.command.Sender;
import com.uxplima.uxmlib.command.annotation.annotations.FromConfig;
import com.uxplima.uxmlib.command.annotation.annotations.Permission;
import com.uxplima.uxmlib.command.annotation.annotations.PlayerOnly;
import com.uxplima.uxmlib.command.annotation.annotations.Subcommand;

/** The chest: what a player bought and has not taken, and the gifts that wait for them. */
@FromConfig(value = ChestCommand.KEY, fallbackName = "chest", description = "Take what you bought, accept gifts")
public final class ChestCommand {

    /** The key this command is named under in {@code commands.conf}. */
    public static final String KEY = "chest";

    private final ChestMenus chest;

    public ChestCommand(ChestMenus chest) {
        this.chest = Objects.requireNonNull(chest, "chest");
    }

    @Subcommand("")
    @Permission(ChestMenus.NODE)
    @PlayerOnly
    public void open(Sender sender) {
        chest.openChest(sender.player().orElseThrow());
    }

    @FromConfig(value = "sent", fallbackName = "sent", description = "The gifts you sent, to take one back")
    @Permission(ChestMenus.NODE)
    @PlayerOnly
    public void sent(Sender sender) {
        chest.openSent(sender.player().orElseThrow());
    }
}
