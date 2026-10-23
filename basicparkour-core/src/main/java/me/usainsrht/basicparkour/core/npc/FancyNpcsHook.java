package me.usainsrht.basicparkour.core.npc;

import de.oliver.fancynpcs.api.FancyNpcsPlugin;
import de.oliver.fancynpcs.api.Npc;
import de.oliver.fancynpcs.api.events.NpcInteractEvent;
import me.usainsrht.basicparkour.core.BasicParkourPlugin;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

/**
 * Handles FancyNPCs integration and interaction listeners.
 */
public final class FancyNpcsHook implements Listener {

    private final BasicParkourPlugin plugin;
    private boolean available = false;

    public FancyNpcsHook(@NotNull BasicParkourPlugin plugin) {
        this.plugin = plugin;
        init();
    }

    public void init() {
        if (Bukkit.getPluginManager().isPluginEnabled("FancyNpcs")) {
            try {
                if (FancyNpcsPlugin.get() != null) {
                    available = true;
                    Bukkit.getPluginManager().registerEvents(this, plugin);
                    plugin.getLogger().info("[BasicParkour] Hooked into FancyNPCs successfully.");
                }
            } catch (Throwable t) {
                available = false;
                plugin.getLogger().warning("[BasicParkour] Failed to hook into FancyNPCs: " + t.getMessage());
            }
        } else {
            available = false;
        }
    }

    public boolean isAvailable() {
        return available;
    }

    @EventHandler
    public void onNpcInteract(NpcInteractEvent event) {
        if (!available) return;
        NpcManager manager = plugin.getNpcManager();
        if (manager == null) return;

        String npcName = event.getNpc().getData().getName();
        ParkourNpcData data = manager.getNpc(NpcPlatform.FANCYNPCS, npcName);
        if (data != null) {
            manager.executeAction(event.getPlayer(), data.getAction(), data.getCourseId());
        }
    }

    public void updateTopHolder(@NotNull String npcName, @NotNull String playerName) {
        if (!available) return;

        plugin.getScheduler().runGlobal(() -> {
            try {
                Npc npc = FancyNpcsPlugin.get().getNpcManager().getNpc(npcName);
                if (npc == null) return;

                npc.getData().setSkin(playerName);
                npc.getData().setDisplayName("<gold>#1 <yellow>" + playerName + "</yellow></gold>");
                npc.updateForAll();
            } catch (Throwable t) {
                plugin.getLogger().warning("[BasicParkour] Failed to update FancyNPC top holder '" + npcName + "': " + t.getMessage());
            }
        });
    }
}
