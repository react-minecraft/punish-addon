package com.g4vrk.punishAddon.sequence;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Actions that wait for a player who left in the middle of an animation.
 *
 * <p>Animations tick on region threads on Folia and joins arrive on others, so the map is
 * concurrent.</p>
 */
public final class PendingActionManager {

    private final Map<UUID, List<Runnable>> pending = new ConcurrentHashMap<>();

    public void add(final @NotNull Player player, final @NotNull Runnable action) {
        this.pending.computeIfAbsent(player.getUniqueId(), ignored -> new CopyOnWriteArrayList<>()).add(action);
    }

    public void thenJoin(final @NotNull Player player) {
        final List<Runnable> actions = this.pending.remove(player.getUniqueId());

        if (actions == null) {
            return;
        }

        for (final Runnable action : actions) {
            action.run();
        }
    }

    public void clear() {
        this.pending.clear();
    }

}
