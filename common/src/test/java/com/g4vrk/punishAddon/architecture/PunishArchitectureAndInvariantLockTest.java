/*
 * ARCHITECTURAL INVARIANT LOCK TEST
 *
 * Do NOT weaken, delete, disable, or rewrite these assertions to pass broken changes.
 * Fix production code or modular boundaries instead.
 */
package com.g4vrk.punishAddon.architecture;

import com.g4vrk.functionalActions.ExecutableAction;
import com.g4vrk.functionalActions.impl.UncontextualAction;
import com.g4vrk.functionalActions.list.ExecutableActionList;
import com.g4vrk.punishAddon.action.PunishmentActionRunner;
import com.g4vrk.punishAddon.sequence.PendingActionManager;
import com.g4vrk.punishAddon.sequence.SequenceSession;
import com.g4vrk.punishAddon.sequence.builtin.BuiltInSequenceRegistry;
import com.g4vrk.punishAddon.sequence.impl.TimedTickingSequence;
import com.g4vrk.punishAddon.sequence.impl.VisualSequence;
import com.g4vrk.react.api.platform.player.PlatformPlayer;
import com.g4vrk.schedula.task.Task;
import com.g4vrk.schedula.task.scheduler.TaskScheduler;
import net.kyori.adventure.audience.Audience;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.slf4j.helpers.NOPLogger;
import org.spongepowered.configurate.BasicConfigurationNode;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards punish-addon state-machine correctness, scheduler leak safety, modularity, and
 * repository conventions.
 */
final class PunishArchitectureAndInvariantLockTest {

    private static final Pattern IMPORT_PATTERN = Pattern.compile("^import\\s+(?:static\\s+)?([\\w.*]+);", Pattern.MULTILINE);

