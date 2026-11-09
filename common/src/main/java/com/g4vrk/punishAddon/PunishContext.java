package com.g4vrk.punishAddon;

import com.g4vrk.punishAddon.config.PunishType;
import org.jetbrains.annotations.NotNull;

/**
 * One punishment the command asked for.
 *
 * @param type      the type id from {@code main-config.yml}
 * @param animation the animation to play before the actions
 * @param reason    what followed the players in the command
 * @param punishType the type settings
 */
public record PunishContext(
        @NotNull String type,
        @NotNull String animation,
        @NotNull String reason,
        @NotNull PunishType punishType
) {
}
