package com.g4vrk.punishAddon.sequence.builtin;

import com.g4vrk.functionalActions.parser.ActionParser;
import com.g4vrk.punishAddon.action.PunishmentActionRunner;
import com.g4vrk.punishAddon.sequence.PendingActionManager;
import com.g4vrk.punishAddon.sequence.Sequence;
import com.g4vrk.punishAddon.sequence.builtin.impl.HolyWorldSequence;
import com.g4vrk.punishAddon.sequence.builtin.renderer.HolyWorldRenderer;
import com.g4vrk.react.api.platform.player.PlatformPlayer;
import com.g4vrk.schedula.task.scheduler.TaskScheduler;
import net.kyori.adventure.audience.Audience;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.ConfigurationNode;

import java.util.Map;

/**
 * Creates the animations that ship with the addon.
 */
@ApiStatus.Internal
public final class BuiltInSequenceBootstrap {

    public static final String HOLYWORLD = "holyworld";

    private BuiltInSequenceBootstrap() {

    }

    public static @NotNull Map<String, Sequence<PlatformPlayer>> bootstrap(
            final @NotNull TaskScheduler<PlatformPlayer> scheduler,
            final @NotNull ActionParser<Audience> actionParser,
            final @NotNull PunishmentActionRunner actionRunner,
            final @NotNull PendingActionManager pendingActionManager,
            final @NotNull ConfigurationNode holyWorldConfig,
            final @NotNull HolyWorldRenderer renderer
    ) {

        final BuiltInSequenceRegistry<PlatformPlayer> registry = new BuiltInSequenceRegistry<>();
        registry.register(
                HOLYWORLD,
                new HolyWorldSequence(
                        60L,
                        scheduler,
                        actionParser::parseAll,
                        actionRunner,
                        pendingActionManager,
                        holyWorldConfig,
                        renderer
                )
        );
        return registry.all();

    }

}
