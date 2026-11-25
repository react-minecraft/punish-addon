package com.g4vrk.punishAddon.sequence.builtin;

import com.g4vrk.punishAddon.sequence.Sequence;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registry of built-in punishment sequences shipped with the addon.
 */
@ApiStatus.Internal
public final class BuiltInSequenceRegistry<S> {

    private final Map<String, Sequence<S>> builtIn = new LinkedHashMap<>();

    public BuiltInSequenceRegistry() {

    }

    void register(
            final @NotNull String id,
            final @NotNull Sequence<S> sequence
    ) {

        this.builtIn.put(id, sequence);

    }

    public @NotNull Map<String, Sequence<S>> all() {

        return Map.copyOf(this.builtIn);

    }

}
