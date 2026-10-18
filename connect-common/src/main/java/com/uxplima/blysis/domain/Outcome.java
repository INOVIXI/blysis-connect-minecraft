package com.uxplima.blysis.domain;

/** What happened to a job, in the words the site reads. */
public enum Outcome {
    /** The command ran. */
    DONE("done"),
    /** The command was refused, blocked, or skipped after an earlier one of its purchase failed. */
    FAILED("failed"),
    /** The player left before it ran. The site offers it again when they are back. */
    DEFERRED("deferred"),
    /** It was started and the process stopped before its result was written. Staff decide. */
    UNKNOWN("unknown");

    private final String wire;

    Outcome(String wire) {
        this.wire = wire;
    }

    /** The word the protocol uses. */
    public String wire() {
        return wire;
    }
}
