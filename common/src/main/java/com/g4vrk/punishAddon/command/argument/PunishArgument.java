package com.g4vrk.punishAddon.command.argument;

import com.g4vrk.punishAddon.PunishContext;
import com.g4vrk.punishAddon.config.PunishType;
import com.g4vrk.punishAddon.sequence.Sequence;
import com.g4vrk.react.api.Factory;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.bukkit.data.MultiplePlayerSelector;
import org.incendo.cloud.bukkit.parser.selector.MultiplePlayerSelectorParser;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.StringParser;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.function.BiConsumer;

public final class PunishArgument {

    private final Factory<Command.Builder<CommandSender>> factory;

    private final String argumentLiteral;

    private final Map<String, PunishType> types;

    private final BiConsumer<CommandContext<CommandSender>, PunishContext> handler;

    public PunishArgument(
            final @NotNull Factory<Command.Builder<CommandSender>> factory,
            final @NotNull String argumentLiteral,
            final @NotNull Map<String, PunishType> types,
            final @NotNull BiConsumer<CommandContext<CommandSender>, PunishContext> handler
    ) {
        this.factory = factory;
        this.argumentLiteral = argumentLiteral;
        this.types = types;
        this.handler = handler;
    }

    public void register(
            final @NotNull CommandManager<CommandSender> manager
    ) {
        for (final Map.Entry<String, PunishType> entry : this.types.entrySet()) {

            final String typeId = entry.getKey();
            final PunishType punishType = entry.getValue();

            Command.Builder<CommandSender> typeBuilder =
                    this.factory.create()
                            .literal(this.argumentLiteral)
                            .permission("react.punish")
                            .literal(typeId)
                            .permission("react.punish." + typeId)
                            .required(
                                    "players",
                                    MultiplePlayerSelectorParser.multiplePlayerSelectorParser()
                            );

            if (!punishType.animation().needToSelectInCommand()) {

                typeBuilder = typeBuilder
                        .required(
                                "input-after-type",
                                StringParser.greedyStringParser()
                        )
                        .handler(context -> {

                            final MultiplePlayerSelector players =
                                    context.get("players");

                            final String inputAfterType =
                                    context.get("input-after-type");

                            final String animationId =
                                    punishType.animation().selected();

                            final Sequence<Player> sequence =
                                    punishType.sequence(animationId);

                            if (sequence == null) {
                                throw new IllegalArgumentException(
                                        "Unknown animation '" + animationId +
                                                "' for punishment type '" + typeId + '\''
                                );
                            }

                            final PunishContext punishContext =
                                    new PunishContext(
                                            typeId,
                                            animationId,
                                            inputAfterType,
                                            punishType,
                                            players
                                    );

                            this.handler.accept(
                                    context,
                                    punishContext
                            );
                        });

                manager.command(typeBuilder);

                continue;
            }

            for (final String animationId : punishType.animations().keySet()) {

                Command.Builder<CommandSender> animationBuilder =
                        typeBuilder.literal(animationId);

                animationBuilder = animationBuilder
                        .required(
                                "input-after-type",
                                StringParser.greedyStringParser()
                        )
                        .handler(context -> {

                            final MultiplePlayerSelector players =
                                    context.get("players");

                            final String inputAfterType =
                                    context.get("input-after-type");

                            final PunishContext punishContext =
                                    new PunishContext(
                                            typeId,
                                            animationId,
                                            inputAfterType,
                                            punishType,
                                            players
                                    );

                            this.handler.accept(
                                    context,
                                    punishContext
                            );
                        });

                manager.command(animationBuilder);
            }
        }
    }
}