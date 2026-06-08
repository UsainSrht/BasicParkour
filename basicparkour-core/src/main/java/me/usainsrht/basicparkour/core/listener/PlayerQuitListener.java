package me.usainsrht.basicparkour.core.listener;

import me.usainsrht.basicparkour.core.session.SessionManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Handles player disconnection during an active session.
 *
 * <p>The session is invalidated (not failed, so no respawn teleport is issued)
 * and all resources are released. The ghost recording is discarded to avoid
 * saving an incomplete run.</p>
 */
public final class PlayerQuitListener implements Listener {

    private final SessionManager sessionManager;

    public PlayerQuitListener(@NotNull SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(@NotNull PlayerQuitEvent event) {
        Player player = event.getPlayer();
        sessionManager.getSession(player).ifPresent(session ->
            sessionManager.removeSession(player)
        );
    }
}
