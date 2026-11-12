package com.g4vrk.punishAddon;

import com.g4vrk.functionalActions.ExecutableAction;
import com.g4vrk.functionalActions.parser.ActionParser;
import com.g4vrk.punishAddon.command.PunishCommand;
import com.g4vrk.punishAddon.config.AnimationSettings;
import com.g4vrk.punishAddon.config.PunishType;
import com.g4vrk.punishAddon.sequence.PendingActionManager;
import com.g4vrk.punishAddon.sequence.Sequence;
import com.g4vrk.punishAddon.sequence.builtin.BuiltInSequenceBootstrap;
import com.g4vrk.react.api.ReloadObserver;
import com.g4vrk.react.api.addon.JavaAddon;
import com.g4vrk.react.api.event.Subscribe;
import com.g4vrk.react.api.event.plugin.ReloadEvent;
import com.g4vrk.schedula.task.TickSchedule;
import net.kyori.adventure.audience.Audience;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Punishes players with a command and plays an animation over them first.
 *
 * <p>The command, the join listener and the reload subscription all go through the addon
 * environment, so the plugin removes them when the addon is disabled.</p>
 */
public final class ReactPunishAddon extends JavaAddon implements Listener {

    private static final String MAIN_CONFIG = "main-config.yml";
    private static final String HOLYWORLD_CONFIG = "animations/holyworld.yml";
    private static final String DEFAULT_LABEL = "punish";

    private final PendingActionManager pending = new PendingActionManager();

    private volatile Map<String, Sequence<Player>> animations;
    private volatile Map<String, PunishType> types = Map.of();
    private volatile String label = DEFAULT_LABEL;

    @Override
    public void onEnable() {

        saveDefaultResource(MAIN_CONFIG);
        saveDefaultResource(HOLYWORLD_CONFIG);

        load();

        environment().listeners().register(this);
        registerCommand();

        logger().info("Punish types: {}, command /{}", this.types.keySet(), this.label);

    }

    @Override
    public void onDisable() {

        this.pending.clear();

    }

    /**
     * Re-reads the configs after {@code /react reload} and rebuilds the command, since its
     * literals are the type and animation ids.
     */
    @Subscribe
    public void onReload(final @NotNull ReloadEvent event) {

        final String previous = this.label;

        try {
            load();
        } catch (final RuntimeException exception) {
            logger().error("Could not reload the punish addon, keeping the old settings", exception);
            return;
        }

        environment().scheduler().schedule(() -> {
            environment().commands().unregister(previous);
            registerCommand();
        }, TickSchedule.instant());

    }

    @EventHandler
    public void onJoin(final @NotNull PlayerJoinEvent event) {

        this.pending.thenJoin(event.getPlayer());

    }

    private void registerCommand() {

        new PunishCommand(this.label, this.types, this::punish).register(environment().commands());

    }

    private void punish(final @NotNull Player player, final @NotNull PunishContext context) {

        final Sequence<Player> sequence = context.punishType().sequence(context.animation());

        if (sequence == null) {
            logger().warn("Animation '{}' is unknown, punishing {} without it", context.animation(), player.getName());
            execute(player, context);
            return;
        }

        sequence.newSession(player, target -> execute(target, context)).start();

    }

    private void execute(final @NotNull Player player, final @NotNull PunishContext context) {

        final String name = player.getName();
        final String uuid = player.getUniqueId().toString();
        final String reason = context.reason();

        final UnaryOperator<String> placeholders = input -> input == null ? null : input
                .replace("{player.name}", name)
                .replace("{player.uuid}", uuid)
                .replace("{context.input-after-type}", reason)
                .replace("{reason}", reason);

        for (final ExecutableAction<? super Audience> action : context.punishType().actions()) {
            try {
                action.execute(player, placeholders);
            } catch (final RuntimeException exception) {
                logger().error("Punishment action of type '{}' failed for {}", context.type(), name, exception);
            }
        }

    }

    private void load() {

        final CommentedConfigurationNode main = read(MAIN_CONFIG);
        final CommentedConfigurationNode holyWorld = read(HOLYWORLD_CONFIG);
        final ActionParser<Audience> parser = environment().actions();

        Map<String, Sequence<Player>> animations = this.animations;
        if (animations == null) {
            animations = BuiltInSequenceBootstrap.bootstrap(environment().scheduler(), parser, this.pending, holyWorld);
        } else {
            reloadHolyWorld(animations, holyWorld);
        }

        final ConfigurationNode settings = main.node("settings");
        // "argument-literal" is the key of the old /react subcommand; it still names the command.
        final String label = settings.node("command").getString(
                settings.node("argument-literal").getString(DEFAULT_LABEL));

        final Map<String, PunishType> types;
        try {
            types = loadTypes(main.node("types"), animations, parser);
        } catch (final SerializationException exception) {
            throw new IllegalStateException("Could not read the punishment types", exception);
        }

        this.animations = animations;
        this.types = types;
        this.label = label;

    }

    private @NotNull Map<String, PunishType> loadTypes(
            final @NotNull ConfigurationNode node,
            final @NotNull Map<String, Sequence<Player>> animations,
            final @NotNull ActionParser<Audience> parser
    ) throws SerializationException {

        final Map<String, PunishType> types = new LinkedHashMap<>();

        for (final Map.Entry<Object, ? extends ConfigurationNode> entry : node.childrenMap().entrySet()) {
            final String id = String.valueOf(entry.getKey());
            final ConfigurationNode type = entry.getValue();
            final ConfigurationNode animation = type.node("animation");

            final AnimationSettings settings = new AnimationSettings(
                    animation.node("need-to-select-in-command").getBoolean(false),
                    animation.node("selected").getString(BuiltInSequenceBootstrap.HOLYWORLD)
            );

            if (!settings.needToSelectInCommand() && !animations.containsKey(settings.selected())) {
                logger().warn("Type '{}' selects the unknown animation '{}'", id, settings.selected());
            }

            final List<String> lines = type.node("actions").getList(String.class, List.of());
            final List<ExecutableAction<? super Audience>> actions = new ArrayList<>(lines.size());

            for (final String line : lines) {
                final ExecutableAction<? super Audience> action = parser.parse(line);

                if (action == null) {
                    logger().warn("Unknown action '{}' in punishment type '{}'", line, id);
                    continue;
                }

                actions.add(action);
            }

            types.put(id, new PunishType(animations, settings, List.copyOf(actions)));
        }

        return java.util.Collections.unmodifiableMap(types);

    }

    @SuppressWarnings("unchecked")
    private static void reloadHolyWorld(
            final @NotNull Map<String, Sequence<Player>> animations,
            final @NotNull ConfigurationNode config
    ) {

        if (animations.get(BuiltInSequenceBootstrap.HOLYWORLD) instanceof ReloadObserver<?> observer) {
            ((ReloadObserver<ConfigurationNode>) observer).onReload(config);
        }

    }

    private @NotNull CommentedConfigurationNode read(final @NotNull String name) {

        try {
            return YamlConfigurationLoader.builder().path(directory().resolve(name)).build().load();
        } catch (final IOException exception) {
            throw new IllegalStateException("Could not load " + name, exception);
        }

    }

    private void saveDefaultResource(final @NotNull String name) {

        final Path path = directory().resolve(name);

        if (!path.toFile().exists()) {
            saveResource(name, false);
        }

    }

}
