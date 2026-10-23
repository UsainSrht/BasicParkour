package me.usainsrht.basicparkour.core.editor;

import me.usainsrht.basicparkour.core.BasicParkourPlugin;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.jetbrains.annotations.NotNull;

/**
 * Listens for wand selections to set position 1 and position 2 for course creation.
 */
public final class WandListener implements Listener {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final Sound SOUND_POS1 = Sound.sound(
            Key.key("minecraft:block.note_block.pling"), Sound.Source.PLAYER, 1.0f, 1.5f);
    private static final Sound SOUND_POS2 = Sound.sound(
            Key.key("minecraft:block.note_block.pling"), Sound.Source.PLAYER, 1.0f, 2.0f);

    private final BasicParkourPlugin plugin;
    private final EditorManager editorManager;

    public WandListener(@NotNull BasicParkourPlugin plugin, @NotNull EditorManager editorManager) {
        this.plugin = plugin;
        this.editorManager = editorManager;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();

        if (!editorManager.isWandItem(event.getItem())) return;

        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        event.setCancelled(true);
        Location loc = clicked.getLocation();

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            editorManager.setPos1(player, loc);
            player.playSound(SOUND_POS1);
            player.sendMessage(MINI.deserialize("<green>Position 1 set to <white>(%d, %d, %d)</white> in <aqua>%s</aqua>.</green>"
                    .formatted(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), loc.getWorld().getName())));
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            editorManager.setPos2(player, loc);
            player.playSound(SOUND_POS2);
            player.sendMessage(MINI.deserialize("<green>Position 2 set to <white>(%d, %d, %d)</white> in <aqua>%s</aqua>.</green>"
                    .formatted(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), loc.getWorld().getName())));
        }
    }
}
