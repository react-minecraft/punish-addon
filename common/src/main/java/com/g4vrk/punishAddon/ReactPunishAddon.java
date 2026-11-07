package com.g4vrk.punishAddon;

import com.g4vrk.functionalActions.ExecutableAction;
import com.g4vrk.functionalActions.parser.ActionParser;
import com.g4vrk.punishAddon.command.argument.PunishArgument;
import com.g4vrk.punishAddon.config.AnimationSettings;
import com.g4vrk.punishAddon.config.PunishType;
import com.g4vrk.punishAddon.listener.JoinListener;
import com.g4vrk.punishAddon.sequence.PendingActionManager;
import com.g4vrk.punishAddon.sequence.Sequence;
import com.g4vrk.punishAddon.sequence.builtin.BuiltInSequenceBootstrap;
import com.g4vrk.react.React;
import com.g4vrk.react.api.ReloadObserver;
import com.g4vrk.react.api.addon.JavaAddon;
import com.g4vrk.schedula.task.TickSchedule;
import net.kyori.adventure.audience.Audience;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.incendo.cloud.bukkit.data.MultiplePlayerSelector;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;
import org.spongepowered.configurate.serialize.SerializationException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

public final class ReactPunishAddon extends JavaAddon {

    public static ReactPunishAddon INSTANCE;

    private volatile PendingActionManager pendingActionManager;

    private volatile CommentedConfigurationNode mainConfig;
    private volatile CommentedConfigurationNode holyWorldConfig;

    private volatile Map<String, Sequence<Player>> animations;

    private volatile Map<String, PunishType> types;

    private volatile Listener listener;

    private final AtomicBoolean reloading = new AtomicBoolean();

    @Override
    public void onLoad() {
        INSTANCE = this;
    }

    @Override
    public void onEnable() {

        this.saveDefaultResource("main-config.yml");
        this.saveDefaultResource("animations/holyworld.yml");

        final React react = React.INSTANCE;

        this.pendingActionManager = new PendingActionManager();

        this.listener = new JoinListener(pendingActionManager::thenJoin);

        final Plugin pluginInstance = react.plugin();

        pluginInstance.getServer().getPluginManager().registerEvents(this.listener, pluginInstance);

        this.loadConfigurableModules();

        final PunishArgument punishArgument =
                new PunishArgument(
                        react.commands().builderFactory(),
                        this.mainConfig
                                .node("settings", "argument-literal")
                                .getString("menu"),
                        this.types,
                        this::handlePunish
                );

        punishArgument.register(
                react.commands().manager()
        );

        slf4jLogger().info(
                "ReactPunishAddon successfully enabled!"
        );
    }

    private void handlePunish(
            final @NotNull CommandContext<CommandSender> context,
            final @NotNull PunishContext punishContext
    ) {
        final MultiplePlayerSelector selector =
                punishContext.players();

        final Sequence<Player> sequence =
                punishContext.punishType()
                        .sequence(punishContext.animation());

        if (sequence == null) {
            throw new IllegalStateException(
                    "Sequence '" + punishContext.animation() +
                            "' is not registered"
            );
        }

        for (final Player player : selector.values()) {

            final PunishContext playerContext =
                    new PunishContext(
                            punishContext.type(),
                            punishContext.animation(),
                            punishContext.inputAfterType(),
                            punishContext.punishType(),
                            selector
                    );

            sequence.newSession(
                    player,
                    ignored -> this.executeActions(
                            player,
                            playerContext
                    )
            ).start();
        }
    }

    private void executeActions(
            final @NotNull Player player,
            final @NotNull PunishContext context
    ) {
        final Function<String, String> placeholders =
                input -> {

                    if (input == null) {
                        return null;
                    }

                    return input
                            .replace(
                                    "{player.name}",
                                    player.getName()
                            )
                            .replace(
                                    "{player.uuid}",
                                    player.getUniqueId().toString()
                            )
                            .replace(
                                    "{context.input-after-type}",
                                    context.inputAfterType()
                            );
                };

        for (final ExecutableAction<? super Audience> action
                : context.punishType().actions()) {

            try {
                action.execute(
                        player,
                        placeholders
                );
            } catch (final Throwable throwable) {
                slf4jLogger().error(
                        "Failed to execute punishment action for {}",
                        player.getName(),
                        throwable
                );
            }
        }
    }

    @Override
    public void onDisable() {
        if (this.listener != null) {
            HandlerList.unregisterAll(this.listener);
            this.listener = null;
        }
        slf4jLogger().info(
                "ReactPunishAddon successfully disabled!"
        );
    }

