package com.g4vrk.punishAddon.sequence.impl;

import com.g4vrk.punishAddon.sequence.PendingActionManager;
import com.g4vrk.punishAddon.sequence.Sequence;
import com.g4vrk.schedula.task.Task;
import com.g4vrk.schedula.task.TickSchedule;
import com.g4vrk.schedula.task.scheduler.Scheduler;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;
import java.util.function.LongConsumer;

/**
 * Runs an animation from start to completion on the target player's entity scheduler.
 */
public abstract class TimedTickingSequence implements Sequence<Player> {

    private static final TickSchedule TICK_SCHEDULE = TickSchedule.repeating(1, 1);

    private final long duration;
    private final Scheduler scheduler;

    protected TimedTickingSequence(
            final long duration,
            final @NotNull Scheduler scheduler
    ) {

        this.duration = duration;
        this.scheduler = scheduler;

    }

    protected final void scheduleVisualSequence(
            final @NotNull Player player,
            final @NotNull PendingActionManager.Action action,
            final @NotNull Runnable onStart,
            final @NotNull LongConsumer onTick,
            final @NotNull Runnable onEnd
    ) {

        final Task task;
        try {
            task = scheduler.scheduleEntity(player, new Consumer<Task>() {

                private long elapsed = -2L;

                @Override
                public void accept(final @NotNull Task current) {

                    if (!action.active() || !player.isOnline()) {
                        action.defer();
                        current.cancel();
                        return;
                    }
                    try {
                        final long tick = elapsed++;
                        if (tick == -2L) {
                            onStart.run();
                        }
                        if (tick < 0L) {
                            return;
                        }
                        if (tick < duration) {
                            onTick.accept(tick);
                        }
                        if (tick + 1L >= duration) {
                            current.cancel();
                            onEnd.run();
                        }
                    } catch (final RuntimeException | Error failure) {
                        action.discard();
                        throw failure;
                    }

                }

            }, TICK_SCHEDULE);
        } catch (final RuntimeException | Error failure) {
            action.discard();
            throw failure;
        }
        action.attach(task);

    }

    public long duration() {

        return duration;

    }

}
