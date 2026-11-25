package com.g4vrk.punishAddon.sequence.builtin.renderer;

import com.g4vrk.react.api.platform.player.PlatformPlayer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Applies the platform effects used by the HolyWorld sequence.
 */
@ApiStatus.AvailableSince("1.0")
public interface HolyWorldRenderer {

    /**
     * Prepares the two configured dust colors and size.
     *
     * @param first  first packed RGB color
     * @param second second packed RGB color
     * @param size   dust size
     */
    void configure(
            final int first,
            final int second,
            final float size
    );

    /**
     * Applies levitation for the duration of the sequence.
     *
     * @param player   player whose entity scheduler executes this call
     * @param duration duration in ticks
     */
    void levitate(
            final @NotNull PlatformPlayer player,
            final long duration
    );

    /**
     * Emits a dust particle relative to the player's current position.
     *
     * @param player     target player
     * @param x          horizontal offset
     * @param y          vertical offset
     * @param z          horizontal offset
     * @param firstColor whether to use the first configured color
     */
    void dust(
            final @NotNull PlatformPlayer player,
            final double x,
            final double y,
            final double z,
            final boolean firstColor
    );

}
