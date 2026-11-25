package com.g4vrk.punishAddon.sequence.impl;

import com.g4vrk.punishAddon.sequence.PendingActionManager;
import com.g4vrk.punishAddon.sequence.Sequence;
import com.g4vrk.react.api.platform.player.PlatformPlayer;
import com.g4vrk.schedula.task.Task;
import com.g4vrk.schedula.task.TickSchedule;
import com.g4vrk.schedula.task.scheduler.TaskScheduler;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;
import java.util.function.LongConsumer;

/**
 * Runs an animation from start to completion on the target player's entity scheduler.
 */
@ApiStatus.AvailableSince("1.0")
public abstract class TimedTickingSequence implements Sequence<PlatformPlayer> {

    private static final TickSchedule TICK_SCHEDULE = TickSchedule.repeating(1, 1);

    private final long duration;
    private final TaskScheduler<PlatformPlayer> scheduler;

    protected TimedTickingSequence(
            final long duration,
            final @NotNull TaskScheduler<PlatformPlayer> scheduler
    ) {

        if (duration <= 0L) {
            throw new IllegalArgumentException("Sequence duration must be positive: " + duration);
        }
        this.duration = duration;
        this.scheduler = scheduler;

    }

    protected final void scheduleVisualSequence(
            final @NotNull PlatformPlayer player,
            final @NotNull PendingActionManager.Action action,
            final @NotNull Runnable onStart,
            final @NotNull LongConsumer onTick,
            final @NotNull Runnable onEnd
    ) {

        final Task task;
        try {
            task = this.scheduler.scheduleEntity(player, new Consumer<Task>() {

                private long elapsed = -1L;

                @Override
                public void accept(
                        final @NotNull Task current
                ) {

                    if (!action.active() || !player.online()) {
                        action.defer();
                        current.cancel();
                        return;
                    }
                    try {
                        final long tick = this.elapsed++;
                        if (tick < 0L) {
                            onStart.run();
                            return;
                        }
                        if (tick < TimedTickingSequence.this.duration) {
                            onTick.accept(tick);
                        }
                        if (tick + 1L >= TimedTickingSequence.this.duration) {
                            current.cancel();
                            onEnd.run();
                        }
                    } catch (final RuntimeException | Error failure) {
                        current.cancel();
                        if (player.online()) {
                            action.complete(player);
                        } else {
                            action.defer();
                        }
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

        return this.duration;

    }

}
