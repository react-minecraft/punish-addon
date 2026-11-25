package com.g4vrk.punishAddon.sponge.sequence;

import com.g4vrk.punishAddon.sequence.builtin.renderer.HolyWorldRenderer;
import com.g4vrk.punishAddon.sequence.builtin.renderer.ParticleFrame;
import com.g4vrk.react.api.platform.player.PlatformPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.api.effect.particle.ParticleEffect;
import org.spongepowered.api.effect.particle.ParticleOptions;
import org.spongepowered.api.effect.particle.ParticleTypes;
import org.spongepowered.api.effect.potion.PotionEffect;
import org.spongepowered.api.effect.potion.PotionEffectTypes;
import org.spongepowered.api.entity.living.player.server.ServerPlayer;
import org.spongepowered.api.util.Color;
import org.spongepowered.api.util.Ticks;
import org.spongepowered.api.world.server.ServerWorld;
import org.spongepowered.math.vector.Vector3d;

/**
 * Renders the HolyWorld effect through SpongeAPI on the player's server entity thread.
 */
public final class SpongeHolyWorldRenderer implements HolyWorldRenderer {

    private volatile Palette palette;

    @Override
    public void configure(final int first, final int second, final float size) {

        this.palette = new Palette(particle(first, size), particle(second, size));

    }

    @Override
    public void levitate(final @NotNull PlatformPlayer player, final long duration) {

        requirePlayer(player).potionEffects().add(PotionEffect.builder()
                .potionType(PotionEffectTypes.LEVITATION)
                .duration(Ticks.of(duration))
                .amplifier(0)
                .showParticles(false)
                .build());

    }

    @Override
    public @Nullable ParticleFrame frame(final @NotNull PlatformPlayer player) {

        final ServerPlayer target = requirePlayer(player);
        final ServerWorld world = target.world();
        final Vector3d position = target.position();
        final double x = position.x();
        final double y = position.y();
        final double z = position.z();
        final Palette current = this.palette;
        if (current == null) {
            throw new IllegalStateException("HolyWorld renderer is not configured");
        }
        return (offsetX, offsetY, offsetZ, firstColor) -> world.spawnParticles(
                firstColor ? current.first() : current.second(),
                x + offsetX,
                y + offsetY,
                z + offsetZ
        );

    }

    private static @NotNull ServerPlayer requirePlayer(final @NotNull PlatformPlayer player) {

        final ServerPlayer nativePlayer = player.unwrap(ServerPlayer.class);
        if (nativePlayer == null) {
            throw new IllegalStateException("HolyWorld animation requires a Sponge server player");
        }
        return nativePlayer;

    }

    private static @NotNull ParticleEffect particle(final int rgb, final float size) {

        return ParticleEffect.builder()
                .type(ParticleTypes.DUST.get())
                .option(ParticleOptions.COLOR.get(), Color.ofRgb(rgb))
                .scale((double) size)
                .build();

    }

    private record Palette(@NotNull ParticleEffect first, @NotNull ParticleEffect second) {

    }

}