    public @NotNull CompletableFuture<Void> reloadAsync() {

        if (!this.reloading.compareAndSet(false, true)) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException(
                            "Punish addon is already reloading"
                    )
            );
        }

        return CompletableFuture
                .runAsync(this::loadConfigurableModules)
                .thenCompose(ignored -> {

                    final CompletableFuture<Void> future =
                            new CompletableFuture<>();

                    React.INSTANCE.scheduler().schedule(
                            () -> {
                                try {
                                    future.complete(null);
                                } catch (final Throwable throwable) {
                                    future.completeExceptionally(
                                            throwable
                                    );
                                }
                            },
                            TickSchedule.instant()
                    );

                    return future;
                })
                .whenComplete(
                        (ignored, throwable) -> {
                            this.reloading.set(false);

                            if (throwable != null) {
                                slf4jLogger().error(
                                        "Failed to reload ReactPunishAddon",
                                        throwable
                                );
                            }
                        }
                );
    }

    public @NotNull ConfigurationNode mainConfig() {
        return this.mainConfig;
    }

    public @NotNull ConfigurationNode holyWorldConfig() {
        return this.holyWorldConfig;
    }

    public @NotNull Map<String, Sequence<Player>> animations() {
        return this.animations;
    }

    public @NotNull Map<String, PunishType> types() {
        return this.types;
    }

    private void loadConfigurableModules() {

        final Path mainConfigPath =
                directory()
                        .toPath()
                        .resolve("main-config.yml");

        final Path holyWorldConfigPath =
                directory()
                        .toPath()
                        .resolve("animations/holyworld.yml");

        final CommentedConfigurationNode config;
        final CommentedConfigurationNode holyWorldConfig;

        try {
            config = load(mainConfigPath);
            holyWorldConfig = load(holyWorldConfigPath);
        } catch (final IOException exception) {
            throw new RuntimeException(
                    "Failed to load configs",
                    exception
            );
        }

        this.mainConfig = config;
        this.holyWorldConfig = holyWorldConfig;

        final Map<String, Sequence<Player>> animations;
        if (this.animations == null) {
            animations = BuiltInSequenceBootstrap.bootstrap(this.pendingActionManager);
        } else {
            animations = this.animations;
            this.reloadAnimations(animations, holyWorldConfig);
        }

        final ActionParser<Audience> actionParser =
                React.INSTANCE.actionParser();

        final Map<String, PunishType> types;

        try {
            types = this.loadTypes(
                    config.node("types"),
                    animations,
                    actionParser
            );
        } catch (final SerializationException ex) {
            throw new RuntimeException(
                    "Failed to load punishment types",
                    ex
            );
        }

        this.animations = animations;
        this.types = types;
    }

    private @NotNull Map<String, PunishType> loadTypes(
            final @NotNull ConfigurationNode typesNode,
            final @NotNull Map<String, Sequence<Player>> animations,
            final @NotNull ActionParser<Audience> actionParser
    ) throws SerializationException {
        final Map<String, PunishType> types =
                new HashMap<>();

        for (final Map.Entry<Object, ? extends ConfigurationNode> entry
                : typesNode.childrenMap().entrySet()) {

            final String typeId =
                    String.valueOf(entry.getKey());

            final ConfigurationNode typeNode =
                    entry.getValue();

            final ConfigurationNode animationNode =
                    typeNode.node("animation");

            final AnimationSettings animation =
                    new AnimationSettings(
                            animationNode
                                    .node("need-to-select-in-command")
                                    .getBoolean(false),

                            animationNode
                                    .node("selected")
                                    .getString("holyworld")
                    );

            final List<String> actionStrings =
                    typeNode
                            .node("actions")
                            .getList(
                                    String.class,
                                    List.of()
                            );

            final List<ExecutableAction<? super Audience>> actions =
                    new ArrayList<>(
                            actionStrings.size()
                    );

            for (final String actionString : actionStrings) {

                final ExecutableAction<? super Audience> action =
                        actionParser.parse(actionString);

                if (action == null) {
                    slf4jLogger().warn(
                            "Unknown action '{}' in punishment type '{}'",
                            actionString,
                            typeId
                    );
                    continue;
                }

                actions.add(action);
            }

            types.put(
                    typeId,
                    new PunishType(
                            animations,
                            animation,
                            List.copyOf(actions)
                    )
            );
        }

        return Map.copyOf(types);
    }

    private static @NotNull CommentedConfigurationNode load(
            final @NotNull Path path
    ) throws IOException {
        return YamlConfigurationLoader.builder()
                .path(path)
                .build()
                .load();
    }

    @SuppressWarnings("unchecked")
    private void reloadAnimations(
            final @NotNull Map<String, Sequence<Player>> animations,
            final @NotNull ConfigurationNode holyWorldConfig
    ) {
        final Sequence<Player> holyWorld = animations.get("holyworld");
        if (holyWorld instanceof ReloadObserver<?> observer) {
            ((ReloadObserver<ConfigurationNode>) observer).onReload(holyWorldConfig);
        }
    }

    private void saveDefaultResource(
            final @NotNull String resourcePath
    ) {
        final Path path =
                directory()
                        .toPath()
                        .resolve(resourcePath);

        if (!path.toFile().exists()) {
            super.saveResource(
                    resourcePath,
                    false
            );
        }
    }
}