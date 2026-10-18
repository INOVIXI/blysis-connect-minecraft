package com.uxplima.blysis.domain;

/** Where this server's connection to the site stands. */
public enum LinkState {
    /** Saying hello. */
    CONNECTING,
    /** Polling, with the site answering. */
    CONNECTED,
    /** The site did not answer; trying again after a wait. Players get cached answers and no purchases. */
    UNREACHABLE,
    /** The site said this process must stop: a wrong token, a switched-off server, another process. */
    STOPPED
}
