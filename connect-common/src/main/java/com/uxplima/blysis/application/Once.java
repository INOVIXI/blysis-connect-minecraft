package com.uxplima.blysis.application;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * One player action that moves credits or claims something, done once however the network behaves.
 *
 * <p>The key is made here, once, and sent with every attempt. A call that timed out may have gone through,
 * so it is asked again with the same key and the site hands back what it did the first time rather than
 * doing it twice. A site still busy with the first attempt answers {@code in_progress}, which is asked again
 * too. Anything else is the answer.
 */
public final class Once {

    /** The waits before the second and the third attempt. */
    private static final List<Duration> RETRIES = List.of(Duration.ofSeconds(1), Duration.ofSeconds(3));

    private final Sleeper sleeper;

    public Once(Sleeper sleeper) {
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
    }

    /** A fresh key for one action. */
    public static String key() {
        return UUID.randomUUID().toString();
    }

    /** Does the action with one key, asking again while the outcome is unknown. */
    public <T> Answer<T> run(Function<String, T> call) {
        Objects.requireNonNull(call, "call");
        String key = key();
        Answer<T> answer = Answer.of(() -> call.apply(key));
        for (Duration wait : RETRIES) {
            if (!unknown(answer)) {
                return answer;
            }
            if (!sleeper.sleep(wait)) {
                return answer;
            }
            answer = Answer.of(() -> call.apply(key));
        }
        return answer;
    }

    private static boolean unknown(Answer<?> answer) {
        return answer instanceof Answer.Away<?>
                || (answer instanceof Answer.Refused<?> refused
                        && refused.code().equals("in_progress"));
    }

    /** Waits on the calling thread, which is never a server thread. False when the wait was cut short. */
    @FunctionalInterface
    public interface Sleeper {
        boolean sleep(Duration duration);

        /** The real clock. */
        static Sleeper real() {
            return duration -> {
                try {
                    Thread.sleep(duration);
                    return true;
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            };
        }
    }
}
