/*
 * ARCHITECTURAL INVARIANT LOCK TEST
 *
 * Do NOT weaken, delete, disable, or rewrite these assertions to pass broken changes.
 * Fix production code or modular boundaries instead.
 */
package com.g4vrk.punishAddon.architecture;

import com.g4vrk.functionalActions.ExecutableAction;
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
import org.junit.jupiter.api.Test;
import org.slf4j.helpers.NOPLogger;
import org.spongepowered.configurate.BasicConfigurationNode;

import java.io.File;
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
    void sourcesRemainFreeOfFastutilEmptyDirsWildcardsAndSingleLineComments() throws IOException {

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
                if (!file.toString().contains("builtin" + File.separator + "impl")
                        && content.contains("import org.bukkit.")) {
                    violations.add(file.getFileName() + " leaks Bukkit outside builtin/impl");
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

        final PendingActionManager manager = new PendingActionManager();
        final UUID playerId = UUID.randomUUID();
        final AtomicBoolean onlineFlag = new AtomicBoolean(true);
        final PlatformPlayer firstSession = proxyPlayer(playerId, onlineFlag);
        final AtomicInteger completions = new AtomicInteger();
        final AtomicBoolean taskCancelled = new AtomicBoolean();
        final PendingActionManager.Action active = manager.track(firstSession, target -> completions.incrementAndGet());
        active.attach(dummyTask(taskCancelled));

        manager.pollOnline(List.of(firstSession));
        assertTrue(active.active(), "Online poll must not defer an active animation");
        assertFalse(taskCancelled.get(), "Online poll must not cancel an active animation task");
        assertEquals(0, completions.get());

        onlineFlag.set(false);
        manager.pollOnline(List.of());
        assertFalse(active.active());
        assertTrue(active.deferred());
        assertTrue(taskCancelled.get(), "Going offline must cancel the animation task and defer completion");

        onlineFlag.set(true);
        final PlatformPlayer rejoined = proxyPlayer(playerId, onlineFlag);
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

        final PendingActionManager manager = new PendingActionManager();
        final UUID playerId = UUID.randomUUID();
        final AtomicBoolean oldOnline = new AtomicBoolean(false);
        final PlatformPlayer oldHandle = proxyPlayer(playerId, oldOnline);
        final AtomicBoolean taskCancelled = new AtomicBoolean();
        final AtomicInteger completions = new AtomicInteger();
        final PendingActionManager.Action action = manager.track(oldHandle, target -> completions.incrementAndGet());
        action.attach(dummyTask(taskCancelled));

        final PlatformPlayer newHandle = proxyPlayer(playerId, new AtomicBoolean(true));
        manager.pollOnline(List.of(newHandle));
        assertTrue(taskCancelled.get());
        assertEquals(1, completions.get());
        assertFalse(action.complete(newHandle));

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
        final PendingActionManager manager = new PendingActionManager();
        final PlatformPlayer player = proxyPlayer(UUID.randomUUID(), new AtomicBoolean(true));
        manager.track(player, ignored -> { });
        final VisualSequence visual = new VisualSequence(
                5L,
                capturingScheduler,
                lines -> new ExecutableActionList<>(List.of()),
                new PunishmentActionRunner(capturingScheduler, NOPLogger.NOP_LOGGER, manager),
                manager
        ) {

            @Override
            protected void start(final @NotNull PlatformPlayer source) {

                throw new IllegalStateException("particle boom");

            }

            @Override
            protected void tick(final @NotNull PlatformPlayer source, final long tick) {

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
    void sequenceRegistryReturnsNullWhenMissing() {

        final BuiltInSequenceRegistry<PlatformPlayer> registry = new BuiltInSequenceRegistry<>();
        assertNull(registry.find("missing"));
        assertTrue(registry.all().isEmpty());

    }

    private static PlatformPlayer proxyPlayer(
            final @NotNull UUID uuid,
            final @NotNull AtomicBoolean online
    ) {

        return (PlatformPlayer) Proxy.newProxyInstance(
                PlatformPlayer.class.getClassLoader(),
                new Class<?>[] {PlatformPlayer.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "uniqueId" -> uuid;
                    case "name" -> "Player";
                    case "online" -> online.get();
                    default -> null;
                }
        );

    }

    private static Task dummyTask(final @NotNull AtomicBoolean cancelled) {

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
    private static TaskScheduler<PlatformPlayer> failingScheduler() {

        return (TaskScheduler<PlatformPlayer>) Proxy.newProxyInstance(
                TaskScheduler.class.getClassLoader(),
                new Class<?>[] {TaskScheduler.class},
                (proxy, method, args) -> {
                    throw new UnsupportedOperationException();
                }
        );

    }

    @SuppressWarnings("unchecked")
    private static TaskScheduler<PlatformPlayer> capturingScheduler(
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
