package com.uxplima.blysis.application;

import java.util.Objects;
import java.util.function.Supplier;

import com.uxplima.blysis.application.port.SiteRefused;
import com.uxplima.blysis.application.port.SiteUnavailable;
import org.jspecify.annotations.Nullable;

/**
 * What the site said to one thing a player asked: the answer, a refusal with its code, or nothing because it
 * could not be reached. A menu or a command turns each into what the player reads, and the compiler makes it
 * handle all three.
 */
public sealed interface Answer<T> {

    /** The site answered. */
    record Given<T>(T value) implements Answer<T> {
        public Given {
            Objects.requireNonNull(value, "value");
        }
    }

    /** The site understood and said no, with its stable code and, for a product question, the field. */
    record Refused<T>(String code, @Nullable String field) implements Answer<T> {
        public Refused {
            Objects.requireNonNull(code, "code");
        }
    }

    /** The site could not be reached, or did not answer in time. */
    record Away<T>(String reason) implements Answer<T> {
        public Away {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /** Asks once, and reads every way it can end. */
    static <T> Answer<T> of(Supplier<T> call) {
        Objects.requireNonNull(call, "call");
        try {
            return new Given<>(call.get());
        } catch (SiteRefused refused) {
            return new Refused<>(refused.code(), refused.field());
        } catch (SiteUnavailable away) {
            return new Away<>(String.valueOf(away.getMessage()));
        }
    }
}
