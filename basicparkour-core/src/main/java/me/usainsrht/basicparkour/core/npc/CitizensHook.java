package me.usainsrht.basicparkour.core.npc;

import me.usainsrht.basicparkour.core.BasicParkourPlugin;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.TraitInfo;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;

/**
 * Handles Citizens integration and dynamic leaderboard NPC updates.
 */
public final class CitizensHook {

    private final BasicParkourPlugin plugin;
    private boolean available = false;

    public CitizensHook(@NotNull BasicParkourPlugin plugin) {
        this.plugin = plugin;
        init();
    }

    public void init() {
        if (Bukkit.getPluginManager().isPluginEnabled("Citizens")) {
            try {
                CitizensAPI.getTraitFactory().registerTrait(
                        TraitInfo.create(CitizensParkourTrait.class).withName("basicparkour")
                );
                available = true;
                plugin.getLogger().info("[BasicParkour] Hooked into Citizens and registered trait 'basicparkour'.");
            } catch (Throwable t) {
                available = false;
                plugin.getLogger().warning("[BasicParkour] Failed to register Citizens trait: " + t.getMessage());
            }
        } else {
            available = false;
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public void updateTopHolder(int npcId, @NotNull String playerName) {
        if (!available) return;

        plugin.getScheduler().runGlobal(() -> {
            try {
                NPC npc = CitizensAPI.getNPCRegistry().getById(npcId);
                if (npc == null) return;

                npc.setName(playerName);
                SkinTrait skinTrait = npc.getOrAddTrait(SkinTrait.class);
                skinTrait.setSkinName(playerName, true);
            } catch (Throwable t) {
                plugin.getLogger().warning("[BasicParkour] Failed to update Citizens top holder NPC #" + npcId + ": " + t.getMessage());
            }
        });
    }

    public void linkNpc(int npcId, @NotNull NpcAction action, @NotNull String courseId) {
        if (!available) return;
        NPC npc = CitizensAPI.getNPCRegistry().getById(npcId);
        if (npc == null) return;

        CitizensParkourTrait trait = npc.getOrAddTrait(CitizensParkourTrait.class);
        trait.setAction(action.name());
        trait.setCourseId(courseId);
    }
}
