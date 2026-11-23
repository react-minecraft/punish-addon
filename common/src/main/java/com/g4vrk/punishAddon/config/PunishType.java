package com.g4vrk.punishAddon.config;

import com.g4vrk.punishAddon.sequence.Sequence;
import com.g4vrk.functionalActions.ExecutableAction;
import net.kyori.adventure.audience.Audience;
import com.g4vrk.react.api.platform.player.PlatformPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public record PunishType(
        @NotNull Map<String, Sequence<PlatformPlayer>> animations,
        @NotNull AnimationConfig animation,
        @NotNull List<ExecutableAction<? super Audience>> actions
) {

    public @Nullable Sequence<PlatformPlayer> sequence(
            final @NotNull String animationId
    ) {

        return this.animations.get(animationId);

    }

}
