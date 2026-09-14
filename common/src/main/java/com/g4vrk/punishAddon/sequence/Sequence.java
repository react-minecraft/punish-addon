package com.g4vrk.punishAddon.sequence;

import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public interface Sequence<S> {

    @NotNull SequenceSession newSession(
            final @NotNull S source,
            final @NotNull Consumer<S> onEnd
    );

}
