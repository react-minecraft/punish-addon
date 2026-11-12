package com.g4vrk.punishAddon.sequence.impl;

import com.g4vrk.functionalActions.list.ExecutableActionList;
import com.g4vrk.punishAddon.action.PunishmentActionRunner;
import com.g4vrk.punishAddon.sequence.PendingActionManager;
import com.g4vrk.punishAddon.sequence.SequenceSession;
import com.g4vrk.punishAddon.sequence.impl.session.DelegateSequenceSession;
import com.g4vrk.react.api.ReloadObserver;
import com.g4vrk.schedula.task.scheduler.Scheduler;
import net.kyori.adventure.audience.Audience;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A ticked visual effect with configured actions at each stage.
 */
public abstract class VisualSequence extends TimedTickingSequence implements ReloadObserver<ConfigurationNode> {

    private final Function<List<String>, ExecutableActionList<? super Audience>> actionParser;
    private final PunishmentActionRunner actionRunner;
    private final PendingActionManager pending;

    private ExecutableActionList<? super Audience> onStart;
    private ExecutableActionList<? super Audience> thenTick;
    private ExecutableActionList<? super Audience> atEnd;

    protected VisualSequence(
            final long duration,
            final @NotNull Scheduler scheduler,
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

            this.onStart = actionParser
                    .apply(
                            config.node(
                                    "actions",
                                    "on-start"
                            ).getList(String.class)
                    );

            this.thenTick = actionParser
                    .apply(
                            config.node(
                                    "actions",
                                    "then-tick"
                            ).getList(String.class)
                    );

            this.atEnd = actionParser
                    .apply(
                            config.node(
                                    "actions",
                                    "finally"
                            ).getList(String.class)
                    );

        } catch (final SerializationException ex) {

            throw new RuntimeException(ex);

        }

    }

    @Override
    public @NotNull SequenceSession newSession(
            final @NotNull Player source,
            final @NotNull Consumer<Player> onEnd
    ) {

        return new DelegateSequenceSession(() -> {
            final PendingActionManager.Action action = pending.track(source.getUniqueId(), onEnd);
            scheduleVisualSequence(
                    source,
                    action,
                    () -> {
                        start(source);
                        actionRunner.run(source, onStart);
                    },
                    tick -> {
                        tick(source, tick);
                        actionRunner.run(source, thenTick);
                    },
                    () -> {
                        if (action.complete(source)) {
                            actionRunner.run(source, atEnd);
                        }
                    }
            );
        });

    }

    protected abstract void start(
            final @NotNull Player source
    );

    protected abstract void tick(
            final @NotNull Player source,
            final long tick
    );

}
