package com.g4vrk.punishAddon.sequence.builtin.renderer;

import com.g4vrk.react.api.platform.player.PlatformPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Applies the platform effects used by the HolyWorld sequence.
 */
public interface HolyWorldRenderer {

    /**
     * Prepares the two configured dust colors and size.
     *
     * @param first first packed RGB color
     * @param second second packed RGB color
     * @param size dust size
     */
    void configure(final int first, final int second, final float size);

    /**
     * Applies levitation for the duration of the sequence.
     *
     * @param player player whose entity scheduler executes this call
     * @param duration duration in ticks
     */
    void levitate(final @NotNull PlatformPlayer player, final long duration);

    /**
     * Captures the current particle origin on the player's entity thread.
     *
     * @param player target player
     * @return a frame renderer, or null when the player has no world
     */
    @Nullable ParticleFrame frame(final @NotNull PlatformPlayer player);

}
