package me.usainsrht.basicparkour.core.tab;

import me.neznamy.tab.api.TabAPI;
import me.neznamy.tab.api.TabPlayer;
import me.neznamy.tab.api.nametag.NameTagManager;
import me.usainsrht.basicparkour.core.BasicParkourPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

/**
 * Handles integration with NEZNAMY's TAB plugin.
 * Manages player collision rules and team handling during parkour sessions.
 */
public final class TabHook {

    private final BasicParkourPlugin plugin;
    private boolean available = false;
    private final Set<UUID> pausedPlayers = ConcurrentHashMap.newKeySet();

    public TabHook(@NotNull BasicParkourPlugin plugin) {
        this.plugin = plugin;
        init();
    }

    public void init() {
        if (Bukkit.getPluginManager().isPluginEnabled("TAB") && plugin.getConfig().getBoolean("tab.enabled", true)) {
            try {
                if (TabAPI.getInstance() != null) {
                    available = true;
                    plugin.getLogger().info("[BasicParkour] Hooked into TAB plugin successfully.");
                }
            } catch (Throwable t) {
                available = false;
                plugin.getLogger().warning("[BasicParkour] Failed to hook into TAB: " + t.getMessage());
            }
        } else {
            available = false;
        }
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     * Called when a player enters an active parkour session.
     */
    public void onPlayerEnterSession(@NotNull Player player) {
        if (!available) return;
        try {
            TabPlayer tabPlayer = TabAPI.getInstance().getPlayer(player.getUniqueId());
            if (tabPlayer == null) return;

            NameTagManager manager = TabAPI.getInstance().getNameTagManager();
            if (manager == null) return;

            // 1. Force disable collision via TAB
            if (plugin.getConfig().getBoolean("tab.disable-collision", true)) {
                manager.setCollisionRule(tabPlayer, false);
            }

            // 2. Hide nametags in parkour if configured
            if (plugin.getConfig().getBoolean("tab.hide-nametag", false)) {
                manager.hideNameTag(tabPlayer);
            }

            // 3. Pause TAB team handling if translucent mode / dedicated team is used
            // so TAB nametag teams do not override Vanilla canSeeFriendlyInvisibles teams
            if (plugin.getConfig().getBoolean("tab.pause-team-handling", true)) {
                manager.pauseTeamHandling(tabPlayer);
                pausedPlayers.add(player.getUniqueId());
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[BasicParkour] Error updating TAB state for " + player.getName() + ": " + t.getMessage());
        }
    }

    /**
     * Called when a player leaves / completes an active parkour session.
     */
    public void onPlayerLeaveSession(@NotNull Player player) {
        if (!available) return;
        try {
            TabPlayer tabPlayer = TabAPI.getInstance().getPlayer(player.getUniqueId());
            if (tabPlayer == null) return;

            NameTagManager manager = TabAPI.getInstance().getNameTagManager();
            if (manager == null) return;

            // 1. Reset collision rule to fallback
            if (plugin.getConfig().getBoolean("tab.disable-collision", true)) {
                manager.setCollisionRule(tabPlayer, null);
            }

            // 2. Restore nametag visibility
            if (plugin.getConfig().getBoolean("tab.hide-nametag", false)) {
                manager.showNameTag(tabPlayer);
            }

            // 3. Resume TAB team handling
            if (pausedPlayers.remove(player.getUniqueId())) {
                manager.resumeTeamHandling(tabPlayer);
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[BasicParkour] Error resetting TAB state for " + player.getName() + ": " + t.getMessage());
        }
    }

    public void cleanup() {
        if (!available) return;
        try {
            NameTagManager manager = TabAPI.getInstance().getNameTagManager();
            if (manager != null) {
                for (UUID uuid : pausedPlayers) {
                    TabPlayer tabPlayer = TabAPI.getInstance().getPlayer(uuid);
                    if (tabPlayer != null) {
                        manager.setCollisionRule(tabPlayer, null);
                        manager.showNameTag(tabPlayer);
                        manager.resumeTeamHandling(tabPlayer);
                    }
                }
            }
        } catch (Throwable ignored) {}
        pausedPlayers.clear();
        available = false;
    }
}
