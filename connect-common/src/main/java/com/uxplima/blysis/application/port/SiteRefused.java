package com.uxplima.blysis.application.port;

import java.io.Serial;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * The site understood the call and said no, with a stable code: a wrong token, a switched-off server, a
 * superseded process, a refused purchase. Unlike {@link SiteUnavailable}, trying again changes nothing.
 */
public final class SiteRefused extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int status;
    private final String code;
    private final @Nullable String field;

    public SiteRefused(int status, String code, String message) {
        this(status, code, message, null);
    }

    public SiteRefused(int status, String code, String message, @Nullable String field) {
        super(message);
        this.status = status;
        this.code = Objects.requireNonNull(code, "code");
        this.field = field;
    }

    /** The question of a product the answers did not satisfy, for {@code field_invalid}. */
    public @Nullable String field() {
        return field;
    }

    /** The HTTP status the site answered with. */
    public int status() {
        return status;
    }

    /** The site's stable code, such as {@code superseded} or {@code insufficient_balance}. */
    public String code() {
        return code;
    }

    /** True when this process must stop calling: the token is gone, the server is off, or it was replaced. */
    public boolean stopsTheConnection() {
        return status == 401 || code.equals("server_disabled") || code.equals("superseded") || status == 426;
    }
}
