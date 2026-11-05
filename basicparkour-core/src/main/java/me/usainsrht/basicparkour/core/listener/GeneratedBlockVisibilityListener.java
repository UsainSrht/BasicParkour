package me.usainsrht.basicparkour.core.listener;

import io.papermc.paper.event.packet.PlayerChunkLoadEvent;
import me.usainsrht.basicparkour.core.generator.GeneratorManagerImpl;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

/**
 * Generated parkour blocks are real server blocks that are hidden per player with block-change packets. A chunk
 * (re)sent to a runner (walking into new chunks, teleports, respawns) carries those blocks again, so the other
 * runs' blocks in it have to be hidden once more.
 */
public final class GeneratedBlockVisibilityListener implements Listener {

    private final GeneratorManagerImpl generatorManager;

    public GeneratedBlockVisibilityListener(@NotNull GeneratorManagerImpl generatorManager) {
        this.generatorManager = generatorManager;
    }

    @EventHandler
    public void onChunkLoad(@NotNull PlayerChunkLoadEvent event) {
        generatorManager.hideOtherRunsInChunk(event.getPlayer(), event.getChunk().getX(), event.getChunk().getZ());
    }
}
