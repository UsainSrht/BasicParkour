package me.usainsrht.basicparkour.core.gui;

import me.usainsrht.basicparkour.core.BasicParkourPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Base abstract class for all BasicParkour GUIs.
 * Implements {@link InventoryHolder} to uniquely identify menus without external libraries.
 */
public abstract class BasicParkourGUI implements InventoryHolder {

    protected final BasicParkourPlugin plugin;
    protected final Player player;
    protected Inventory inventory;

    public BasicParkourGUI(@NotNull BasicParkourPlugin plugin, @NotNull Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    /**
     * Builds and opens the inventory for the player.
     */
    public abstract void open();

    /**
     * Handles slot clicks inside the top inventory.
     * Clicks are already cancelled by {@link GUIListener}.
     *
     * @param player    the clicking player
     * @param slot      the slot index clicked
     * @param clickType the type of click performed
     */
    public abstract void handleClick(@NotNull Player player, int slot, @NotNull ClickType clickType);

    /**
     * Optional callback when the GUI inventory is closed.
     *
     * @param player the player who closed the GUI
     */
    public void handleClose(@NotNull Player player) {}

    @Override
    @NotNull
    public Inventory getInventory() {
        return inventory;
    }
}
