package com.g4vrk.punishAddon.sequence.builtin.impl;

import com.g4vrk.functionalActions.list.ExecutableActionList;
import com.g4vrk.punishAddon.action.PunishmentActionRunner;
import com.g4vrk.punishAddon.sequence.PendingActionManager;
import com.g4vrk.punishAddon.sequence.builtin.renderer.HolyWorldRenderer;
import com.g4vrk.punishAddon.sequence.builtin.renderer.ParticleFrame;
import com.g4vrk.punishAddon.sequence.impl.VisualSequence;
import com.g4vrk.react.api.platform.player.PlatformPlayer;
import com.g4vrk.schedula.task.scheduler.TaskScheduler;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import net.kyori.adventure.audience.Audience;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.ConfigurationNode;

/**
 * Calculates the HolyWorld animation and delegates entity effects to the platform.
 */
public final class HolyWorldSequence extends VisualSequence {

    private final HolyWorldRenderer renderer;
    private int particleCount;
    private double verticalOffset;
    private double rotationSpeed;
    private double zRotationSin;
    private double zRotationCos;
    private double randomOffsetHalf;
    private double[] circleX;
    private double[] circleZ;

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
    public void onReload(final @NotNull ConfigurationNode config) {

        super.onReload(config);
        this.particleCount = Math.max(1, Math.min(128, config.node("particle-count").getInt(32)));
        final double radius = Math.max(0.0D, config.node("radius").getDouble(1.0D));
        this.verticalOffset = config.node("vertical-offset").getDouble(0.0D);
        this.rotationSpeed = Math.toRadians(config.node("rotation-speed").getDouble(3.0D));
        final double zRotation = Math.toRadians(config.node("z-rotation").getDouble(45.0D));
        this.zRotationSin = Math.sin(zRotation);
        this.zRotationCos = Math.cos(zRotation);
        this.randomOffsetHalf = Math.max(0.0D, config.node("random-offset").getDouble(0.02D)) * 0.5D;
        final float dustSize = Math.max(0.01F, Math.min(4.0F, (float) config.node("dust-size").getDouble(1.5D)));
        final int firstColor = color(config.node("color-1").getString("#FFFFFF"));
        final int secondColor = color(config.node("color-2").getString("#3DC0FF"));
        this.renderer.configure(firstColor, secondColor, dustSize);
        final double[] x = new double[this.particleCount];
        final double[] z = new double[this.particleCount];
        final double angleStep = Math.PI * 2.0D / this.particleCount;
        for (int index = 0; index < this.particleCount; index++) {
            final double angle = angleStep * index;
            x[index] = radius * Math.cos(angle);
            z[index] = radius * Math.sin(angle);
        }
        this.circleX = x;
        this.circleZ = z;

    }

    @Override
    protected void start(final @NotNull PlatformPlayer player) {

        this.renderer.levitate(player, duration());

    }

    @Override
    protected void tick(final @NotNull PlatformPlayer player, final long tick) {

        final ParticleFrame frame = this.renderer.frame(player);
        if (frame == null) {
            return;
        }
        final double rotation = tick * this.rotationSpeed;
        final double rotationSin = Math.sin(rotation);
        final double rotationCos = Math.cos(rotation);
        final ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int index = 0; index < this.particleCount; index++) {
            final double rotatedX = this.circleX[index] * this.zRotationCos - this.verticalOffset * this.zRotationSin;
            final double rotatedY = this.circleX[index] * this.zRotationSin + this.verticalOffset * this.zRotationCos;
            final double finalX = rotatedX * rotationCos + this.circleZ[index] * rotationSin;
            final double finalZ = -rotatedX * rotationSin + this.circleZ[index] * rotationCos;
            frame.dust(
                    finalX + (random.nextDouble() * 2.0D - 1.0D) * this.randomOffsetHalf,
                    rotatedY + (random.nextDouble() * 2.0D - 1.0D) * this.randomOffsetHalf,
                    finalZ + (random.nextDouble() * 2.0D - 1.0D) * this.randomOffsetHalf,
                    random.nextBoolean()
            );
        }

    }

    private static int color(final @NotNull String value) {

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

}
