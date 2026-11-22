package com.g4vrk.punishAddon.command;

import com.g4vrk.punishAddon.PunishContext;
import com.g4vrk.punishAddon.config.PunishType;
import com.g4vrk.react.api.extension.command.CommandRegistrar;
import com.g4vrk.react.api.platform.sender.PlatformSender;
import com.g4vrk.react.api.platform.player.PlatformPlayer;
import java.util.Collection;
import io.leangen.geantyref.TypeToken;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.key.CloudKey;
import org.incendo.cloud.parser.standard.StringParser;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.function.BiConsumer;

/**
 * {@code /react <label> <type> [animation] <players> <reason>} on the Cloud command tree.
 *
 * <p>Each punishment type requires {@code react.punish.<type>}.
 * {@code [animation]} is a literal only for types with {@code need-to-select-in-command: true}.</p>
 */
public final class PunishCommand {

    public static final String TYPE_PERMISSION = "react.punish.";
    public static final String ROOT = "react";

    private static final CloudKey<Collection<PlatformPlayer>> PLAYERS = CloudKey.of("players", new TypeToken<Collection<PlatformPlayer>>() {});
    private static final CloudKey<String> REASON = CloudKey.of("reason", String.class);

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
            final Command.Builder<PlatformSender> root = commands.command(ROOT)
                    .literal(this.label)
                    .literal(type)
                    .permission(TYPE_PERMISSION + type);

            if (!settings.animation().needToSelectInCommand()) {
                commands.register(ROOT, withTarget(commands, root, type, settings.animation().selected(), settings));
                continue;
            }

            for (final String animation : settings.animations().keySet()) {
                commands.register(ROOT, withTarget(commands, root.literal(animation), type, animation, settings));
            }
        }

    }

    private Command.@NotNull Builder<PlatformSender> withTarget(
            final @NotNull CommandRegistrar commands,
            final @NotNull Command.Builder<PlatformSender> builder,
            final @NotNull String type,
            final @NotNull String animation,
            final @NotNull PunishType settings
    ) {

        return builder
                .argument(commands.multiplePlayersArgument(PLAYERS, true))
                .required(REASON, StringParser.greedyStringParser())
                .handler(context -> run(context, new PunishContext(type, animation, context.get(REASON), settings)));

    }

    private void run(
            final @NotNull CommandContext<PlatformSender> context,
            final @NotNull PunishContext punishment
    ) {

        final Collection<PlatformPlayer> players = context.get(PLAYERS);

        for (final PlatformPlayer player : players) {
            this.punish.accept((Player) player.handle(), punishment);
        }

    }

}
