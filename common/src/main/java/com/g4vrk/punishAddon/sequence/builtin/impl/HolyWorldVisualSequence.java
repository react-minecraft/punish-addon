package com.g4vrk.punishAddon.sequence.builtin.impl;

import com.g4vrk.functionalActions.list.ExecutableActionList;
import com.g4vrk.punishAddon.action.PunishmentActionRunner;
import com.g4vrk.punishAddon.sequence.PendingActionManager;
import com.g4vrk.punishAddon.sequence.impl.VisualSequence;
import com.g4vrk.schedula.task.scheduler.Scheduler;
import net.kyori.adventure.audience.Audience;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.ConfigurationNode;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

/**
 * Plays the HolyWorld levitation and particle animation around a player.
 */
public final class HolyWorldVisualSequence extends VisualSequence {

    private static final Particle DUST = dustParticle();

    private static final double TWO_PI = Math.PI * 2.0D;

    private int particleCount;

    private double verticalOffset;

    private double rotationSpeed;

    private double zRotationSin;
    private double zRotationCos;

    private double randomOffsetHalf;

    private Particle.DustOptions dust1;
    private Particle.DustOptions dust2;

    private double[] circleX;
    private double[] circleZ;

    private static @NotNull Particle dustParticle() {

        try {
            return Particle.valueOf("DUST");
        } catch (final IllegalArgumentException legacy) {
            return Particle.valueOf("REDSTONE");
        }

    }

    public HolyWorldVisualSequence(
            final long duration,
            final @NotNull Scheduler scheduler,
            final @NotNull Function<List<String>, ExecutableActionList<? super Audience>> actionParser,
            final @NotNull PunishmentActionRunner actionRunner,
            final @NotNull PendingActionManager pendingActionManager,
            final @NotNull ConfigurationNode config
    ) {

        super(duration, scheduler, actionParser, actionRunner, pendingActionManager);

        this.onReload(config);

    }

    @Override
    public void onReload(
            final @NotNull ConfigurationNode config
    ) {

        super.onReload(config);

        this.particleCount = Math.max(
                1,
                config.node("particle-count")
                        .getInt(32)
        );

        final double radius = Math.max(
                0.0D,
                config.node("radius")
                        .getDouble(1.0D)
        );

        this.verticalOffset =
                config.node("vertical-offset")
                        .getDouble(0.0D);

        this.rotationSpeed = Math.toRadians(
                config.node("rotation-speed")
                        .getDouble(3.0D)
        );

        final double zRotation =
                Math.toRadians(
                        config.node("z-rotation")
                                .getDouble(45.0D)
                );

        this.zRotationSin = Math.sin(zRotation);
        this.zRotationCos = Math.cos(zRotation);

        final double randomOffset = Math.max(
                0.0D,
                config.node("random-offset")
                        .getDouble(0.02D)
        );

        this.randomOffsetHalf =
                randomOffset * 0.5D;

        final float dustSize = Math.max(
                0.01F,
                (float) config.node("dust-size")
                        .getDouble(1.5D)
        );

        final Color color1 = parseColor(
                config.node("color-1")
                        .getString("#FFFFFF")
        );

        final Color color2 = parseColor(
                config.node("color-2")
                        .getString("#3DC0FF")
        );

        this.dust1 = new Particle.DustOptions(
                color1,
                dustSize
        );

        this.dust2 = new Particle.DustOptions(
                color2,
                dustSize
        );

        final double[] circleX =
                new double[particleCount];

        final double[] circleZ =
                new double[particleCount];

        final double angleStep =
                TWO_PI / particleCount;

        for (int i = 0; i < particleCount; i++) {

            final double angle =
                    angleStep * i;

            circleX[i] =
                    radius * Math.cos(angle);

            circleZ[i] =
                    radius * Math.sin(angle);

        }

        this.circleX = circleX;
        this.circleZ = circleZ;

    }

    @Override
    protected void start(
            final @NotNull Player source
    ) {

        source.addPotionEffect(PotionEffectType.LEVITATION.createEffect(Math.toIntExact(duration()), 1));

    }

    @Override
    protected void tick(
            final @NotNull Player player,
            final long tick
    ) {

        final var center = player.getLocation();

        final double centerX =
                center.getX();

        final double centerY =
                center.getY();

        final double centerZ =
                center.getZ();

        final double rotation =
                tick * rotationSpeed;

        final double rotationSin =
                Math.sin(rotation);

        final double rotationCos =
                Math.cos(rotation);

        final double zSin =
                zRotationSin;

        final double zCos =
                zRotationCos;

        final double vertical =
                verticalOffset;

        final double offset =
                randomOffsetHalf;

        final double[] circleX =
                this.circleX;

        final double[] circleZ =
                this.circleZ;

        final ThreadLocalRandom random =
                ThreadLocalRandom.current();

        final Particle.DustOptions dust1 =
                this.dust1;

        final Particle.DustOptions dust2 =
                this.dust2;

        for (int i = 0; i < particleCount; i++) {

            final double localX =
                    circleX[i];

            final double localZ =
                    circleZ[i];

            final double rotatedX =
                    localX * zCos
                            - vertical * zSin;

            final double rotatedY =
                    localX * zSin
                            + vertical * zCos;

            final double finalX =
                    rotatedX * rotationCos
                            + localZ * rotationSin;

            final double finalZ =
                    -rotatedX * rotationSin
                            + localZ * rotationCos;

            player.getWorld().spawnParticle(
                    DUST,

                    centerX
                            + finalX
                            + random.nextDouble(
                            -offset,
                            offset
                    ),

                    centerY
                            + rotatedY
                            + random.nextDouble(
                            -offset,
                            offset
                    ),

                    centerZ
                            + finalZ
                            + random.nextDouble(
                            -offset,
                            offset
                    ),

                    1,

                    0.0D,
                    0.0D,
                    0.0D,

                    0.0D,

                    random.nextBoolean()
                            ? dust1
                            : dust2
            );

        }

    }

    private static @NotNull Color parseColor(
            final @NotNull String value
    ) {

        final String hex =
                value.charAt(0) == '#'
                        ? value.substring(1)
                        : value;

        if (hex.length() != 6) {

            throw new IllegalArgumentException(
                    "Invalid color '" + value
                            + "'. Expected #RRGGBB."
            );

        }

        try {

            return Color.fromRGB(
                    Integer.parseInt(
                            hex.substring(0, 2),
                            16
                    ),
                    Integer.parseInt(
                            hex.substring(2, 4),
                            16
                    ),
                    Integer.parseInt(
                            hex.substring(4, 6),
                            16
                    )
            );

        } catch (final NumberFormatException ex) {

            throw new IllegalArgumentException(
                    "Invalid color '" + value
                            + "'. Expected #RRGGBB.",
                    ex
            );

        }

    }

}
