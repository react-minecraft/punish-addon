package com.g4vrk.punishAddon.sequence.builtin;

import com.g4vrk.functionalActions.parser.ActionParser;
import com.g4vrk.punishAddon.action.PunishmentActionRunner;
import com.g4vrk.punishAddon.sequence.PendingActionManager;
import com.g4vrk.punishAddon.sequence.Sequence;
import com.g4vrk.punishAddon.sequence.builtin.impl.HolyWorldVisualSequence;
import com.g4vrk.schedula.task.scheduler.Scheduler;
import net.kyori.adventure.audience.Audience;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.ConfigurationNode;

import java.util.Map;

/**
 * Creates the animations that ship with the addon.
 */
public final class BuiltInSequenceBootstrap {

    public static final String HOLYWORLD = "holyworld";

    private BuiltInSequenceBootstrap() {
    }

    public static @NotNull Map<String, Sequence<Player>> bootstrap(
            final @NotNull Scheduler scheduler,
            final @NotNull ActionParser<Audience> actionParser,
            final @NotNull PunishmentActionRunner actionRunner,
            final @NotNull PendingActionManager pendingActionManager,
            final @NotNull ConfigurationNode holyWorldConfig
    ) {
        final BuiltInSequenceRegistry<Player> registry = new BuiltInSequenceRegistry<>();

        registry.register(
                HOLYWORLD,
                new HolyWorldVisualSequence(
                        60,
                        scheduler,
                        actionParser::parseAll,
                        actionRunner,
                        pendingActionManager,
                        holyWorldConfig
                )
        );

        return registry.all();
    }

}
