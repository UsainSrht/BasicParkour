package me.usainsrht.basicparkour.core.hotbar;

import me.usainsrht.basicparkour.api.event.CourseLeaveEvent;
import me.usainsrht.basicparkour.api.event.CourseRestartEvent;
import me.usainsrht.basicparkour.core.BasicParkourPlugin;
import me.usainsrht.basicparkour.core.session.SessionManager;
import me.usainsrht.itemapi.yamlitem.YamlItem;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages parkour hotbar utility items (Restart, Checkpoint, Visibility, Leave).
 *
 * <p>Designed for full parkour server setups where players receive utility items
 * in their hotbar during active parkour runs. If {@code hotbar-items.save-inventory} is
 * enabled, existing player inventories are safely stashed and restored upon exit.</p>
 */
public final class HotbarManager implements Listener {

    public enum HotbarAction {
        RESTART,
        CHECKPOINT,
        VISIBILITY,
        LEAVE,
        NONE
    }

    public record HotbarItemDef(int slot, HotbarAction action, ItemStack item) {}

    public record SavedInventory(ItemStack[] contents, ItemStack[] armor, ItemStack offhand) {}

    private final BasicParkourPlugin plugin;
    private final SessionManager sessionManager;

    private boolean enabled;
    private boolean saveInventory;

    private final Map<HotbarAction, HotbarItemDef> itemDefs = new ConcurrentHashMap<>();
    private final Map<UUID, SavedInventory> savedInventories = new ConcurrentHashMap<>();

    public HotbarManager(@NotNull BasicParkourPlugin plugin, @NotNull SessionManager sessionManager) {
        this.plugin = plugin;
        this.sessionManager = sessionManager;
        load();
    }

    public void load() {
        itemDefs.clear();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("hotbar-items");
        if (sec == null) {
            enabled = false;
            saveInventory = true;
            return;
        }

        enabled = sec.getBoolean("enabled", false);
        saveInventory = sec.getBoolean("save-inventory", true);

        ConfigurationSection itemsSec = sec.getConfigurationSection("items");
        if (itemsSec != null) {
            loadItem(itemsSec, "restart", HotbarAction.RESTART, 0, Material.REPEATER);
            loadItem(itemsSec, "checkpoint", HotbarAction.CHECKPOINT, 1, Material.ARROW);
            loadItem(itemsSec, "visibility", HotbarAction.VISIBILITY, 7, Material.ENDER_EYE);
            loadItem(itemsSec, "leave", HotbarAction.LEAVE, 8, Material.RED_BED);
        }
    }

    private void loadItem(ConfigurationSection parent, String key, HotbarAction action, int defaultSlot, Material defaultMat) {
        ConfigurationSection sec = parent.getConfigurationSection(key);
        if (sec == null) return;

        int slot = sec.getInt("slot", defaultSlot);
        ItemStack item;
        try {
            item = YamlItem.parse(sec);
        } catch (Exception e) {
            item = new ItemStack(defaultMat);
        }

        if (item != null && !item.getType().isAir()) {
            itemDefs.put(action, new HotbarItemDef(slot, action, item));
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Called when a player starts a parkour session.
     */
    public void onSessionStart(@NotNull Player player) {
        if (!enabled) return;

        if (saveInventory) {
            savedInventories.put(player.getUniqueId(), new SavedInventory(
                player.getInventory().getContents().clone(),
                player.getInventory().getArmorContents().clone(),
                player.getInventory().getItemInOffHand() != null ? player.getInventory().getItemInOffHand().clone() : null
            ));
        }

        player.getInventory().clear();
        giveHotbarItems(player);
    }

    /**
     * Called when a player completes, fails (with exit), or leaves a parkour session.
     */
    public void onSessionEnd(@NotNull Player player) {
        if (!enabled) return;

        player.getInventory().clear();

        SavedInventory saved = savedInventories.remove(player.getUniqueId());
        if (saved != null) {
            if (saved.contents() != null) {
                player.getInventory().setContents(saved.contents());
            }
            if (saved.armor() != null) {
                player.getInventory().setArmorContents(saved.armor());
            }
            if (saved.offhand() != null) {
                player.getInventory().setItemInOffHand(saved.offhand());
            }
        }
    }

    /**
     * Restores all cached inventories (e.g. on server disable / reload).
     */
    public void restoreAll() {
        for (Map.Entry<UUID, SavedInventory> entry : savedInventories.entrySet()) {
            Player p = org.bukkit.Bukkit.getPlayer(entry.getKey());
            if (p != null && p.isOnline()) {
                p.getInventory().clear();
                SavedInventory saved = entry.getValue();
                if (saved.contents() != null) p.getInventory().setContents(saved.contents());
                if (saved.armor() != null) p.getInventory().setArmorContents(saved.armor());
                if (saved.offhand() != null) p.getInventory().setItemInOffHand(saved.offhand());
            }
        }
        savedInventories.clear();
    }

    private void giveHotbarItems(@NotNull Player player) {
        for (HotbarItemDef def : itemDefs.values()) {
            if (def.slot() >= 0 && def.slot() < 9) {
                player.getInventory().setItem(def.slot(), def.item().clone());
            }
        }
    }

    // ── Interaction Listeners ────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (!enabled) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        if (sessionManager.getSession(player).isEmpty()) return;

        ItemStack inHand = event.getItem();
        if (inHand == null || inHand.getType().isAir()) return;

        HotbarAction action = getActionForItem(inHand);
        if (action == HotbarAction.NONE) return;

        event.setCancelled(true);

        switch (action) {
            case RESTART -> sessionManager.restartSession(player, CourseRestartEvent.RestartReason.HOTBAR);
            case CHECKPOINT -> sessionManager.teleportToCheckpoint(player);
            case VISIBILITY -> sessionManager.cycleVisibility(player);
            case LEAVE -> sessionManager.leaveSession(player, true, CourseLeaveEvent.LeaveReason.HOTBAR);
            case NONE -> {}
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!enabled) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (sessionManager.getSession(player).isEmpty()) return;

        // Prevent moving or tampering with parkour utility items
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemDrop(PlayerDropItemEvent event) {
        if (!enabled) return;
        Player player = event.getPlayer();
        if (sessionManager.getSession(player).isEmpty()) return;

        // Prevent dropping hotbar items
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (!enabled) return;
        Player player = event.getPlayer();
        if (sessionManager.getSession(player).isEmpty()) return;

        event.setCancelled(true);
    }

    private HotbarAction getActionForItem(@NotNull ItemStack stack) {
        for (HotbarItemDef def : itemDefs.values()) {
            if (def.item().isSimilar(stack)) {
                return def.action();
            }
        }
        return HotbarAction.NONE;
    }
}
