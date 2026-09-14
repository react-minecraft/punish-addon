package com.g4vrk.punishAddon.sequence.builtin;

import com.g4vrk.punishAddon.sequence.Sequence;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class BuiltInSequenceRegistry<S> {

    private final Map<String, Sequence<S>> builtIn = new Object2ObjectOpenHashMap<>();

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

    public @NotNull Sequence<S> get(
            final @NotNull String id
    ) {

        return this.builtIn.get(id);

    }

    public @NotNull Map<String, Sequence<S>> all() {

        return new Object2ObjectOpenHashMap<>(builtIn);

    }


}
