package com.uxplima.blysis.paper.application.message;

import com.uxplima.uxmlib.text.message.MessageKey;

/**
 * Every string that a player can see. A key carries its own English default, so the plugin can never ship a
 * message with no text, and an operator who deletes a line still gets something readable.
 *
 * <p>No user-facing text appears anywhere else. A literal in a command or a listener is a defect, and
 * {@code NoInlineTextTest} fails the build over one.
 */
public enum Keys implements MessageKey {
    USAGE("admin.usage", "<tag:'BLYSIS'> <body>Add <value>help<body> after this command to see everything it does."),
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
    REFUSED_LIMIT_REACHED(
            "refused.limit_reached",
            "<etag:'ERROR'> <body>One account may own only so many of that, and whoever it is for has reached it."),
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
            "refused.unavailable",
            "<etag:'ERROR'> <body>That cannot be bought by this account right now. The store page on the site says"
                    + " why."),

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

    VOUCHER_CREATED(
            "voucher.created",
            "<tag:'BLYSIS'> <body>A voucher of <value><amount><body> credits is in your inventory. It pays once, to whoever uses it."),
    VOUCHER_GIVEN(
            "voucher.given",
            "<tag:'BLYSIS'> <body>A voucher of <value><amount><body> credits went to <value><player><body>."),
    VOUCHER_RECEIVED(
            "voucher.received", "<tag:'BLYSIS'> <body>You were given a voucher of <value><amount><body> credits."),
    VOUCHER_REDEEMED("voucher.redeemed", "<tag:'BLYSIS'> <body>The voucher paid <value><amount><body> credits."),
    VOUCHER_DROPPED("voucher.dropped", "<tag:'BLYSIS'> <body>Your inventory is full, so the voucher is at your feet."),
    VOUCHER_NOT_HELD("voucher.not-held", "<etag:'ERROR'> <body>Hold the voucher in your hand."),
    VOUCHER_ITEM_NAME("voucher.item.name", "<value><amount> credits"),
    VOUCHER_ITEM_LORE("voucher.item.lore", "<body>A credit voucher."),
    VOUCHER_ITEM_HINT("voucher.item.hint", "<subtext>Use it to add the credits to your account. It pays once."),
    MENU_MISSING("menu.missing", "<etag:'ERROR'> <body>The <value><menu><body> window is not on this server."),
    STORE_EMPTY("store.empty", "<tag:'BLYSIS'> <body>The store has nothing for sale in game yet."),
    STORE_STALE(
            "store.stale",
            "<warn>The site does not answer, so this is the store as it last was. Buying waits until it is back."),
    STORE_EVERY_SERVER("store.every-server", "every server"),
    STORE_UNLIMITED("store.unlimited", "unlimited"),
    STORE_YOURSELF("store.yourself", "yourself"),
    STORE_NOT_GIFTABLE("store.not-giftable", "<etag:'ERROR'> <body>That cannot be given to someone else."),
    STORE_BOUGHT(
            "store.bought",
            "<tag:'BLYSIS'> <body>You bought <value><quantity>x <product><body> for <value><total><body> credits. You have <value><balance><body> left. Order <value><order><body>."),
    STORE_GIFTED(
            "store.gifted",
            "<tag:'BLYSIS'> <body>You gave <value><quantity>x <product><body> to <value><to><body> for <value><total><body> credits. You can take it back until they accept it."),
    INPUT_QUANTITY("input.quantity", "<body>How many?"),
    INPUT_RECIPIENT("input.recipient", "<body>Who is it for? Their name on the site or in game."),
    INPUT_FIELD("input.field", "<body><field>"),
    GIFTS_ON("chest.gifts-on", "<tag:'BLYSIS'> <body>You take gifts now."),
    GIFTS_OFF(
            "chest.gifts-off",
            "<tag:'BLYSIS'> <body>You no longer take gifts. A purchase for you is refused before anyone pays."),
    CHEST_CLAIMED("chest.claimed", "<tag:'BLYSIS'> <value><thing><body> is on its way to you."),
    CHEST_ACCEPTED(
            "chest.accepted", "<tag:'BLYSIS'> <body>You accepted <value><thing><body>. It is in your chest to claim."),
    GIFT_ALREADY_TAKEN(
            "gift.already-accepted", "<etag:'ERROR'> <body>That gift was accepted. It cannot be taken back."),
    GIFT_TAKE_BACK_TITLE("gift.take-back-title", "Take the gift back?"),
    GIFT_TAKEN_BACK(
            "gift.taken-back",
            "<tag:'BLYSIS'> <body>You took back <value><thing><body>. <value><amount><body> credits came back to you."),
    STATE_GIFT_WAITING("state.gift-waiting", "waiting to be accepted"),
    STATE_READY("state.ready", "ready to claim"),
    STATE_ACCEPTED("state.accepted", "accepted"),
    STATE_ON("state.on", "on"),
    STATE_OFF("state.off", "off"),

    ANNOUNCE_PURCHASE(
            "announce.purchase",
            "<tag:'BLYSIS'> <value><player><body> bought <value><quantity>x <product><body> on the site."),
    ANNOUNCE_TOPUP(
            "announce.topup",
            "<tag:'BLYSIS'> <value><player><body> topped up <value><amount><body> credits on the site."),
    ANNOUNCE_GOAL_TITLE("announce.goal-title", "<good>Goal reached"),
    ANNOUNCE_GOAL_SUBTITLE("announce.goal-subtitle", "<body><event-title>: <value><raised><body> of <value><goal>"),
    ANNOUNCE_SOMEONE("announce.someone", "Someone"),
    ANNOUNCE_GIFT_RECEIVED(
            "announce.gift-received",
            "<tag:'BLYSIS'> <value><from><body> sent you <value><quantity>x <product><body>. It waits in your chest"
                    + " until you accept it."),
    STAFF_SITE_DOWN(
            "staff.site-down",
            "<etag:'ERROR'> <body>The site does not answer. Deliveries wait there and purchases are refused until it is back."),
    STAFF_SITE_BACK("staff.site-back", "<tag:'BLYSIS'> <body>The site answers again."),

    PLAYER_UNKNOWN("account.player-unknown", "<etag:'ERROR'> <body>Nobody called <value><input><body> is online."),

    LANGUAGE_CURRENT("language.current", "<tag:'BLYSIS'> <body>You read this in <value><language><body>."),
    LANGUAGE_CHOICES("language.choices", "<tag:'BLYSIS'> <body>You may read it in <value><languages><body>."),
    LANGUAGE_CHANGED("language.changed", "<tag:'BLYSIS'> <body>You now read this in <value><language><body>."),
    LANGUAGE_ALREADY("language.already", "<tag:'BLYSIS'> <body>You already read this in <value><language><body>."),
    LANGUAGE_RESET("language.reset", "<tag:'BLYSIS'> <body>You no longer have a language of your own."),
    LANGUAGE_SEPARATOR("language.separator", "<dim>, "),
    LANGUAGE_UNKNOWN(
            "language.unknown",
            "<etag:'ERROR'> <body>This server has no <value><input><body>. It has <value><languages><body>."),
    LANGUAGE_FORCED(
            "language.forced",
            "<etag:'ERROR'> <body>This server reads one language, and it is <value><language><body>."),
    LANGUAGE_SET_PLAYER(
            "language.set-player", "<tag:'BLYSIS'> <value><player><body> now reads <value><language><body>."),
    LANGUAGE_SET_PLAYER_AWAY(
            "language.set-player-away",
            "<tag:'BLYSIS'> <value><player><body> is away and reads <value><language><body> from their next join."),
    LANGUAGE_RESET_PLAYER(
            "language.reset-player", "<tag:'BLYSIS'> <value><player><body> no longer has a language of their own."),
    LANGUAGE_RESET_PLAYER_AWAY(
            "language.reset-player-away",
            "<tag:'BLYSIS'> <value><player><body> is away, and their language is cleared at their next join."),
    LANGUAGE_FORCED_ANYWAY(
            "language.forced-anyway",
            "<tag:'BLYSIS'> <body>It is kept, and everyone reads <value><language><body> while that is forced."),
    LANGUAGE_UNKNOWN_PLAYER(
            "language.unknown-player", "<etag:'ERROR'> <body>Nobody called <value><input><body> has played here."),

    COMMAND_NO_PERMISSION("command.no-permission", "<etag:'ERROR'> <body>You do not have permission to do that."),

    COMMAND_PLAYER_ONLY("command.player-only", "<etag:'ERROR'> <body>Only a player can run this command."),
    COMMAND_INVALID_VALUE(
            "command.invalid-value",
            "<etag:'ERROR'> <body><value><input><body> is not a valid <value><argument><body>."),
    COMMAND_INVALID_VALUE_WHY(
            "command.invalid-value-why",
            "<etag:'ERROR'> <body><value><input><body> is not a valid <value><argument><body>: <subtext><reason>"),
    COMMAND_NOT_ONE_OF(
            "command.not-one-of",
            "<etag:'ERROR'> <body><value><input><body> is not a valid <value><argument><body>. Try one of <value><allowed><body>."),
    COMMAND_INVALID_ARGUMENT("command.invalid-argument", "<etag:'ERROR'> <body><detail>"),
    COMMAND_BAD_ARGUMENT("command.bad-argument", "<etag:'ERROR'> <body>That argument is not valid."),
    COMMAND_INTERNAL_ERROR(
            "command.internal-error",
            "<etag:'ERROR'> <body>Something went wrong while running this command. The console holds the reason."),
    COMMAND_ON_COOLDOWN(
            "command.on-cooldown", "<etag:'ERROR'> <body>Wait <value><time><body> before you use this again."),
    COMMAND_HELP_HEADER(
            "command.help-header", "<h:'Help'> <dim>/<value><command> <dim>(<value><page><dim>/<value><pages><dim>)"),
    COMMAND_HELP_COMMAND("command.help-command", "<value><command>"),
    COMMAND_HELP_SEPARATOR("command.help-separator", "<dim> : "),
    COMMAND_HELP_DESCRIPTION("command.help-description", "<subtext><description>"),
    COMMAND_HELP_FILL_HINT("command.help-fill-hint", "<subtext>Click to write this command."),
    COMMAND_HELP_PAGE_HINT("command.help-page-hint", "<subtext>Page <value><page>"),

    // The doctor: one line per thing that can be wrong, and a closing line that says whether anything
    // needs an operator. Every plugin of ours answers this in the same six lines.
    DOCTOR_HEADER("admin.doctor.header", "<h:'How this plugin is'>"),
    DOCTOR_OK("admin.doctor.ok", "<good>ok<body>  <value><check><body>: <subtext><said>"),
    DOCTOR_WARN("admin.doctor.warn", "<warn>warn<body>  <value><check><body>: <subtext><said>"),
    DOCTOR_FAIL("admin.doctor.fail", "<bad>fail<body>  <value><check><body>: <subtext><said>"),
    DOCTOR_WELL("admin.doctor.well", "<tag:'BLYSIS'> <body>Everything answered."),
    DOCTOR_UNWELL(
            "admin.doctor.unwell",
            "<etag:'ERROR'> <body>Something is wrong above. The console carries the whole of it.");
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