    @Test
    void sourcesRemainFreeOfFastutilEmptyDirsWildcardsAndBukkitLeaks() throws IOException {

        final Path repo = repoRoot();
        final List<String> violations = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(repo.resolve("common/src/main/java"))) {
            for (final Path file : walk.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                final String content = Files.readString(file);
                final Matcher matcher = IMPORT_PATTERN.matcher(content);
                while (matcher.find()) {
                    final String imported = matcher.group(1);
                    if (imported.endsWith(".*")) {
                        violations.add(file.getFileName() + " uses wildcard import " + imported);
                    }
                    if (imported.startsWith("it.unimi.dsi.fastutil")) {
                        violations.add(file.getFileName() + " leaks Fastutil " + imported);
                    }
                }
                if (content.contains("import org.bukkit.")) {
                    violations.add(file.getFileName() + " leaks Bukkit into common");
                }
            }
        }
        try (Stream<Path> dirs = Files.walk(repo.resolve("common/src"))) {
            for (final Path dir : dirs.filter(Files::isDirectory).toList()) {
                try (Stream<Path> children = Files.list(dir)) {
                    if (children.findAny().isEmpty()) {
                        violations.add("Empty directory present: " + repo.relativize(dir));
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(), "punish-addon invariant violation: " + violations);

    }

    @Test
    void pendingActionPollDoesNotInterruptActiveOnlineAnimationsAndResumesDeferredOnce() {

        final PendingActionManager manager = new PendingActionManager(NOPLogger.NOP_LOGGER);
        final UUID playerId = UUID.randomUUID();
        final AtomicBoolean onlineFlag = new AtomicBoolean(true);
        final PlatformPlayer firstSession = proxyPlayer(playerId, onlineFlag, 10);
        final AtomicInteger completions = new AtomicInteger();
        final AtomicBoolean taskCancelled = new AtomicBoolean();
        final PendingActionManager.Action active = manager.track(firstSession, target -> completions.incrementAndGet());
        active.attach(dummyTask(taskCancelled));

        final PlatformPlayer sameSessionWrapper = proxyPlayer(playerId, onlineFlag, 10);
        manager.pollOnline(List.of(sameSessionWrapper));
        assertTrue(active.active(), "Online poll with a new wrapper for the same entity must not defer an active animation");
        assertFalse(taskCancelled.get(), "Online poll must not cancel an active animation task");
        assertEquals(0, completions.get());

        onlineFlag.set(false);
        manager.pollOnline(List.of());
        assertFalse(active.active());
        assertTrue(active.deferred());
        assertTrue(taskCancelled.get(), "Going offline must cancel the animation task and defer completion");

        onlineFlag.set(true);
        final PlatformPlayer rejoined = proxyPlayer(playerId, onlineFlag, 11);
        manager.pollOnline(List.of(rejoined));
        manager.pollOnline(List.of(rejoined));
        assertEquals(1, completions.get(), "Deferred action must execute exactly once upon reconnect");

        final AtomicBoolean clearCancelled = new AtomicBoolean();
        final PendingActionManager.Action toClear = manager.track(rejoined, target -> completions.incrementAndGet());
        toClear.attach(dummyTask(clearCancelled));
        manager.clear();
        assertTrue(clearCancelled.get());
        assertFalse(toClear.complete(rejoined));

    }

    @Test
    void reconnectDuringSamePollIntervalDetectsReplacedPlayerSessionWithoutDoubleCompletion() {

        final PendingActionManager manager = new PendingActionManager(NOPLogger.NOP_LOGGER);
        final UUID playerId = UUID.randomUUID();
        final AtomicBoolean oldOnline = new AtomicBoolean(false);
        final PlatformPlayer oldHandle = proxyPlayer(playerId, oldOnline, 10);
        final AtomicBoolean taskCancelled = new AtomicBoolean();
        final AtomicInteger completions = new AtomicInteger();
        final PendingActionManager.Action action = manager.track(oldHandle, target -> completions.incrementAndGet());
        action.attach(dummyTask(taskCancelled));

        final PlatformPlayer newHandle = proxyPlayer(playerId, new AtomicBoolean(true), 11);
        manager.pollOnline(List.of(newHandle));
        assertTrue(taskCancelled.get());
        assertEquals(1, completions.get());
        assertFalse(action.complete(newHandle));

    }

    @Test
    void pollOnlineContinuesResumingOtherPlayersWhenOneCompletionFails() {

        final PendingActionManager manager = new PendingActionManager(NOPLogger.NOP_LOGGER);
        final PlatformPlayer failingPlayer = proxyPlayer(UUID.randomUUID(), new AtomicBoolean(true), 1);
        final PlatformPlayer healthyPlayer = proxyPlayer(UUID.randomUUID(), new AtomicBoolean(true), 2);
        final AtomicInteger healthyResumed = new AtomicInteger();

        manager.track(failingPlayer, target -> {
            throw new IllegalStateException("boom");
        }).defer();
        manager.track(healthyPlayer, target -> healthyResumed.incrementAndGet()).defer();

        manager.pollOnline(List.of(failingPlayer, healthyPlayer));
        assertEquals(1, healthyResumed.get());

    }

    @Test
    void trackedRunExecutesUncontextualActionEvenWhenPlayerIsOffline() {

        final List<Runnable> globalRunnables = new ArrayList<>();
        final TaskScheduler<PlatformPlayer> scheduler = routingScheduler(globalRunnables);
        final PendingActionManager manager = new PendingActionManager(NOPLogger.NOP_LOGGER);
        final PunishmentActionRunner runner = new PunishmentActionRunner(scheduler, NOPLogger.NOP_LOGGER, manager);
        final PlatformPlayer offlinePlayer = proxyPlayer(UUID.randomUUID(), new AtomicBoolean(false), 1);
        final AtomicInteger executed = new AtomicInteger();

        final UncontextualAction consoleAction = new UncontextualAction() {

            @Override
            public void execute(
                    final @Nullable String value
            ) {

                executed.incrementAndGet();

            }

        };

        runner.runTracked(
                offlinePlayer,
                List.of(),
                List.of(new ExecutableAction<>(consoleAction, "console")),
                UnaryOperator.identity()
        );

        assertEquals(1, globalRunnables.size());
        globalRunnables.get(0).run();
        assertEquals(1, executed.get());

    }

    @Test
    void timedSequenceRejectsNonPositiveDurationAndCancelsOnTickFailure() {

        final TaskScheduler<PlatformPlayer> scheduler = failingScheduler();
        assertThrows(IllegalArgumentException.class, () -> new TimedTickingSequence(0L, scheduler) {

            @Override
            public @NotNull SequenceSession newSession(
                    final @NotNull PlatformPlayer source,
                    final @NotNull BiConsumer<PlatformPlayer, Iterable<? extends ExecutableAction<? super Audience>>> onEnd
            ) {

                throw new UnsupportedOperationException();

            }

        });

        final AtomicBoolean taskCancelled = new AtomicBoolean();
        final List<Consumer<Task>> captured = new ArrayList<>();
        final TaskScheduler<PlatformPlayer> capturingScheduler = capturingScheduler(captured, taskCancelled);
        final PendingActionManager manager = new PendingActionManager(NOPLogger.NOP_LOGGER);
        final PlatformPlayer player = proxyPlayer(UUID.randomUUID(), new AtomicBoolean(true), 1);
        manager.track(player, ignored -> { });
        final VisualSequence visual = new VisualSequence(
                5L,
                capturingScheduler,
                lines -> new ExecutableActionList<>(List.of()),
                new PunishmentActionRunner(capturingScheduler, NOPLogger.NOP_LOGGER, manager),
                manager
        ) {

            @Override
            protected void start(
                    final @NotNull PlatformPlayer source
            ) {

                throw new IllegalStateException("particle boom");

            }

            @Override
            protected void tick(
                    final @NotNull PlatformPlayer source,
                    final long tick
            ) {

            }

        };
        visual.onReload(BasicConfigurationNode.root());
        visual.newSession(player, (target, before) -> { }).start();

        assertEquals(1, captured.size());
        final Task task = dummyTask(taskCancelled);
        assertThrows(IllegalStateException.class, () -> captured.get(0).accept(task));
        assertTrue(taskCancelled.get(), "Failed tick handler must cancel its repeating task");

    }

    @Test
    void timedSequenceRunsWithoutExtraEmptyTick() {

        final AtomicBoolean taskCancelled = new AtomicBoolean();
        final List<Consumer<Task>> captured = new ArrayList<>();
        final TaskScheduler<PlatformPlayer> capturingScheduler = capturingScheduler(captured, taskCancelled);
        final PendingActionManager manager = new PendingActionManager(NOPLogger.NOP_LOGGER);
        final PlatformPlayer player = proxyPlayer(UUID.randomUUID(), new AtomicBoolean(true), 1);
        final List<String> events = new ArrayList<>();
        final VisualSequence visual = new VisualSequence(
                2L,
                capturingScheduler,
                lines -> new ExecutableActionList<>(List.of()),
                new PunishmentActionRunner(capturingScheduler, NOPLogger.NOP_LOGGER, manager),
                manager
        ) {

            @Override
            protected void start(
                    final @NotNull PlatformPlayer source
            ) {

                events.add("start");

            }

            @Override
            protected void tick(
                    final @NotNull PlatformPlayer source,
                    final long tick
            ) {

                events.add("tick:" + tick);

            }

        };
        visual.onReload(BasicConfigurationNode.root());
        visual.newSession(player, (target, before) -> events.add("end")).start();

        final Consumer<Task> runner = captured.get(0);
        final Task task = dummyTask(taskCancelled);
        runner.accept(task);
        runner.accept(task);
        runner.accept(task);

        assertEquals(List.of("start", "tick:0", "tick:1", "end"), events);
        assertTrue(taskCancelled.get());

    }

    @Test
    void sequenceRegistryStartsEmpty() {

        final BuiltInSequenceRegistry<PlatformPlayer> registry = new BuiltInSequenceRegistry<>();
        assertNull(registry.all().get("missing"));
        assertTrue(registry.all().isEmpty());

    }

    private static @NotNull PlatformPlayer proxyPlayer(
            final @NotNull UUID uuid,
            final @NotNull AtomicBoolean online,
            final int entityId
    ) {

        return (PlatformPlayer) Proxy.newProxyInstance(
                PlatformPlayer.class.getClassLoader(),
                new Class<?>[] {PlatformPlayer.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "uniqueId" -> uuid;
                    case "entityId" -> entityId;
                    case "name" -> "Player";
                    case "online" -> online.get();
                    default -> null;
                }
        );

    }

    private static @NotNull Task dummyTask(
            final @NotNull AtomicBoolean cancelled
    ) {

        return new Task() {

            @Override
            public void cancel() {

                cancelled.set(true);

            }

            @Override
            public boolean isCancelled() {

                return cancelled.get();

            }

            @Override
            public boolean sync() {

                return true;

            }

        };

    }

    @SuppressWarnings("unchecked")
    private static @NotNull TaskScheduler<PlatformPlayer> failingScheduler() {

        return (TaskScheduler<PlatformPlayer>) Proxy.newProxyInstance(
                TaskScheduler.class.getClassLoader(),
                new Class<?>[] {TaskScheduler.class},
                (proxy, method, args) -> {
                    throw new UnsupportedOperationException();
                }
        );

    }

    @SuppressWarnings("unchecked")
    private static @NotNull TaskScheduler<PlatformPlayer> routingScheduler(
            final @NotNull List<Runnable> globalRunnables
    ) {

        return (TaskScheduler<PlatformPlayer>) Proxy.newProxyInstance(
                TaskScheduler.class.getClassLoader(),
                new Class<?>[] {TaskScheduler.class},
                (proxy, method, args) -> {
                    if ("schedule".equals(method.getName()) && args != null && args.length > 0 && args[0] instanceof Runnable runnable) {
                        globalRunnables.add(runnable);
                        return dummyTask(new AtomicBoolean());
                    }
                    throw new AssertionError("Unexpected scheduler call: " + method.getName());
                }
        );

    }

    @SuppressWarnings("unchecked")
    private static @NotNull TaskScheduler<PlatformPlayer> capturingScheduler(
            final @NotNull List<Consumer<Task>> captured,
            final @NotNull AtomicBoolean cancelled
    ) {

        return (TaskScheduler<PlatformPlayer>) Proxy.newProxyInstance(
                TaskScheduler.class.getClassLoader(),
                new Class<?>[] {TaskScheduler.class},
                (proxy, method, args) -> {
                    if (args != null) {
                        for (final Object arg : args) {
                            if (arg instanceof Consumer<?> consumer) {
                                captured.add((Consumer<Task>) consumer);
                            }
                        }
                    }
                    return dummyTask(cancelled);
                }
        );

    }

    private static @NotNull Path repoRoot() {

        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("settings.gradle.kts"))
                    && Files.isDirectory(current.resolve("common/src/main/java"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate punish-addon repository root");

    }

}
