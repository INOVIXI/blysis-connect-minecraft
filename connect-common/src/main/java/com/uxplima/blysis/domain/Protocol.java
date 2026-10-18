package com.uxplima.blysis.domain;

/**
 * The version of the site's game-connect protocol this client speaks, and the header that carries it.
 *
 * <p>The site refuses a call without the header, and answers a version it does not speak with 426, so a
 * client and a site that drifted apart say so on the first call rather than misreading each other.
 */
public final class Protocol {

    /** The protocol version this client was written against. */
    public static final int VERSION = 1;

    /** The header every call carries. */
    public static final String HEADER = "X-Game-Connect-Protocol";

    /** The path every endpoint lives under, after the site's address. */
    public static final String PREFIX = "/api/v1/game-connect/";

    private Protocol() {}

    /** The full address of one endpoint on a site, with or without a trailing slash on the site. */
    public static String endpoint(String site, String path) {
        String base = site.endsWith("/") ? site.substring(0, site.length() - 1) : site;
        String tail = path.startsWith("/") ? path.substring(1) : path;
        return base + PREFIX + tail;
    }
}
