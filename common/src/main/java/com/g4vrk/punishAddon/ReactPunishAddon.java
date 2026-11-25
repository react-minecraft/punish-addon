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
import com.g4vrk.punishAddon.sequence.builtin.renderer.HolyWorldRenderer;
import com.g4vrk.react.api.addon.JavaAddon;
import com.g4vrk.react.api.config.migration.ConfigMigrationChain;
import com.g4vrk.react.api.event.bus.Subscribe;
import com.g4vrk.react.api.event.plugin.ReloadEvent;
import com.g4vrk.react.api.platform.player.PlatformPlayer;
import com.g4vrk.schedula.task.Task;
import com.g4vrk.schedula.task.TickSchedule;
import net.kyori.adventure.audience.Audience;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Punishes players with a command and plays an animation over them first.
 *
 * <p>The command and reload subscription belong to the addon environment. Deferred actions are
 * resumed from the platform's online-player view.</p>
 */
@ApiStatus.Internal
public final class ReactPunishAddon extends JavaAddon {

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

    private final Object lifecycleLock = new Object();

    private PendingActionManager pending;
    private PunishmentActionRunner actionRunner;
    private HolyWorldRenderer renderer;
    private Task reconnectMonitor;
    private volatile LoadedSettings settings = new LoadedSettings(DEFAULT_LABEL, Map.of());
    private volatile boolean disabled;

    @Override
    public void onEnable() {

        this.pending = new PendingActionManager(logger());
        this.actionRunner = new PunishmentActionRunner(environment().scheduler(), logger(), this.pending);
        this.renderer = loadRenderer();
        final LoadedSettings initial = load();
        this.reconnectMonitor = environment().scheduler().schedule(
                () -> this.pending.pollOnline(environment().platform().onlinePlayers()),
                TickSchedule.repeating(20, 20)
        );
        environment().listeners().register(this);
        registerCommand(initial);
        logger().info("Punish types: {}, command /react {}", initial.types().keySet(), initial.label());

    }

    @Override
    public void onDisable() {

        synchronized (this.lifecycleLock) {
            this.disabled = true;
            if (this.pending != null) {
                this.pending.clear();
            }
            if (this.reconnectMonitor != null) {
                this.reconnectMonitor.cancel();
                this.reconnectMonitor = null;
            }
        }

    }

    /**
     * Re-reads the configs after {@code /react reload} and rebuilds the command, since its
     * literals are the type and animation ids.
     */
    @Subscribe
    public void onReload(
            final @NotNull ReloadEvent event
    ) {

        synchronized (this.lifecycleLock) {
            if (this.disabled) {
                return;
            }
            final LoadedSettings reloaded;
            try {
                reloaded = load();
            } catch (final RuntimeException exception) {
                logger().error("Could not reload the punish addon, keeping the old settings", exception);
                return;
            }
            environment().scheduler().schedule(() -> {
                synchronized (this.lifecycleLock) {
                    if (this.disabled) {
                        return;
                    }
                    environment().commands().unregister(PunishCommand.ROOT);
                    registerCommand(reloaded);
                }
            }, TickSchedule.instant());
        }

    }

    private void registerCommand(
            final @NotNull LoadedSettings current
    ) {

        new PunishCommand(current.label(), current.types(), this::punish).register(environment().commands());

    }

    private void punish(
            final @NotNull PlatformPlayer player,
            final @NotNull PunishContext context
    ) {

        final Sequence<PlatformPlayer> sequence = context.punishType().sequence(context.animation());
        if (sequence == null) {
            logger().warn("Animation '{}' is unavailable, punishing {} without it", context.animation(), player.name());
            execute(player, context, List.of());
            return;
        }
        sequence.newSession(player, (target, before) -> execute(target, context, before)).start();

    }

    private void execute(
            final @NotNull PlatformPlayer player,
            final @NotNull PunishContext context,
            final @NotNull Iterable<? extends ExecutableAction<? super Audience>> before
    ) {

        final Map<String, String> placeholders = Map.of(
                "{player.name}", player.name(),
                "{player.uuid}", player.uniqueId().toString(),
                "{context.input-after-type}", context.reason(),
                "{reason}", context.reason()
        );
        this.actionRunner.runTracked(
                player,
                before,
                context.punishType().actions(),
                input -> replacePlaceholders(input, placeholders)
        );

    }

