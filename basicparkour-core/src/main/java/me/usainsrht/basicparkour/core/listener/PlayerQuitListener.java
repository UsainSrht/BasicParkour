package me.usainsrht.basicparkour.core.listener;

import me.usainsrht.basicparkour.core.BasicParkourPlugin;
import me.usainsrht.basicparkour.core.session.SessionManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Handles player disconnection during an active session.
 *
 * <p>Restores player inventory and survival state, and teleports them to their
 * pre-parkour join location if teleport-on-leave is enabled so they do not log back
 * in stranded on the parkour course.</p>
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
    }
}

