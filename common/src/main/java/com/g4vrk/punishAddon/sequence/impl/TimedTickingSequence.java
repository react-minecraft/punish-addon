package com.g4vrk.punishAddon.sequence.impl;

import com.g4vrk.punishAddon.sequence.Sequence;
import com.g4vrk.schedula.task.Task;
import com.g4vrk.schedula.task.TickSchedule;
import com.g4vrk.schedula.task.scheduler.Scheduler;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

public abstract class TimedTickingSequence implements Sequence<Player> {

    private final long duration;
    private final TickSchedule tickSchedule;
    private final Scheduler scheduler;

    protected TimedTickingSequence(
            final long duration,
            final @NotNull Scheduler scheduler
    ) {

        this.duration = duration;
        this.scheduler = scheduler;
        this.tickSchedule = TickSchedule.repeating(2, 1);

    }

    protected final void scheduleVisualSequence(
            final @NotNull BiConsumer<Task, Long> tickConsumer,
            final @NotNull BiConsumer<Task, Long> endConsumer
    ) {

        if (duration <= 0L) {
            return;
        }

        final AtomicReference<Task> taskReference = new AtomicReference<>();
        final AtomicLong elapsed = new AtomicLong();

        final Task task = this.scheduler.schedule(
                () -> {

                    final Task currentTask = taskReference.get();

                    if (currentTask == null || currentTask.isCancelled()) {
                        return;
                    }

                    final long currentTick = elapsed.getAndIncrement();

                    tickConsumer.accept(
                            currentTask,
                            currentTick
                    );

                    if (currentTask.isCancelled()) {
                        return;
                    }

                    if (currentTick + 1L >= duration) {

                        currentTask.cancel();

                        endConsumer.accept(
                                currentTask,
                                currentTick
                        );

                    }

                },
                tickSchedule
        );

        taskReference.set(task);

    }

    public long duration() {
        return duration;
    }
}
