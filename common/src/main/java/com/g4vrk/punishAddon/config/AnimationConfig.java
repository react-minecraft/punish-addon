package com.g4vrk.punishAddon.config;

import org.jetbrains.annotations.NotNull;

/**
 * Per-punishment animation selection settings.
 */
public record AnimationConfig(
        boolean needToSelectInCommand,
        @NotNull String selected
) {
}
