package com.g4vrk.punishAddon.minestom.sequence;

import com.g4vrk.punishAddon.sequence.builtin.renderer.HolyWorldRenderer;
import com.g4vrk.react.api.platform.player.PlatformPlayer;
import net.kyori.adventure.text.format.TextColor;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.network.packet.server.play.ParticlePacket;
import net.minestom.server.particle.Particle;
import net.minestom.server.potion.Potion;
import net.minestom.server.potion.PotionEffect;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Sends HolyWorld particles and levitation through Minestom.
 */
@ApiStatus.Internal
public final class MinestomHolyWorldRenderer implements HolyWorldRenderer {

    private volatile Palette palette;

    @Override
    public void configure(
            final int first,
            final int second,
            final float size
    ) {

        this.palette = new Palette(
                Particle.DUST.withProperties(TextColor.color(first), size),
                Particle.DUST.withProperties(TextColor.color(second), size)
        );

    }

    @Override
    public void levitate(
            final @NotNull PlatformPlayer player,
            final long duration
    ) {

        requirePlayer(player).addEffect(new Potion(PotionEffect.LEVITATION, 0, Math.toIntExact(duration)));

    }

    @Override
    public void dust(
            final @NotNull PlatformPlayer player,
            final double x,
            final double y,
            final double z,
            final boolean firstColor
    ) {

        final Player target = requirePlayer(player);
        if (target.getInstance() == null) {
            return;
        }
        final Palette current = this.palette;
        if (current == null) {
            throw new IllegalStateException("HolyWorld renderer is not configured");
        }
        final Pos origin = target.getPosition();
        target.sendPacketToViewersAndSelf(new ParticlePacket(
                firstColor ? current.first() : current.second(),
                origin.x() + x,
                origin.y() + y,
                origin.z() + z,
                0.0F,
                0.0F,
                0.0F,
                0.0F,
                1
        ));

    }

    private static @NotNull Player requirePlayer(
            final @NotNull PlatformPlayer player
    ) {

        final Player nativePlayer = player.unwrap(Player.class);
        if (nativePlayer == null) {
            throw new IllegalStateException("HolyWorld animation requires a Minestom player");
        }
        return nativePlayer;

    }

    /**
     * Immutable particle types for the current animation configuration.
     */
    private record Palette(
            @NotNull Particle.Dust first,
            @NotNull Particle.Dust second
    ) {

    }

}
