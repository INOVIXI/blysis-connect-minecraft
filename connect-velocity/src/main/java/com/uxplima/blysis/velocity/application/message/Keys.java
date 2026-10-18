package com.uxplima.blysis.velocity.application.message;

import com.uxplima.uxmlib.text.message.MessageKey;

/**
 * Every string a player reads on the proxy. A key carries its own English default, so the proxy can never
 * send a message with no text, and an operator who deletes a line still gets something readable.
 */
public enum Keys implements MessageKey {
    VERSION("admin.version", "<tag:'BLYSIS'> <body>BlysisConnect <value><version>"),
    RELOADED("admin.reloaded", "<tag:'BLYSIS'> <body>The configuration is loaded again, in <value><ms><body> ms."),
    RELOAD_FAILED("admin.reload-failed", "<etag:'ERROR'> <body>The reload failed. The console holds the reason."),
    NOT_CONNECTED("account.not-connected", "<etag:'ERROR'> <body>This server is not connected to the site yet."),
    SITE_AWAY(
            "account.away",
            "<etag:'ERROR'> <body>The site does not answer right now. Nothing was taken; try again in a moment."),
    SITE_UNSURE(
            "account.unsure",
            "<etag:'ERROR'> <body>The site did not confirm in time. Look at your balance before you try again."),
    REFUSED_OTHER("refused.other", "<etag:'ERROR'> <body>The site refused that: <value><code><body>."),
    REFUSED_NOT_LINKED(
            "refused.not_linked",
            "<etag:'ERROR'> <body>Link your account first: <value>/link<body>, or make a code on the site and"
                    + " type it after <value>/verify<body>."),
    REFUSED_RECIPIENT_UNKNOWN(
            "refused.recipient_unknown",
            "<etag:'ERROR'> <body>Nobody on the site answers to that name, or it could mean two people."),
    REFUSED_INSUFFICIENT_BALANCE(
            "refused.insufficient_balance", "<etag:'ERROR'> <body>You do not have enough credits for that."),
    REFUSED_AMOUNT_INVALID(
            "refused.amount_invalid",
            "<etag:'ERROR'> <body>That is not an amount: more than zero, two decimals at most."),
    REFUSED_SAME_ACCOUNT("refused.same_account", "<etag:'ERROR'> <body>That is your own account."),
    REFUSED_NOT_ALLOWED(
            "refused.not_allowed",
            "<etag:'ERROR'> <body>The site does not let this server do that. An admin can allow it."),
    REFUSED_NOT_FOUND("refused.not_found", "<etag:'ERROR'> <body>The site has no such thing any more."),
    REFUSED_NOT_IN_GAME("refused.not_in_game", "<etag:'ERROR'> <body>That is not for sale in game."),
    REFUSED_OUT_OF_STOCK("refused.out_of_stock", "<etag:'ERROR'> <body>That has sold out."),
    REFUSED_QUANTITY_INVALID("refused.quantity_invalid", "<etag:'ERROR'> <body>You cannot buy that many of it."),
    REFUSED_FIELD_INVALID(
            "refused.field_invalid", "<etag:'ERROR'> <body>The answer to <value><field><body> is missing or wrong."),
    REFUSED_NOT_GIFTABLE("refused.not_giftable", "<etag:'ERROR'> <body>That cannot be given to someone else."),
    REFUSED_RECIPIENT_REFUSES_GIFTS(
            "refused.recipient_refuses_gifts", "<etag:'ERROR'> <body>That member does not take gifts."),
    REFUSED_ALREADY_CLAIMED("refused.already_claimed", "<etag:'ERROR'> <body>That was already claimed."),
    REFUSED_NOT_PENDING("refused.not_pending", "<etag:'ERROR'> <body>That gift is no longer waiting."),
    REFUSED_VOUCHER_USED(
            "refused.voucher_used", "<etag:'ERROR'> <body>That voucher was already redeemed. It is worth nothing now."),
    REFUSED_CODE_INVALID(
            "refused.code_invalid", "<etag:'ERROR'> <body>That code is wrong, used or expired. Make a new one."),
    REFUSED_ALREADY_LINKED(
            "refused.already_linked",
            "<etag:'ERROR'> <body>This account or that member is already linked. Unlink it on the site first."),
    REFUSED_NEEDS_PLAYER_NAME(
            "refused.needs_player_name",
            "<etag:'ERROR'> <body>That item needs a player name. Claim it from the site's chest."),
    REFUSED_UNAVAILABLE(
            "refused.unavailable", "<etag:'ERROR'> <body>The site cannot do that right now. Try again later."),
    CREDITS_BALANCE("credits.balance", "<tag:'BLYSIS'> <body>You have <value><balance><body> credits."),
    CREDITS_BALANCE_REMEMBERED(
            "credits.balance-remembered",
            "<tag:'BLYSIS'> <body>You had <value><balance><body> credits when the site last answered. It does not"
                    + " answer now, so this may have changed."),
    CREDITS_SENT(
            "credits.sent",
            "<tag:'BLYSIS'> <body>You sent <value><amount><body> credits to <value><to><body>. You have <value><balance><body> left."),
    CREDITS_TOP_HEADER("credits.top-header", "<h:'Top credits'>"),
    CREDITS_TOP_LINE("credits.top-line", "<rank>#<place> <body><name> <subtext><amount>"),
    CREDITS_TOP_ANONYMOUS("credits.top-anonymous", "<subtext>a member"),
    CREDITS_TOP_EMPTY("credits.top-empty", "<tag:'BLYSIS'> <body>Nobody is on this list yet."),
    CREDITS_ADMIN_DONE(
            "credits.admin-done", "<tag:'BLYSIS'> <value><target><body> has <value><balance><body> credits."),
    LINK_CODE(
            "link.code",
            "<tag:'BLYSIS'> <body>Open <value><link><body> signed in and confirm <value><code><body>. It lasts ten minutes."),
    LINK_DONE("link.done", "<tag:'BLYSIS'> <body>This account is linked to <value><member><body> on the site."),
    LINK_ALREADY("link.already", "<tag:'BLYSIS'> <body>This account is already linked to <value><member><body>."),
    PLAYER_UNKNOWN("account.player-unknown", "<etag:'ERROR'> <body>Nobody called <value><input><body> is online."),
    COMMAND_NO_PERMISSION("command.no-permission", "<etag:'ERROR'> <body>You do not have permission to do that."),
    COMMAND_PLAYER_ONLY("command.player-only", "<etag:'ERROR'> <body>Only a player can run this command."),
    PROXY_STATUS(
            "admin.status",
            "<tag:'BLYSIS'> <body>The site: <value><state><body>. Outcomes waiting for it: <value><waiting><body>."),
    CREDITS_USAGE(
            "credits.usage",
            "<tag:'BLYSIS'> <body>Use <value>/credits<body>, <value>/credits send <member> <amount><body> or <value>/credits top [balance|topups|latest] [day|month|year|all]<body>."),
    STAFF_USAGE(
            "admin.usage",
            "<tag:'BLYSIS'> <body>Use <value>reload<body>, <value>status<body> or <value>version<body>.");

    private final String path;
    private final String defaultTemplate;

    Keys(String path, String defaultTemplate) {
        this.path = path;
        this.defaultTemplate = defaultTemplate;
    }

    @Override
    public String path() {
        return path;
    }

    @Override
    public String defaultTemplate() {
        return defaultTemplate;
    }
}
