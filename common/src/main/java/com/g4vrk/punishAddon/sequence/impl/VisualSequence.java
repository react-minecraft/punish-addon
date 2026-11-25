package com.g4vrk.punishAddon.sequence.impl;

import com.g4vrk.functionalActions.ExecutableAction;
import com.g4vrk.functionalActions.list.ExecutableActionList;
import com.g4vrk.punishAddon.action.PunishmentActionRunner;
import com.g4vrk.punishAddon.sequence.PendingActionManager;
import com.g4vrk.punishAddon.sequence.SequenceSession;
import com.g4vrk.react.api.ReloadObserver;
import com.g4vrk.react.api.platform.player.PlatformPlayer;
import com.g4vrk.schedula.task.scheduler.TaskScheduler;
import net.kyori.adventure.audience.Audience;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A ticked visual effect with configured actions at each stage.
 */
@ApiStatus.AvailableSince("1.0")
public abstract class VisualSequence extends TimedTickingSequence implements ReloadObserver<ConfigurationNode> {

    private final Function<List<String>, ExecutableActionList<? super Audience>> actionParser;
    private final PunishmentActionRunner actionRunner;
    private final PendingActionManager pending;
    private volatile StageActions actions = StageActions.EMPTY;

    protected VisualSequence(
            final long duration,
            final @NotNull TaskScheduler<PlatformPlayer> scheduler,
            final @NotNull Function<List<String>, ExecutableActionList<? super Audience>> actionParser,
            final @NotNull PunishmentActionRunner actionRunner,
            final @NotNull PendingActionManager pending
    ) {

        super(duration, scheduler);
        this.actionParser = actionParser;
        this.actionRunner = actionRunner;
        this.pending = pending;

    }

    @Override
    public void onReload(
            final @NotNull ConfigurationNode config
    ) {

        try {
            final ExecutableActionList<? super Audience> onStart = this.actionParser.apply(
                    config.node("actions", "on-start").getList(String.class, List.of())
            );
            final ExecutableActionList<? super Audience> thenTick = this.actionParser.apply(
                    config.node("actions", "then-tick").getList(String.class, List.of())
            );
            final ExecutableActionList<? super Audience> atEnd = this.actionParser.apply(
                    config.node("actions", "finally").getList(String.class, List.of())
            );
            this.actions = new StageActions(onStart, thenTick, atEnd);
        } catch (final SerializationException exception) {
            throw new IllegalStateException("Could not read animation stage actions", exception);
        }

    }

    @Override
    public @NotNull SequenceSession newSession(
            final @NotNull PlatformPlayer source,
            final @NotNull BiConsumer<PlatformPlayer, Iterable<? extends ExecutableAction<? super Audience>>> onEnd
    ) {

        return () -> {
            final StageActions snapshot = this.actions;
            final Consumer<PlatformPlayer> completion = target -> onEnd.accept(target, snapshot.atEnd());
            final PendingActionManager.Action action = this.pending.track(source, completion);
            scheduleVisualSequence(
                    source,
                    action,
                    () -> {
                        start(source);
                        this.actionRunner.run(source, snapshot.onStart());
                    },
                    tick -> {
                        tick(source, tick);
                        this.actionRunner.run(source, snapshot.thenTick());
                    },
                    () -> action.complete(source)
            );
        };

    }

    protected abstract void start(
            final @NotNull PlatformPlayer source
    );

    protected abstract void tick(
            final @NotNull PlatformPlayer source,
            final long tick
    );

    private record StageActions(
            @NotNull ExecutableActionList<? super Audience> onStart,
            @NotNull ExecutableActionList<? super Audience> thenTick,
            @NotNull ExecutableActionList<? super Audience> atEnd
    ) {

        private static final StageActions EMPTY = new StageActions(
                new ExecutableActionList<>(List.of()),
                new ExecutableActionList<>(List.of()),
                new ExecutableActionList<>(List.of())
        );

    }

}
