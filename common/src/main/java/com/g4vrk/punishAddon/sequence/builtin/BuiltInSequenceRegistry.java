package com.g4vrk.punishAddon.sequence.builtin;

import com.g4vrk.punishAddon.sequence.Sequence;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registry of built-in punishment sequences shipped with the addon.
 */
public class BuiltInSequenceRegistry<S> {

    private final Map<String, Sequence<S>> builtIn = new LinkedHashMap<>();

    public BuiltInSequenceRegistry() {

    }

    void register(
            final @NotNull String id,
            final @NotNull Sequence<S> sequence
    ) {

        this.builtIn.put(id, sequence);

    }

    void populate(
            final @NotNull Map<String, Sequence<S>> builtIn
    ) {

        this.builtIn.putAll(builtIn);

    }

    public @Nullable Sequence<S> find(
            final @NotNull String id
    ) {

        return this.builtIn.get(id);

    }

    public @NotNull Map<String, Sequence<S>> all() {

        return Map.copyOf(builtIn);

    }

}
