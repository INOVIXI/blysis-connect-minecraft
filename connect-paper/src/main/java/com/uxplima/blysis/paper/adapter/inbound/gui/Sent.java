package com.uxplima.blysis.paper.adapter.inbound.gui;

import java.util.List;

import com.uxplima.blysis.domain.account.SentGift;

/** What the sent gifts window is about: every gift the player bought for somebody else. */
public record Sent(List<SentGift> gifts) {

    public Sent {
        gifts = List.copyOf(gifts);
    }
}
