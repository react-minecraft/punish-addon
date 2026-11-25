package com.g4vrk.punishAddon.action;

import com.g4vrk.functionalActions.ExecutableAction;
import com.g4vrk.functionalActions.impl.UncontextualAction;
import com.g4vrk.punishAddon.sequence.PendingActionManager;
import com.g4vrk.react.api.platform.player.PlatformPlayer;
import com.g4vrk.schedula.task.Task;
import com.g4vrk.schedula.task.TickSchedule;
import com.g4vrk.schedula.task.scheduler.TaskScheduler;
import net.kyori.adventure.audience.Audience;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Iterator;
import java.util.function.UnaryOperator;

/**
 * Runs configured actions in order on the player or global scheduler as required.
 */
@ApiStatus.Internal
public final class PunishmentActionRunner {

    private static final TickSchedule NEXT_TICK = TickSchedule.instant();
    private static final UnaryOperator<String> UNCHANGED = UnaryOperator.identity();

    private final TaskScheduler<PlatformPlayer> scheduler;
    private final Logger logger;
    private final PendingActionManager pending;

    public PunishmentActionRunner(
            final @NotNull TaskScheduler<PlatformPlayer> scheduler,
            final @NotNull Logger logger,
            final @NotNull PendingActionManager pending
    ) {

        this.scheduler = scheduler;
        this.logger = logger;
        this.pending = pending;

    }

    /**
     * Runs the final animation and punishment actions as one resumable sequence.
     *
     * @param player       target player
     * @param before       animation-end actions
     * @param punishment   configured punishment actions
     * @param placeholders placeholder replacer for punishment actions
     */
    public void runTracked(
            final @NotNull PlatformPlayer player,
            final @NotNull Iterable<? extends ExecutableAction<? super Audience>> before,
            final @NotNull Iterable<? extends ExecutableAction<? super Audience>> punishment,
            final @NotNull UnaryOperator<String> placeholders
    ) {

        new TrackedRun(before.iterator(), punishment.iterator(), placeholders).resume(player);

    }

    public void run(
            final @NotNull PlatformPlayer player,
            final @NotNull Iterable<? extends ExecutableAction<? super Audience>> actions
    ) {

        run(player, actions, UNCHANGED);

    }

    public void run(
            final @NotNull PlatformPlayer player,
            final @NotNull Iterable<? extends ExecutableAction<? super Audience>> actions,
            final @NotNull UnaryOperator<String> placeholders
    ) {

        final Iterator<? extends ExecutableAction<? super Audience>> remaining = actions.iterator();
        if (remaining.hasNext()) {
            run(player, remaining.next(), remaining, placeholders, false);
        }

    }

    private void run(
            final @NotNull PlatformPlayer player,
            final @NotNull ExecutableAction<? super Audience> first,
            final @NotNull Iterator<? extends ExecutableAction<? super Audience>> remaining,
            final @NotNull UnaryOperator<String> placeholders,
            final boolean global
    ) {

        ExecutableAction<? super Audience> action = first;
        while (true) {
            final boolean requiresGlobal = action.action() instanceof UncontextualAction;
            if (!requiresGlobal && !player.online()) {
                return;
            }
            if (requiresGlobal != global) {
                final ExecutableAction<? super Audience> next = action;
                final Runnable continuation = () -> run(player, next, remaining, placeholders, requiresGlobal);
                if (requiresGlobal) {
                    this.scheduler.schedule(continuation, NEXT_TICK);
                } else {
                    this.scheduler.scheduleEntity(player, continuation, NEXT_TICK);
                }
                return;
            }
            try {
                action.execute(player, placeholders);
            } catch (final RuntimeException failure) {
                this.logger.error("Could not run a punishment action", failure);
            }
            if (!remaining.hasNext()) {
                return;
            }
            action = remaining.next();
        }

    }

    /**
     * Retains the current action until it has run, including across reconnects.
     */
    private final class TrackedRun {

        private final Iterator<? extends ExecutableAction<? super Audience>> before;
        private final Iterator<? extends ExecutableAction<? super Audience>> punishment;
        private final UnaryOperator<String> placeholders;
        private @Nullable ExecutableAction<? super Audience> current;
        private boolean beforeStage = true;
        private @Nullable PendingActionManager.Action pendingAction;

        private TrackedRun(
                final @NotNull Iterator<? extends ExecutableAction<? super Audience>> before,
                final @NotNull Iterator<? extends ExecutableAction<? super Audience>> punishment,
                final @NotNull UnaryOperator<String> placeholders
        ) {

            this.before = before;
            this.punishment = punishment;
            this.placeholders = placeholders;

        }

        private synchronized void resume(
                final @NotNull PlatformPlayer player
        ) {

            final PendingActionManager.Action action = pending.track(player, this::resume);
            this.pendingAction = action;
            if (!loadNextAction()) {
                action.discard();
                return;
            }
            final boolean requiresGlobal = this.current.action() instanceof UncontextualAction;
            if (!requiresGlobal && !player.online()) {
                action.defer();
                return;
            }
            try {
                final Runnable continuation = () -> advance(player, action, requiresGlobal);
                final Task task = requiresGlobal
                        ? scheduler.schedule(continuation, NEXT_TICK)
                        : scheduler.scheduleEntity(player, continuation, NEXT_TICK);
                action.attach(task);
            } catch (final RuntimeException | Error failure) {
                action.defer();
                throw failure;
            }

        }

        private synchronized void advance(
                final @NotNull PlatformPlayer player,
                final @NotNull PendingActionManager.Action action,
                final boolean global
        ) {

            while (this.pendingAction == action && action.active()) {
                if (!loadNextAction()) {
                    action.discard();
                    return;
                }
                final boolean requiresGlobal = this.current.action() instanceof UncontextualAction;
                if (!requiresGlobal && !player.online()) {
                    action.defer();
                    return;
                }
                if (requiresGlobal != global) {
                    final Runnable continuation = () -> advance(player, action, requiresGlobal);
                    try {
                        final Task task = requiresGlobal
                                ? scheduler.schedule(continuation, NEXT_TICK)
                                : scheduler.scheduleEntity(player, continuation, NEXT_TICK);
                        action.attach(task);
                    } catch (final RuntimeException | Error failure) {
                        action.defer();
                        throw failure;
                    }
                    return;
                }
                try {
                    this.current.execute(player, this.beforeStage ? UNCHANGED : this.placeholders);
                } catch (final RuntimeException failure) {
                    logger.error("Could not run a punishment action", failure);
                }
                this.current = null;
            }

        }

        private boolean loadNextAction() {

            if (this.current != null) {
                return true;
            }
            if (this.beforeStage && this.before.hasNext()) {
                this.current = this.before.next();
                return true;
            }
            this.beforeStage = false;
            if (!this.punishment.hasNext()) {
                return false;
            }
            this.current = this.punishment.next();
            return true;

        }

    }

}
