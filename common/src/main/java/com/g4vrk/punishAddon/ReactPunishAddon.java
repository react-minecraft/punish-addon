package com.g4vrk.punishAddon;

import com.g4vrk.functionalActions.ExecutableAction;
import com.g4vrk.functionalActions.parser.ActionParser;
import com.g4vrk.punishAddon.action.PunishmentActionRunner;
import com.g4vrk.punishAddon.command.PunishCommand;
import com.g4vrk.punishAddon.config.AnimationConfig;
import com.g4vrk.punishAddon.config.PunishType;
import com.g4vrk.punishAddon.sequence.PendingActionManager;
import com.g4vrk.punishAddon.sequence.Sequence;
import com.g4vrk.punishAddon.sequence.builtin.BuiltInSequenceBootstrap;
import com.g4vrk.react.api.addon.JavaAddon;
import com.g4vrk.react.api.config.ConfigMigrationChain;
import com.g4vrk.react.api.event.Subscribe;
import com.g4vrk.react.api.event.plugin.ReloadEvent;
import com.g4vrk.schedula.task.TickSchedule;
import net.kyori.adventure.audience.Audience;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
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
    private static final ConfigMigrationChain MAIN_MIGRATIONS = ConfigMigrationChain.builder().step(root -> {
        final ConfigurationNode settings = root.node("settings");
        final ConfigurationNode command = settings.node("command");
        if (command.virtual() && !settings.node("argument-literal").virtual()) {
            command.from(settings.node("argument-literal"));
        }
    }).build();

    private final PendingActionManager pending = new PendingActionManager();

    private PunishmentActionRunner actionRunner;
    private volatile Map<String, PunishType> types = Map.of();
    private volatile String label = DEFAULT_LABEL;
    private volatile boolean disabled;

    @Override
    public void onEnable() {

        this.actionRunner = new PunishmentActionRunner(environment().scheduler().<Player>mapEntity(environment().platform()::adaptPlayer), logger(), this.pending);
        load();

        environment().listeners().register(this);
        registerCommand();

        logger().info("Punish types: {}, command /react {}", this.types.keySet(), this.label);

    }

    @Override
    public void onDisable() {

        this.disabled = true;
        this.pending.clear();

    }

    /**
     * Re-reads the configs after {@code /react reload} and rebuilds the command, since its
     * literals are the type and animation ids.
     */
    @Subscribe
    public void onReload(final @NotNull ReloadEvent event) {

        if (this.disabled) {
            return;
        }
        try {
            load();
        } catch (final RuntimeException exception) {
            logger().error("Could not reload the punish addon, keeping the old settings", exception);
            return;
        }

        environment().scheduler().<Player>mapEntity(environment().platform()::adaptPlayer).schedule(() -> {
            if (this.disabled) {
                return;
            }
            environment().commands().unregister(PunishCommand.ROOT);
            registerCommand();
        }, TickSchedule.instant());

    }

    @EventHandler
    public void onJoin(final @NotNull PlayerJoinEvent event) {

        this.pending.thenJoin(event.getPlayer());

    }

    @EventHandler
    public void onQuit(final @NotNull PlayerQuitEvent event) {

        this.pending.onQuit(event.getPlayer().getUniqueId());

    }

    private void registerCommand() {

        new PunishCommand(this.label, this.types, this::punish).register(environment().commands());

    }

    private void punish(final @NotNull Player player, final @NotNull PunishContext context) {

        final Sequence<Player> sequence = context.punishType().sequence(context.animation());

        if (sequence == null) {
            logger().warn("Animation '{}' is unknown, punishing {} without it", context.animation(), player.getName());
            final PendingActionManager.Action action = pending.track(player.getUniqueId(), target -> execute(target, context, List.of()));
            try {
                action.attach(environment().scheduler().<Player>mapEntity(environment().platform()::adaptPlayer).scheduleEntity(player, () -> {
                    if (player.isOnline()) {
                        action.complete(player);
                    } else {
                        action.defer();
                    }
                }, TickSchedule.instant()));
            } catch (final RuntimeException | Error failure) {
                action.discard();
                throw failure;
            }
            return;
        }

        sequence.newSession(player, (target, before) -> execute(target, context, before)).start();

    }

    private void execute(
            final @NotNull Player player,
            final @NotNull PunishContext context,
            final @NotNull Iterable<? extends ExecutableAction<? super Audience>> before
    ) {

        final String name = player.getName();
        final String uuid = player.getUniqueId().toString();
        final String reason = context.reason();

        final UnaryOperator<String> placeholders = input -> input == null ? null : input
                .replace("{player.name}", name)
                .replace("{player.uuid}", uuid)
                .replace("{context.input-after-type}", reason)
                .replace("{reason}", reason);

        actionRunner.runTracked(player, before, context.punishType().actions(), placeholders);

    }

    private void load() {

        final CommentedConfigurationNode main = read(MAIN_CONFIG, MAIN_MIGRATIONS);
        final CommentedConfigurationNode holyWorld = read(HOLYWORLD_CONFIG, ConfigMigrationChain.none());
        final ActionParser<Audience> parser = environment().actions();

        final Map<String, Sequence<Player>> animations = BuiltInSequenceBootstrap.bootstrap(environment().scheduler().<Player>mapEntity(environment().platform()::adaptPlayer), parser, actionRunner, pending, holyWorld);

        final ConfigurationNode settings = main.node("settings");
        /*
         * Older configurations still use the argument-literal key for the command name.
         */
        final String label = settings.node("command").getString(settings.node("argument-literal").getString(DEFAULT_LABEL)).trim();
        if (!label.matches("[a-z0-9_-]+")) {
            throw new IllegalArgumentException("settings.command must be a lowercase command literal");
        }

        final Map<String, PunishType> types;
        try {
            types = loadTypes(main.node("types"), animations, parser);
        } catch (final SerializationException exception) {
            throw new IllegalStateException("Could not read the punishment types", exception);
        }

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

            final AnimationConfig settings = new AnimationConfig(
                    animation.node("need-to-select-in-command").getBoolean(false),
                    animation.node("selected").getString(BuiltInSequenceBootstrap.HOLYWORLD)
            );

            if (!settings.needToSelectInCommand() && !animations.containsKey(settings.selected())) {
                logger().warn("Type '{}' selects the unknown animation '{}'", id, settings.selected());
            }

            final List<String> lines = type.node("actions").getList(String.class, List.of());
            if (lines.isEmpty()) {
                throw new IllegalStateException(
                        "Punishment type '" + id + "' has no configured actions"
                );
            }

            final List<ExecutableAction<? super Audience>> actions = new ArrayList<>(lines.size());

            for (final String line : lines) {
                final ExecutableAction<? super Audience> action = parser.parse(line);

                if (action == null) {
                    throw new IllegalStateException(
                            "Unknown action '" + line + "' in punishment type '" + id + "'"
                    );
                }

                actions.add(action);
            }

            types.put(id, new PunishType(animations, settings, List.copyOf(actions)));
        }

        return Collections.unmodifiableMap(types);

    }

    private @NotNull CommentedConfigurationNode read(
            final @NotNull String name,
            final @NotNull ConfigMigrationChain migrations
    ) {

        final String language = "en".equalsIgnoreCase(System.getProperty("user.language")) ? "en" : "ru";
        try {
            return environment().configs().file(name, ConfigurationNode.class)
                    .defaults(getClass().getClassLoader(), language + "/" + name)
                    .migrations(migrations)
                    .keepMissing()
                    .build()
                    .loadNode();
        } catch (final IOException exception) {
            throw new IllegalStateException("Could not load " + name, exception);
        }

    }

}
