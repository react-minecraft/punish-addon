package com.g4vrk.punishAddon.sequence.impl;

import com.g4vrk.functionalActions.list.ExecutableActionList;
import com.g4vrk.punishAddon.sequence.SequenceSession;
import com.g4vrk.punishAddon.sequence.impl.session.DelegateSequenceSession;
import com.g4vrk.react.api.ReloadObserver;
import com.g4vrk.schedula.task.Task;
import net.kyori.adventure.audience.Audience;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class VisualSequence extends TimedTickingSequence implements ReloadObserver<ConfigurationNode> {

    private final Function<List<String>, ExecutableActionList<? super Audience>> actionParser;

    private Consumer<Player> onStart;
    private Consumer<Player> thenTick;
    private Consumer<Player> atEnd;

    protected VisualSequence(
            final long duration,
            final @NotNull Function<List<String>, ExecutableActionList<? super Audience>> actionParser
    ) {

        super(duration);

        this.actionParser = actionParser;

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
                    )::run;

            this.thenTick = actionParser
                    .apply(
                            config.node(
                                    "actions",
                                    "then-tick"
                            ).getList(String.class)
                    )::run;

            this.atEnd = actionParser
                    .apply(
                            config.node(
                                    "actions",
                                    "finally"
                            ).getList(String.class)
                    )::run;

        } catch (final SerializationException ex) {

            throw new RuntimeException(ex);

        }

    }

    @Override
    public @NotNull SequenceSession newSession(
            final @NotNull Player source,
            final @NotNull Consumer<Player> onEnd
    ) {

        return new DelegateSequenceSession(
                () -> {

                    start(source);

                    onStart.accept(source);

                    scheduleVisualSequence(
                            (task, tick) -> {

                                this.tick(
                                        source,
                                        task,
                                        tick,
                                        onEnd
                                );

                                thenTick.accept(source);

                            },
                            (task, tick) -> {

                                end(
                                        source,
                                        task,
                                        tick
                                );

                                onEnd.accept(source);

                                atEnd.accept(source);

                            }
                    );

                }
        );

    }

    protected abstract void start(
            final @NotNull Player source
    );

    protected abstract void tick(
            final @NotNull Player source,
            final @NotNull Task currentTask,
            final long tick,
            final @NotNull Consumer<Player> onEnd
    );

    protected abstract void end(
            final @NotNull Player source,
            final @NotNull Task currentTask,
            final long tick
    );

}
