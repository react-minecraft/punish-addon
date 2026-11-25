package com.g4vrk.punishAddon.sequence;

import com.g4vrk.react.api.platform.player.PlatformPlayer;
import com.g4vrk.schedula.task.Task;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Owns pending punishments from scheduling until completion or the player's next join.
 */
public final class PendingActionManager {

    private final Map<UUID, List<Action>> pending = new HashMap<>();
    private boolean closed;

    public synchronized @NotNull Action track(
            final @NotNull UUID player,
            final @NotNull Consumer<PlatformPlayer> completion
    ) {

        return trackInternal(player, null, completion);

    }

    public synchronized @NotNull Action track(
            final @NotNull PlatformPlayer player,
            final @NotNull Consumer<PlatformPlayer> completion
    ) {

        return trackInternal(player.uniqueId(), player, completion);

    }

    private Action trackInternal(
            final @NotNull UUID player,
            final @Nullable PlatformPlayer owner,
            final @NotNull Consumer<PlatformPlayer> completion
    ) {

        if (closed) {
            throw new IllegalStateException("The punishment addon is disabled");
        }
        final Action action = new Action(
                player,
                owner,
                Objects.requireNonNull(completion, "completion")
        );
        pending.computeIfAbsent(player, ignored -> new ArrayList<>()).add(action);
        return action;

    }

    public synchronized void onQuit(final @NotNull UUID player) {

        final List<Action> actions = pending.get(player);
        if (actions != null) {
            actions.forEach(Action::defer);
        }

    }

    public void thenJoin(final @NotNull PlatformPlayer player) {

        final List<Action> ready = collectDeferred(player.uniqueId());
        if (ready == null) {
            return;
        }
        for (final Action action : ready) {
            action.completion.accept(player);
        }

    }

    /**
     * Defers stale active actions belonging to offline players and resumes deferred actions once a
     * matching player is back online, without interrupting active animations of online players.
     *
     * @param onlinePlayers currently connected players
     */
    public void pollOnline(final @NotNull Collection<? extends PlatformPlayer> onlinePlayers) {

        final List<Map.Entry<PlatformPlayer, Action>> toResume = new ArrayList<>();
        synchronized (this) {
            if (closed || pending.isEmpty()) {
                return;
            }
            final Map<UUID, PlatformPlayer> onlineById = new HashMap<>(onlinePlayers.size() * 2 + 1);
            for (final PlatformPlayer online : onlinePlayers) {
                if (online.online()) {
                    onlineById.put(online.uniqueId(), online);
                }
            }
            final Iterator<Map.Entry<UUID, List<Action>>> mapIterator = pending.entrySet().iterator();
            while (mapIterator.hasNext()) {
                final Map.Entry<UUID, List<Action>> entry = mapIterator.next();
                final PlatformPlayer online = onlineById.get(entry.getKey());
                final List<Action> actions = entry.getValue();
                if (online == null) {
                    actions.forEach(Action::defer);
                    continue;
                }
                final Iterator<Action> listIterator = actions.iterator();
                while (listIterator.hasNext()) {
                    final Action action = listIterator.next();
                    if (action.state == State.ACTIVE && action.owner != null && (!action.owner.online() || action.owner != online)) {
                        action.defer();
                    }
                    if (action.state == State.DEFERRED) {
                        action.markCompleted();
                        listIterator.remove();
                        toResume.add(Map.entry(online, action));
                    }
                }
                if (actions.isEmpty()) {
                    mapIterator.remove();
                }
            }
        }
        for (final Map.Entry<PlatformPlayer, Action> item : toResume) {
            item.getValue().completion.accept(item.getKey());
        }

    }

    public synchronized void clear() {

        closed = true;
        for (final List<Action> actions : pending.values()) {
            actions.forEach(Action::markCompletedAndCancel);
        }
        pending.clear();

    }

    private synchronized @Nullable List<Action> collectDeferred(final @NotNull UUID player) {

        final List<Action> actions = pending.get(player);
        if (actions == null || actions.isEmpty()) {
            return null;
        }
        final List<Action> ready = new ArrayList<>();
        final Iterator<Action> iterator = actions.iterator();
        while (iterator.hasNext()) {
            final Action action = iterator.next();
            if (action.state == State.DEFERRED) {
                action.markCompleted();
                iterator.remove();
                ready.add(action);
            }
        }
        if (actions.isEmpty()) {
            pending.remove(player);
        }
        return ready.isEmpty() ? null : ready;

    }

    private synchronized boolean remove(final @NotNull Action action, final boolean requireActive) {

        final List<Action> actions = pending.get(action.player);
        if (requireActive && !action.active() || actions == null || !actions.remove(action)) {
            return false;
        }
        if (actions.isEmpty()) {
            pending.remove(action.player);
        }
        action.markCompletedAndCancel();
        return true;

    }

    private enum State {

        ACTIVE,
        DEFERRED,
        COMPLETED

    }

    /**
     * One punishment and the scheduled task for the current connection.
     */
    public final class Action {

        private final UUID player;
        private final @Nullable PlatformPlayer owner;
        private final Consumer<PlatformPlayer> completion;
        private @Nullable Task task;
        private volatile State state = State.ACTIVE;

        private Action(
                final @NotNull UUID player,
                final @Nullable PlatformPlayer owner,
                final @NotNull Consumer<PlatformPlayer> completion
        ) {

            this.player = player;
            this.owner = owner;
            this.completion = completion;

        }

        public boolean active() {

            return state == State.ACTIVE;

        }

        public boolean deferred() {

            return state == State.DEFERRED;

        }

        public synchronized void attach(final @NotNull Task scheduled) {

            if (state == State.ACTIVE) {
                task = scheduled;
            } else {
                scheduled.cancel();
            }

        }

        public synchronized void defer() {

            if (state != State.ACTIVE) {
                return;
            }
            state = State.DEFERRED;
            cancelAttachedTask();

        }

        private synchronized void markCompleted() {

            state = State.COMPLETED;

        }

        private synchronized void markCompletedAndCancel() {

            state = State.COMPLETED;
            cancelAttachedTask();

        }

        private void cancelAttachedTask() {

            if (task != null) {
                task.cancel();
                task = null;
            }

        }

        public boolean complete(final @NotNull PlatformPlayer target) {

            synchronized (PendingActionManager.this) {
                if (!remove(this, true)) {
                    return false;
                }
            }
            completion.accept(target);
            return true;

        }

        public void discard() {

            remove(this, false);

        }

    }

}
