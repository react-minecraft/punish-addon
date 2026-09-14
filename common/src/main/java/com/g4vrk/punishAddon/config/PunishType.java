package com.g4vrk.punishAddon.config;

import com.g4vrk.punishAddon.sequence.Sequence;
import com.g4vrk.functionalActions.ExecutableAction;
import net.kyori.adventure.audience.Audience;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public record PunishType(
        @NotNull Map<String, Sequence<Player>> animations,
        @NotNull AnimationSettings animation,
        @NotNull List<ExecutableAction<? super Audience>> actions
) {

    public @Nullable Sequence<Player> sequence(
            final @NotNull String animationId
    ) {
        return this.animations.get(animationId);
    }
}