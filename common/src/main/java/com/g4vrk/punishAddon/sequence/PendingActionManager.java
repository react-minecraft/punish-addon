package com.g4vrk.punishAddon.sequence;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class PendingActionManager {

    private final Map<UUID, List<Runnable>> pending = new HashMap<>();

    public void add(
            final @NotNull Player player,
            final @NotNull Runnable action
    ) {

        this.pending.computeIfAbsent(player.getUniqueId(), k -> new ObjectArrayList<>()).add(action);

    }

    public void addAll(
            final @NotNull Player player,
            final @NotNull Iterable<Runnable> actions
    ) {

        for (final Runnable action : actions) {

            this.add(player, action);

        }

    }

    public void thenJoin(
            final @NotNull Player player
    ) {

        final List<Runnable> actions = pending.remove(player.getUniqueId());

        if (actions != null && !actions.isEmpty()) {

            for (final Runnable action : actions) {

                action.run();

            }

        }

    }

}
