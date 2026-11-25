package com.g4vrk.punishAddon.fabric.sequence;

import com.g4vrk.punishAddon.sequence.builtin.renderer.HolyWorldRenderer;
import com.g4vrk.react.api.platform.player.PlatformPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Renders the HolyWorld effect through Minecraft's Fabric server player API.
 */
@ApiStatus.Internal
public final class FabricHolyWorldRenderer implements HolyWorldRenderer {

    private volatile Palette palette;

    @Override
    public void configure(
            final int first,
            final int second,
            final float size
    ) {

        this.palette = new Palette(new DustParticleOptions(first, size), new DustParticleOptions(second, size));

    }

    @Override
    public void levitate(
            final @NotNull PlatformPlayer player,
            final long duration
    ) {

        requirePlayer(player).addEffect(new MobEffectInstance(
                MobEffects.LEVITATION,
                Math.toIntExact(duration),
                0,
                false,
                false,
                true
        ));

    }

    @Override
    public void dust(
            final @NotNull PlatformPlayer player,
            final double x,
            final double y,
            final double z,
            final boolean firstColor
    ) {

        final ServerPlayer target = requirePlayer(player);
        final ServerLevel world = (ServerLevel) target.level();
        final Palette current = this.palette;
        if (current == null) {
            throw new IllegalStateException("HolyWorld renderer is not configured");
        }
        world.sendParticles(
                firstColor ? current.first() : current.second(),
                target.getX() + x,
                target.getY() + y,
                target.getZ() + z,
                1,
                0.0D,
                0.0D,
                0.0D,
                0.0D
        );

    }

    private static @NotNull ServerPlayer requirePlayer(
            final @NotNull PlatformPlayer player
    ) {

        final ServerPlayer nativePlayer = player.unwrap(ServerPlayer.class);
        if (nativePlayer == null) {
            throw new IllegalStateException("HolyWorld animation requires a Fabric server player");
        }
        return nativePlayer;

    }

    private record Palette(
            @NotNull DustParticleOptions first,
            @NotNull DustParticleOptions second
    ) {

    }

}
