package me.usainsrht.basicparkour.core.listener;

import me.usainsrht.basicparkour.core.BasicParkourPlugin;
import me.usainsrht.basicparkour.core.session.SessionManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Handles player connection events (quit, join, world change) during active sessions
 * and updates player visibility state.
 */
public final class PlayerQuitListener implements Listener {

    private final BasicParkourPlugin plugin;
    private final SessionManager sessionManager;

    public PlayerQuitListener(@NotNull BasicParkourPlugin plugin, @NotNull SessionManager sessionManager) {
        this.plugin = plugin;
        this.sessionManager = sessionManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(@NotNull PlayerQuitEvent event) {
        Player player = event.getPlayer();
        sessionManager.getSession(player).ifPresent(session -> {
            Location joinLoc = session.getJoinLocation();
            sessionManager.removeSession(player);
            if (plugin.getConfig().getBoolean("session.teleport-on-leave", true) && joinLoc != null) {
                player.teleport(joinLoc);
            }
        });
        sessionManager.handlePlayerQuitCleanup(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(@NotNull PlayerJoinEvent event) {
        sessionManager.handlePlayerJoinOrWorldChange(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldChange(@NotNull PlayerChangedWorldEvent event) {
        sessionManager.handlePlayerJoinOrWorldChange(event.getPlayer());
    }
}

