package com.g4vrk.punishAddon.bukkit.sequence;

import com.g4vrk.punishAddon.sequence.builtin.renderer.HolyWorldRenderer;
import com.g4vrk.react.api.platform.player.PlatformPlayer;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Renders the HolyWorld effect through the Bukkit API on the player's entity scheduler.
 */
@ApiStatus.Internal
public final class BukkitHolyWorldRenderer implements HolyWorldRenderer {

    private static final Particle DUST_PARTICLE = resolveDustParticle();
    private static final ThreadLocal<Location> REUSABLE_LOCATION = ThreadLocal.withInitial(() -> new Location(null, 0.0D, 0.0D, 0.0D));

    private volatile Palette palette;

    @Override
    public void configure(
            final int first,
            final int second,
            final float size
    ) {

        this.palette = new Palette(dust(first, size), dust(second, size));

    }

    @Override
    public void levitate(
            final @NotNull PlatformPlayer player,
            final long duration
    ) {

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
    public void dust(
            final @NotNull PlatformPlayer player,
            final double x,
            final double y,
            final double z,
            final boolean firstColor
    ) {

        final Player target = requirePlayer(player);
        final Palette current = this.palette;
        if (current == null) {
            throw new IllegalStateException("HolyWorld renderer is not configured");
        }
        final Location location = REUSABLE_LOCATION.get();
        try {
            target.getLocation(location);
            final World world = location.getWorld();
            if (world == null) {
                return;
            }
            world.spawnParticle(
                    DUST_PARTICLE,
                    location.getX() + x,
                    location.getY() + y,
                    location.getZ() + z,
                    1,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D,
                    firstColor ? current.first() : current.second()
            );
        } finally {
            location.setWorld(null);
        }

    }

    private static @NotNull Particle resolveDustParticle() {

        try {
            return Particle.valueOf("DUST");
        } catch (final IllegalArgumentException ignored) {
            return Particle.REDSTONE;
        }

    }

    private static @NotNull Player requirePlayer(
            final @NotNull PlatformPlayer player
    ) {

        final Player nativePlayer = player.unwrap(Player.class);
        if (nativePlayer == null) {
            throw new IllegalStateException("HolyWorld animation requires a Bukkit player");
        }
        return nativePlayer;

    }

    private static @NotNull Particle.DustOptions dust(
            final int rgb,
            final float size
    ) {

        return new Particle.DustOptions(Color.fromRGB(rgb), size);

    }

    private record Palette(
            @NotNull Particle.DustOptions first,
            @NotNull Particle.DustOptions second
    ) {

    }

}
