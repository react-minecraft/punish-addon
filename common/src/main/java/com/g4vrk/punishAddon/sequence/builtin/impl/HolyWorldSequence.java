package com.g4vrk.punishAddon.sequence.builtin.impl;

import com.g4vrk.functionalActions.list.ExecutableActionList;
import com.g4vrk.punishAddon.action.PunishmentActionRunner;
import com.g4vrk.punishAddon.sequence.PendingActionManager;
import com.g4vrk.punishAddon.sequence.builtin.renderer.HolyWorldRenderer;
import com.g4vrk.punishAddon.sequence.impl.VisualSequence;
import com.g4vrk.react.api.platform.player.PlatformPlayer;
import com.g4vrk.schedula.task.scheduler.TaskScheduler;
import net.kyori.adventure.audience.Audience;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.ConfigurationNode;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

/**
 * Calculates the HolyWorld animation and delegates entity effects to the platform.
 */
@ApiStatus.Internal
public final class HolyWorldSequence extends VisualSequence {

    private final HolyWorldRenderer renderer;
    private volatile Geometry geometry = Geometry.DEFAULT;

    public HolyWorldSequence(
            final long duration,
            final @NotNull TaskScheduler<PlatformPlayer> scheduler,
            final @NotNull Function<List<String>, ExecutableActionList<? super Audience>> actionParser,
            final @NotNull PunishmentActionRunner actionRunner,
            final @NotNull PendingActionManager pending,
            final @NotNull ConfigurationNode config,
            final @NotNull HolyWorldRenderer renderer
    ) {

        super(duration, scheduler, actionParser, actionRunner, pending);
        this.renderer = renderer;
        onReload(config);

    }

    @Override
    public void onReload(
            final @NotNull ConfigurationNode config
    ) {

        super.onReload(config);
        final int particleCount = Math.max(1, Math.min(128, config.node("particle-count").getInt(32)));
        final double radius = Math.max(0.0D, config.node("radius").getDouble(1.0D));
        final double verticalOffset = config.node("vertical-offset").getDouble(0.0D);
        final double rotationSpeed = Math.toRadians(config.node("rotation-speed").getDouble(3.0D));
        final double zRotation = Math.toRadians(config.node("z-rotation").getDouble(45.0D));
        final double randomOffsetHalf = Math.max(0.0D, config.node("random-offset").getDouble(0.02D)) * 0.5D;
        final float dustSize = Math.max(0.01F, Math.min(4.0F, (float) config.node("dust-size").getDouble(1.5D)));
        final int firstColor = color(config.node("color-1").getString("#FFFFFF"));
        final int secondColor = color(config.node("color-2").getString("#3DC0FF"));
        this.renderer.configure(firstColor, secondColor, dustSize);
        this.geometry = Geometry.create(
                particleCount,
                radius,
                verticalOffset,
                rotationSpeed,
                zRotation,
                randomOffsetHalf
        );

    }

    @Override
    protected void start(
            final @NotNull PlatformPlayer player
    ) {

        this.renderer.levitate(player, duration());

    }

    @Override
    protected void tick(
            final @NotNull PlatformPlayer player,
            final long tick
    ) {

        final Geometry current = this.geometry;
        final double rotation = tick * current.rotationSpeed();
        final double rotationSin = Math.sin(rotation);
        final double rotationCos = Math.cos(rotation);
        final ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int index = 0; index < current.particleCount(); index++) {
            final double circleX = current.circleX()[index];
            final double circleZ = current.circleZ()[index];
            final double rotatedX = circleX * current.zRotationCos() - current.verticalOffset() * current.zRotationSin();
            final double rotatedY = circleX * current.zRotationSin() + current.verticalOffset() * current.zRotationCos();
            final double finalX = rotatedX * rotationCos + circleZ * rotationSin;
            final double finalZ = -rotatedX * rotationSin + circleZ * rotationCos;
            this.renderer.dust(
                    player,
                    finalX + (random.nextDouble() * 2.0D - 1.0D) * current.randomOffsetHalf(),
                    rotatedY + (random.nextDouble() * 2.0D - 1.0D) * current.randomOffsetHalf(),
                    finalZ + (random.nextDouble() * 2.0D - 1.0D) * current.randomOffsetHalf(),
                    random.nextBoolean()
            );
        }

    }

    private static int color(
            final @NotNull String value
    ) {

        final String hex = value.startsWith("#") ? value.substring(1) : value;
        if (hex.length() != 6) {
            throw new IllegalArgumentException("Invalid color '" + value + "'. Expected #RRGGBB.");
        }
        try {
            return Integer.parseInt(hex, 16);
        } catch (final NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid color '" + value + "'. Expected #RRGGBB.", exception);
        }

    }

    private record Geometry(
            int particleCount,
            double verticalOffset,
            double rotationSpeed,
            double zRotationSin,
            double zRotationCos,
            double randomOffsetHalf,
            double @NotNull [] circleX,
            double @NotNull [] circleZ
    ) {

        private static final Geometry DEFAULT = create(
                32,
                1.0D,
                0.0D,
                Math.toRadians(3.0D),
                Math.toRadians(45.0D),
                0.01D
        );

        private static @NotNull Geometry create(
                final int particleCount,
                final double radius,
                final double verticalOffset,
                final double rotationSpeed,
                final double zRotation,
                final double randomOffsetHalf
        ) {

            final double[] x = new double[particleCount];
            final double[] z = new double[particleCount];
            final double angleStep = Math.PI * 2.0D / particleCount;
            for (int index = 0; index < particleCount; index++) {
                final double angle = angleStep * index;
                x[index] = radius * Math.cos(angle);
                z[index] = radius * Math.sin(angle);
            }
            return new Geometry(
                    particleCount,
                    verticalOffset,
                    rotationSpeed,
                    Math.sin(zRotation),
                    Math.cos(zRotation),
                    randomOffsetHalf,
                    x,
                    z
            );

        }

    }

}
