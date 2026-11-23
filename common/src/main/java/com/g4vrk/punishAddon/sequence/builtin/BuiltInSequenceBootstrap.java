package com.g4vrk.punishAddon.sequence.builtin;

import com.g4vrk.functionalActions.parser.ActionParser;
import com.g4vrk.punishAddon.action.PunishmentActionRunner;
import com.g4vrk.punishAddon.sequence.PendingActionManager;
import com.g4vrk.punishAddon.sequence.Sequence;
import com.g4vrk.react.api.platform.player.PlatformPlayer;
import com.g4vrk.schedula.task.scheduler.TaskScheduler;
import com.g4vrk.functionalActions.list.ExecutableActionList;
import net.kyori.adventure.audience.Audience;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.ConfigurationNode;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Creates the animations that ship with the addon.
 */
public final class BuiltInSequenceBootstrap {

    public static final String HOLYWORLD = "holyworld";

    private BuiltInSequenceBootstrap() {
    }

    public static @NotNull Map<String, Sequence<PlatformPlayer>> bootstrap(
            final @NotNull TaskScheduler<PlatformPlayer> scheduler,
            final @NotNull ActionParser<Audience> actionParser,
            final @NotNull PunishmentActionRunner actionRunner,
            final @NotNull PendingActionManager pendingActionManager,
            final @NotNull ConfigurationNode holyWorldConfig
    ) {

        final BuiltInSequenceRegistry<PlatformPlayer> registry = new BuiltInSequenceRegistry<>();

        registry.register(
                HOLYWORLD,
                createHolyWorld(
                        scheduler,
                        actionParser,
                        actionRunner,
                        pendingActionManager,
                        holyWorldConfig
                )
        );

        return registry.all();

    }

    @SuppressWarnings("unchecked")
    private static @NotNull Sequence<PlatformPlayer> createHolyWorld(
            final @NotNull TaskScheduler<PlatformPlayer> scheduler,
            final @NotNull ActionParser<Audience> actionParser,
            final @NotNull PunishmentActionRunner actionRunner,
            final @NotNull PendingActionManager pendingActionManager,
            final @NotNull ConfigurationNode holyWorldConfig
    ) {

        try {
            final Class<?> type = Class.forName("com.g4vrk.punishAddon.sequence.builtin.impl.HolyWorldVisualSequence");
            final Constructor<?> constructor = type.getConstructor(
                    long.class,
                    TaskScheduler.class,
                    Function.class,
                    PunishmentActionRunner.class,
                    PendingActionManager.class,
                    ConfigurationNode.class
            );
            return (Sequence<PlatformPlayer>) constructor.newInstance(
                    60L,
                    scheduler,
                    (Function<List<String>, ExecutableActionList<? super Audience>>) actionParser::parseAll,
                    actionRunner,
                    pendingActionManager,
                    holyWorldConfig
            );
        } catch (final ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException exception) {
            throw new IllegalStateException("Could not create the Paper HolyWorld animation", exception);
        } catch (final InvocationTargetException exception) {
            throw new IllegalStateException("Could not create the Paper HolyWorld animation", exception.getCause());
        }

    }

}
