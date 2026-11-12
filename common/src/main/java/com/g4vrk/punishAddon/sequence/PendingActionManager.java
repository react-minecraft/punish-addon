package com.g4vrk.punishAddon.sequence;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Actions that wait for a player who left in the middle of an animation.
 *
 * <p>Animations tick on region threads on Folia and joins arrive on others, so the map is
 * concurrent.</p>
 */
public final class PendingActionManager {

    private final Map<UUID, List<Consumer<Player>>> pending = new ConcurrentHashMap<>();

    public void add(final @NotNull UUID player, final @NotNull Consumer<Player> action) {

        this.pending.compute(player, (ignored, existing) -> {
            final List<Consumer<Player>> actions = existing == null ? new ArrayList<>() : existing;
            actions.add(action);
            return actions;
        });

    }

    public void thenJoin(final @NotNull Player player) {

        final List<Consumer<Player>> actions = this.pending.remove(player.getUniqueId());

        if (actions == null) {
            return;
        }

        for (final Consumer<Player> action : actions) {
            action.accept(player);
        }

    }

    public void clear() {

        this.pending.clear();

    }

}
