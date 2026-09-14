package com.g4vrk.punishAddon.config;

import org.jetbrains.annotations.NotNull;

public record AnimationSettings(
        boolean needToSelectInCommand,
        @NotNull String selected
) {
}
