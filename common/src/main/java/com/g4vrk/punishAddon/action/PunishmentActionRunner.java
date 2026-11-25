package com.g4vrk.punishAddon.action;

import com.g4vrk.functionalActions.ExecutableAction;
import com.g4vrk.functionalActions.impl.UncontextualAction;
import com.g4vrk.punishAddon.sequence.PendingActionManager;
import com.g4vrk.react.api.platform.player.PlatformPlayer;
import com.g4vrk.schedula.task.Task;
import com.g4vrk.schedula.task.TickSchedule;
import com.g4vrk.schedula.task.scheduler.TaskScheduler;
import net.kyori.adventure.audience.Audience;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Iterator;
import java.util.function.UnaryOperator;

/**
 * Runs configured actions in order on the player or global scheduler as required.
 */
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
     */
    public void runTracked(
            final @NotNull PlatformPlayer player,
            final @NotNull Iterable<? extends ExecutableAction<? super Audience>> before,
            final @NotNull Iterable<? extends ExecutableAction<? super Audience>> punishment,
            final @NotNull UnaryOperator<String> placeholders
    ) {

        new TrackedRun(before.iterator(), punishment.iterator(), placeholders).resume(player);

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

        private synchronized void resume(final @NotNull PlatformPlayer player) {

            final PendingActionManager.Action action = pending.track(player, this::resume);
            this.pendingAction = action;
            try {
                action.attach(scheduler.scheduleEntity(player, () -> advance(player, action, false), NEXT_TICK));
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

            if (!player.online()) {
                action.defer();
                return;
            }

            while (this.pendingAction == action && action.active()) {
                if (this.current == null) {
                    if (this.beforeStage && this.before.hasNext()) {
                        this.current = this.before.next();
                    } else {
                        this.beforeStage = false;
                        if (!this.punishment.hasNext()) {
                            action.discard();
                            return;
                        }
                        this.current = this.punishment.next();
                    }
                }

                final boolean requiresGlobal = this.current.action() instanceof UncontextualAction;
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
            if (requiresGlobal != global) {
                final ExecutableAction<? super Audience> next = action;
                final Runnable continuation = () -> run(player, next, remaining, placeholders, requiresGlobal);
                if (requiresGlobal) {
                    scheduler.schedule(continuation, NEXT_TICK);
                } else {
                    scheduler.scheduleEntity(player, continuation, NEXT_TICK);
                }
                return;
            }
            try {
                action.execute(player, placeholders);
            } catch (final RuntimeException failure) {
                logger.error("Could not run a punishment action", failure);
            }
            if (!remaining.hasNext()) {
                return;
            }
            action = remaining.next();
        }

    }

}
