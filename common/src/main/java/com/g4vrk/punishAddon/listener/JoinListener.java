package com.g4vrk.punishAddon.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public class JoinListener implements Listener {

    private final Consumer<Player> onJoin;

    public JoinListener(
            final @NotNull Consumer<Player> onJoin
    ) {
        this.onJoin = onJoin;
    }

    @EventHandler
    public void onJoin(@NotNull PlayerJoinEvent event) {

        onJoin.accept(event.getPlayer());

    }

}
