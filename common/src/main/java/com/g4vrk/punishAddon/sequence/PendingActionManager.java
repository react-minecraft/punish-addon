package com.g4vrk.punishAddon.sequence;

import com.g4vrk.react.api.platform.player.PlatformPlayer;
import com.g4vrk.schedula.task.Task;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Owns pending punishments from scheduling until completion or the player's next join.
 */
@ApiStatus.Internal
public final class PendingActionManager {

    private final Logger logger;
    private final Map<UUID, List<Action>> pending = new HashMap<>();
    private boolean closed;

    public PendingActionManager(
            final @NotNull Logger logger
    ) {

        this.logger = Objects.requireNonNull(logger, "logger");

    }

    public synchronized @NotNull Action track(
            final @NotNull PlatformPlayer player,
            final @NotNull Consumer<PlatformPlayer> completion
    ) {

        if (this.closed) {
            throw new IllegalStateException("The punishment addon is disabled");
        }
        final UUID playerId = player.uniqueId();
        final Action action = new Action(
                playerId,
                player,
                Objects.requireNonNull(completion, "completion")
        );
        this.pending.computeIfAbsent(playerId, ignored -> new ArrayList<>()).add(action);
        return action;

    }

    /**
     * Defers stale active actions belonging to offline players and resumes deferred actions once a
     * matching player is back online, without interrupting active animations of online players.
     *
     * @param onlinePlayers currently connected players
     */
    public void pollOnline(
            final @NotNull Collection<? extends PlatformPlayer> onlinePlayers
    ) {

        final List<Map.Entry<PlatformPlayer, Action>> toResume = new ArrayList<>();
        synchronized (this) {
            if (this.closed || this.pending.isEmpty()) {
                return;
            }
            final Map<UUID, PlatformPlayer> onlineById = new HashMap<>(onlinePlayers.size() * 2 + 1);
            for (final PlatformPlayer online : onlinePlayers) {
                if (online.online()) {
                    onlineById.put(online.uniqueId(), online);
                }
            }
            final Iterator<Map.Entry<UUID, List<Action>>> mapIterator = this.pending.entrySet().iterator();
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
                    if (action.state == State.ACTIVE
                            && (!action.owner.online() || action.owner.entityId() != online.entityId())) {
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
            try {
                item.getValue().completion.accept(item.getKey());
            } catch (final RuntimeException failure) {
                this.logger.error("Could not resume deferred punishment for {}", item.getKey().uniqueId(), failure);
            }
        }

    }

    public synchronized void clear() {

        this.closed = true;
        for (final List<Action> actions : this.pending.values()) {
            actions.forEach(Action::markCompletedAndCancel);
        }
        this.pending.clear();

    }

    private synchronized boolean remove(
            final @NotNull Action action,
            final boolean requireActive
    ) {

        final List<Action> actions = this.pending.get(action.player);
        if (requireActive && !action.active() || actions == null || !actions.remove(action)) {
            return false;
        }
        if (actions.isEmpty()) {
            this.pending.remove(action.player);
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
        private final PlatformPlayer owner;
        private final Consumer<PlatformPlayer> completion;
        private @Nullable Task task;
        private volatile State state = State.ACTIVE;

        private Action(
                final @NotNull UUID player,
                final @NotNull PlatformPlayer owner,
                final @NotNull Consumer<PlatformPlayer> completion
        ) {

            this.player = player;
            this.owner = owner;
            this.completion = completion;

        }

        public boolean active() {

            return this.state == State.ACTIVE;

        }

        public boolean deferred() {

            return this.state == State.DEFERRED;

        }

        public synchronized void attach(
                final @NotNull Task scheduled
        ) {

            if (this.state == State.ACTIVE) {
                this.task = scheduled;
            } else {
                scheduled.cancel();
            }

        }

        public synchronized void defer() {

            if (this.state != State.ACTIVE) {
                return;
            }
            this.state = State.DEFERRED;
            cancelAttachedTask();

        }

        private synchronized void markCompleted() {

            this.state = State.COMPLETED;

        }

        private synchronized void markCompletedAndCancel() {

            this.state = State.COMPLETED;
            cancelAttachedTask();

        }

        private void cancelAttachedTask() {

            if (this.task != null) {
                this.task.cancel();
                this.task = null;
            }

        }

        public boolean complete(
                final @NotNull PlatformPlayer target
        ) {

            synchronized (PendingActionManager.this) {
                if (!remove(this, true)) {
                    return false;
                }
            }
            this.completion.accept(target);
            return true;

        }

        public void discard() {

            remove(this, false);

        }

    }

}
