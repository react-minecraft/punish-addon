package com.g4vrk.punishAddon.sequence.builtin.renderer;

/**
 * Emits a dust particle relative to a captured player position.
 */
@FunctionalInterface
public interface ParticleFrame {

    /**
     * Sends a particle at a relative position.
     *
     * @param x horizontal offset
     * @param y vertical offset
     * @param z horizontal offset
     * @param firstColor whether to use the first configured color
     */
    void dust(final double x, final double y, final double z, final boolean firstColor);

}
