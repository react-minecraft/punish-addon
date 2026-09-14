package com.g4vrk.punishAddon.sequence.builtin;

import com.g4vrk.functionalActions.parser.ActionParser;
import com.g4vrk.punishAddon.sequence.PendingActionManager;
import com.g4vrk.punishAddon.sequence.Sequence;
import com.g4vrk.punishAddon.sequence.builtin.impl.HolyWorldVisualSequence;
import com.g4vrk.react.React;
import net.kyori.adventure.audience.Audience;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BuiltInSequenceBootstrap {

    private static final AtomicBoolean bootstrapped = new AtomicBoolean(false);

    private BuiltInSequenceBootstrap() {
    }

    public static @NotNull Map<String, Sequence<Player>> bootstrap(
            final @NotNull PendingActionManager pendingActionManager
    ) {

        if (!bootstrapped.compareAndSet(false, true)) {
            return Map.of();
        }

        final BuiltInSequenceRegistry<Player> builtInSequenceRegistry = new BuiltInSequenceRegistry<>();

        // registering

        final ActionParser<Audience> actionParser = React.INSTANCE.getActionParser();

        builtInSequenceRegistry.register("holyworld", new HolyWorldVisualSequence(60, actionParser::parseAll, pendingActionManager));

        return builtInSequenceRegistry.all();

    }


}
