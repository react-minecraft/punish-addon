package com.g4vrk.punishAddon.bukkit.sequence;

import com.g4vrk.punishAddon.sequence.builtin.renderer.HolyWorldRenderer;
import com.g4vrk.punishAddon.sequence.builtin.renderer.ParticleFrame;
import com.g4vrk.react.api.platform.player.PlatformPlayer;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Renders the HolyWorld effect through the Bukkit API on the player's entity scheduler.
 */
public final class BukkitHolyWorldRenderer implements HolyWorldRenderer {

    private volatile Palette palette;

    @Override
    public void configure(final int first, final int second, final float size) {

        this.palette = new Palette(dust(first, size), dust(second, size));

    }

    @Override
    public void levitate(final @NotNull PlatformPlayer player, final long duration) {

        requirePlayer(player).addPotionEffect(new PotionEffect(
                PotionEffectType.LEVITATION,
                Math.toIntExact(duration),
                0,
                false,
                false,
                true
        ));

    }

    @Override
    public @Nullable ParticleFrame frame(final @NotNull PlatformPlayer player) {

        final Player target = requirePlayer(player);
        final Location location = target.getLocation();
        final World world = location.getWorld();
        final Palette current = this.palette;
        if (world == null) {
            return null;
        }
        if (current == null) {
            throw new IllegalStateException("HolyWorld renderer is not configured");
        }
        final double x = location.getX();
        final double y = location.getY();
        final double z = location.getZ();
        return (offsetX, offsetY, offsetZ, firstColor) -> world.spawnParticle(
                Particle.REDSTONE,
                x + offsetX,
                y + offsetY,
                z + offsetZ,
                1,
                0.0D,
                0.0D,
                0.0D,
                0.0D,
                firstColor ? current.first() : current.second()
        );

    }

    private static @NotNull Player requirePlayer(final @NotNull PlatformPlayer player) {

        final Player nativePlayer = player.unwrap(Player.class);
        if (nativePlayer == null) {
            throw new IllegalStateException("HolyWorld animation requires a Bukkit player");
        }
        return nativePlayer;

    }

    private static @NotNull Particle.DustOptions dust(final int rgb, final float size) {

        return new Particle.DustOptions(Color.fromRGB(rgb), size);

    }

    private record Palette(@NotNull Particle.DustOptions first, @NotNull Particle.DustOptions second) {

    }

}
