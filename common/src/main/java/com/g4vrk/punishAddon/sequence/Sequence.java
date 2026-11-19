package com.g4vrk.punishAddon.sequence;

import com.g4vrk.functionalActions.ExecutableAction;
import net.kyori.adventure.audience.Audience;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiConsumer;

public interface Sequence<S> {

    @NotNull SequenceSession newSession(
            final @NotNull S source,
            final @NotNull BiConsumer<S, Iterable<? extends ExecutableAction<? super Audience>>> onEnd
    );

}
