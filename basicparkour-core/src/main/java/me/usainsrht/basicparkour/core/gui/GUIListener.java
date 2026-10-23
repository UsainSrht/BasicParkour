package me.usainsrht.basicparkour.core.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;

/**
 * Event listener guaranteeing zero item duplication or loss across all {@link BasicParkourGUI} views.
 */
public final class GUIListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory top = event.getView().getTopInventory();
        if (top == null || !(top.getHolder() instanceof BasicParkourGUI gui)) {
            return;
        }

        // Cancel all clicks whenever a BasicParkourGUI is the active top inventory.
        // This completely prevents shift-click injections, number-key hotbar swaps,
        // double-click collections, and cursor item loss.
        event.setCancelled(true);

        int rawSlot = event.getRawSlot();
        if (rawSlot >= 0 && rawSlot < top.getSize()) {
            gui.handleClick(player, event.getSlot(), event.getClick());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top != null && top.getHolder() instanceof BasicParkourGUI) {
            // Cancel all drags to prevent item splitting or ghost items in the GUI
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof BasicParkourGUI gui
                && event.getPlayer() instanceof Player player) {
            gui.handleClose(player);
        }
    }
}
