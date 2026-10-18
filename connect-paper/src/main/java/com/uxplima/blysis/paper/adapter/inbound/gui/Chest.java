package com.uxplima.blysis.paper.adapter.inbound.gui;

import java.util.List;

import com.uxplima.blysis.domain.account.ChestItem;

/** What the chest window is about: what waits for the player, and whether they take gifts. */
public record Chest(List<ChestItem> items, boolean receiving) {

    public Chest {
        items = List.copyOf(items);
    }
}
