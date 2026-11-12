package com.g4vrk.punishAddon.action;

import com.g4vrk.functionalActions.ExecutableAction;
import com.g4vrk.functionalActions.impl.UncontextualAction;
import com.g4vrk.schedula.task.TickSchedule;
import com.g4vrk.schedula.task.scheduler.Scheduler;
import net.kyori.adventure.audience.Audience;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.Iterator;
import java.util.function.UnaryOperator;

/**
 * Runs configured actions in order on the player or global scheduler as required.
 */
public final class PunishmentActionRunner {

    private static final TickSchedule NEXT_TICK = TickSchedule.instant();
    private static final UnaryOperator<String> UNCHANGED = UnaryOperator.identity();

    private final Scheduler scheduler;
    private final Logger logger;

    public PunishmentActionRunner(final @NotNull Scheduler scheduler, final @NotNull Logger logger) {

        this.scheduler = scheduler;
        this.logger = logger;

    }

    public void run(
            final @NotNull Player player,
            final @NotNull Iterable<? extends ExecutableAction<? super Audience>> actions
    ) {

        run(player, actions, UNCHANGED);

    }

    public void run(
            final @NotNull Player player,
            final @NotNull Iterable<? extends ExecutableAction<? super Audience>> actions,
            final @NotNull UnaryOperator<String> placeholders
    ) {

        final Iterator<? extends ExecutableAction<? super Audience>> remaining = actions.iterator();
        if (remaining.hasNext()) {
            run(player, remaining.next(), remaining, placeholders, false);
        }

    }

    private void run(
            final @NotNull Player player,
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
