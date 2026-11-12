package com.g4vrk.punishAddon.sequence;

import com.g4vrk.schedula.task.Task;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Owns pending punishments from scheduling until completion or the player's next join.
 */
public final class PendingActionManager {

    private final Map<UUID, List<Action>> pending = new HashMap<>();
    private boolean closed;

    public synchronized @NotNull Action track(final @NotNull UUID player, final @NotNull Consumer<Player> completion) {

        if (closed) {
            throw new IllegalStateException("The punishment addon is disabled");
        }
        final Action action = new Action(player, completion);
        pending.computeIfAbsent(player, ignored -> new ArrayList<>()).add(action);
        return action;

    }

    public synchronized void onQuit(final @NotNull UUID player) {

        final List<Action> actions = pending.get(player);
        if (actions != null) {
            actions.forEach(Action::defer);
        }

    }

    public void thenJoin(final @NotNull Player player) {

        final List<Action> actions;
        synchronized (this) {
            actions = pending.remove(player.getUniqueId());
            if (actions != null) {
                actions.forEach(Action::defer);
            }
        }

        if (actions == null) {
            return;
        }

        for (final Action action : actions) {
            action.completion.accept(player);
        }

    }

    public synchronized void clear() {

        closed = true;
        for (final List<Action> actions : pending.values()) {
            actions.forEach(Action::defer);
        }
        pending.clear();

    }

    private synchronized boolean remove(final @NotNull Action action, final boolean requireActive) {

        final List<Action> actions = pending.get(action.player);
        if (requireActive && !action.active() || actions == null || !actions.remove(action)) {
            return false;
        }
        if (actions.isEmpty()) {
            pending.remove(action.player);
        }
        action.defer();
        return true;

    }

    /**
     * One punishment and the scheduled task for the current connection.
     */
    public final class Action {

        private final UUID player;
        private final Consumer<Player> completion;
        private @Nullable Task task;
        private volatile boolean active = true;

        private Action(final @NotNull UUID player, final @NotNull Consumer<Player> completion) {

            this.player = player;
            this.completion = completion;

        }

        public boolean active() {

            return active;

        }

        public synchronized void attach(final @NotNull Task scheduled) {

            if (active) {
                task = scheduled;
            } else {
                scheduled.cancel();
            }

        }

        public synchronized void defer() {

            active = false;
            if (task != null) {
                task.cancel();
                task = null;
            }

        }

        public boolean complete(final @NotNull Player target) {

            if (!remove(this, true)) {
                return false;
            }
            completion.accept(target);
            return true;

        }

        public void discard() {

            remove(this, false);

        }

    }

}
