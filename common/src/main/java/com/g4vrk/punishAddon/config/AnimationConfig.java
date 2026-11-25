package com.g4vrk.punishAddon.config;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Per-punishment animation selection settings.
 */
@ApiStatus.AvailableSince("1.0")
public record AnimationConfig(
        boolean needToSelectInCommand,
        @NotNull String selected
) {

}