    private static @Nullable String replacePlaceholders(
            final @Nullable String input,
            final @NotNull Map<String, String> placeholders
    ) {

        if (input == null || input.indexOf('{') < 0) {
            return input;
        }
        final StringBuilder result = new StringBuilder(input.length() + 16);
        int cursor = 0;
        while (cursor < input.length()) {
            final int open = input.indexOf('{', cursor);
            if (open < 0) {
                result.append(input, cursor, input.length());
                break;
            }
            final int close = input.indexOf('}', open + 1);
            if (close < 0) {
                result.append(input, cursor, input.length());
                break;
            }
            final String replacement = placeholders.get(input.substring(open, close + 1));
            if (replacement != null) {
                result.append(input, cursor, open);
                result.append(replacement);
                cursor = close + 1;
            } else {
                result.append(input, cursor, open + 1);
                cursor = open + 1;
            }
        }
        return result.toString();

    }

    private @NotNull HolyWorldRenderer loadRenderer() {

        final String platformName = environment().platform().platformName();
        final Iterator<HolyWorldRenderer> renderers = ServiceLoader.load(
                HolyWorldRenderer.class,
                getClass().getClassLoader()
        ).iterator();
        if (!renderers.hasNext()) {
            throw new IllegalStateException(
                    "No HolyWorld renderer is installed for platform '" + platformName + "'"
            );
        }
        final HolyWorldRenderer loaded = renderers.next();
        if (renderers.hasNext()) {
            throw new IllegalStateException(
                    "Multiple HolyWorld renderers are installed for platform '" + platformName + "'"
            );
        }
        return loaded;

    }

    private @NotNull LoadedSettings load() {

        final CommentedConfigurationNode main = read(MAIN_CONFIG, MAIN_MIGRATIONS);
        final CommentedConfigurationNode holyWorld = read(HOLYWORLD_CONFIG, ConfigMigrationChain.none());
        final ActionParser<Audience> parser = environment().actions();
        final Map<String, Sequence<PlatformPlayer>> animations = BuiltInSequenceBootstrap.bootstrap(
                environment().scheduler(),
                parser,
                this.actionRunner,
                this.pending,
                holyWorld,
                this.renderer
        );
        final ConfigurationNode settingsNode = main.node("settings");
        /*
         * Older configurations still use the argument-literal key for the command name.
         */
        final String label = settingsNode.node("command")
                .getString(settingsNode.node("argument-literal").getString(DEFAULT_LABEL))
                .trim();
        if (!label.matches("[a-z0-9_-]+")) {
            throw new IllegalArgumentException("settings.command must be a lowercase command literal");
        }
        final Map<String, PunishType> types;
        try {
            types = loadTypes(main.node("types"), animations, parser);
        } catch (final SerializationException exception) {
            throw new IllegalStateException("Could not read the punishment types", exception);
        }
        final LoadedSettings loaded = new LoadedSettings(label, types);
        this.settings = loaded;
        return loaded;

    }

    private @NotNull Map<String, PunishType> loadTypes(
            final @NotNull ConfigurationNode node,
            final @NotNull Map<String, Sequence<PlatformPlayer>> animations,
            final @NotNull ActionParser<Audience> parser
    ) throws SerializationException {

        final Map<String, PunishType> types = new LinkedHashMap<>();
        for (final Map.Entry<Object, ? extends ConfigurationNode> entry : node.childrenMap().entrySet()) {
            final String id = String.valueOf(entry.getKey());
            final ConfigurationNode type = entry.getValue();
            final ConfigurationNode animation = type.node("animation");
            final AnimationConfig animationConfig = new AnimationConfig(
                    animation.node("need-to-select-in-command").getBoolean(false),
                    animation.node("selected").getString(BuiltInSequenceBootstrap.HOLYWORLD)
            );
            if (!animationConfig.needToSelectInCommand() && !animations.containsKey(animationConfig.selected())) {
                logger().warn("Type '{}' selects the unknown animation '{}'", id, animationConfig.selected());
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
            types.put(id, new PunishType(animations, animationConfig, List.copyOf(actions)));
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

    private record LoadedSettings(
            @NotNull String label,
            @NotNull Map<String, PunishType> types
    ) {

    }

}
