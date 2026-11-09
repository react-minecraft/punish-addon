package com.g4vrk.punishAddon.command;

import com.g4vrk.punishAddon.PunishContext;
import com.g4vrk.punishAddon.config.PunishType;
import com.g4vrk.react.api.extension.command.CommandRegistrar;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.bukkit.data.MultiplePlayerSelector;
import org.incendo.cloud.bukkit.parser.selector.MultiplePlayerSelectorParser;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.StringParser;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.function.BiConsumer;

/**
 * {@code /<label> <type> [animation] <players> <reason>} on the Cloud command tree.
 *
 * <p>The root needs {@code react.command.<label>}, every type its own {@code react.punish.<type>}.
 * {@code [animation]} is a literal only for types with {@code need-to-select-in-command: true}.</p>
 */
public final class PunishCommand {

    public static final String TYPE_PERMISSION = "react.punish.";

    private static final String PLAYERS = "players";
    private static final String REASON = "reason";

    private final String label;
    private final Map<String, PunishType> types;
    private final BiConsumer<Player, PunishContext> punish;

    public PunishCommand(
            final @NotNull String label,
            final @NotNull Map<String, PunishType> types,
            final @NotNull BiConsumer<Player, PunishContext> punish
    ) {
        this.label = label;
        this.types = types;
        this.punish = punish;
    }

    public void register(final @NotNull CommandRegistrar commands) {
        for (final Map.Entry<String, PunishType> entry : this.types.entrySet()) {
            final String type = entry.getKey();
            final PunishType settings = entry.getValue();
            final Command.Builder<CommandSender> root = commands.command(this.label)
                    .literal(type)
                    .permission(TYPE_PERMISSION + type);

            if (!settings.animation().needToSelectInCommand()) {
                commands.register(this.label, withTarget(root, type, settings.animation().selected(), settings));
                continue;
            }

            for (final String animation : settings.animations().keySet()) {
                commands.register(this.label, withTarget(root.literal(animation), type, animation, settings));
            }
        }
    }

    private Command.Builder<CommandSender> withTarget(
            final @NotNull Command.Builder<CommandSender> builder,
            final @NotNull String type,
            final @NotNull String animation,
            final @NotNull PunishType settings
    ) {
        return builder
                .required(PLAYERS, MultiplePlayerSelectorParser.multiplePlayerSelectorParser())
                .required(REASON, StringParser.greedyStringParser())
                .handler(context -> run(context, new PunishContext(type, animation, context.get(REASON), settings)));
    }

    private void run(final @NotNull CommandContext<CommandSender> context, final @NotNull PunishContext punishment) {
        final MultiplePlayerSelector players = context.get(PLAYERS);

        for (final Player player : players.values()) {
            this.punish.accept(player, punishment);
        }
    }

}
