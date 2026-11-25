package com.g4vrk.punishAddon.sequence;

import org.jetbrains.annotations.ApiStatus;

/**
 * Lifecycle handle for a single running punishment animation session.
 */
@FunctionalInterface
@ApiStatus.AvailableSince("1.0")
public interface SequenceSession {

    void start();

}
