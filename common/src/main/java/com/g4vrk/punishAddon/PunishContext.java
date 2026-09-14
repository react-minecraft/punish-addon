package com.g4vrk.punishAddon;

import com.g4vrk.punishAddon.config.PunishType;
import org.incendo.cloud.bukkit.data.MultiplePlayerSelector;
import org.jetbrains.annotations.NotNull;

public record PunishContext(
        @NotNull String type,
        @NotNull String animation,
        @NotNull String inputAfterType,
        @NotNull PunishType punishType,
        @NotNull MultiplePlayerSelector players
) {
}