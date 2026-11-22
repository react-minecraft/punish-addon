package com.g4vrk.punishAddon.config;

import org.jetbrains.annotations.NotNull;

public record AnimationConfig(
        boolean needToSelectInCommand,
        @NotNull String selected
) {
}
