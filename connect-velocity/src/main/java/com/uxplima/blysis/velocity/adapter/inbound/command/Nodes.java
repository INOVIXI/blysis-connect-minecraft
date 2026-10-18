package com.uxplima.blysis.velocity.adapter.inbound.command;

import com.velocitypowered.api.permission.PermissionSubject;
import com.velocitypowered.api.permission.Tristate;

/**
 * How a node is read on the proxy. Velocity answers a node nobody set as undefined, and undefined as no, so a
 * proxy with no permissions plugin refused every player command it was given. A server gives a player node to
 * everyone unless it is taken away, and the proxy now does the same; a staff node still has to be granted.
 */
final class Nodes {

    private Nodes() {}

    /** A player's node: allowed unless somebody took it away. */
    static boolean player(PermissionSubject subject, String node) {
        return subject.getPermissionValue(node) != Tristate.FALSE;
    }

    /** A staff node: allowed only when somebody granted it. */
    static boolean staff(PermissionSubject subject, String node) {
        return subject.hasPermission(node);
    }
}
