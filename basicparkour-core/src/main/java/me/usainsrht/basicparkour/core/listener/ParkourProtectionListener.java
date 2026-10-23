package me.usainsrht.basicparkour.core.listener;

import me.usainsrht.basicparkour.api.session.ParkourSession;
import me.usainsrht.basicparkour.core.BasicParkourPlugin;
import me.usainsrht.basicparkour.core.session.SessionManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Provides comprehensive protections for players participating in parkour sessions.
 *
 * <p>Crucial for SMP, Skyblock, and Faction servers to prevent players from taking
 * lethal fall damage, starving, griefing/building on the course, or losing items.</p>
 */
public final class ParkourProtectionListener implements Listener {

    private final BasicParkourPlugin plugin;
    private final SessionManager sessionManager;

    public ParkourProtectionListener(@NotNull BasicParkourPlugin plugin, @NotNull SessionManager sessionManager) {
        this.plugin = plugin;
        this.sessionManager = sessionManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(@NotNull EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (sessionManager.getSession(player).isEmpty()) return;

        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            if (plugin.getConfig().getBoolean("protections.prevent-fall-damage", true)) {
                event.setCancelled(true);
                if (plugin.getConfig().getBoolean("protections.fall-damage-fails-session", true)) {
                    sessionManager.failSession(player, ParkourSession.FailReason.VOID);
                }
            }
        } else {
            if (plugin.getConfig().getBoolean("protections.prevent-damage", true)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPvPDamage(@NotNull EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && sessionManager.getSession(player).isPresent()) {
            if (plugin.getConfig().getBoolean("protections.prevent-pvp", true)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFoodLevelChange(@NotNull FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (sessionManager.getSession(player).isPresent()) {
            if (plugin.getConfig().getBoolean("protections.prevent-hunger", true)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(@NotNull BlockBreakEvent event) {
        if (sessionManager.getSession(event.getPlayer()).isPresent()) {
            if (plugin.getConfig().getBoolean("protections.prevent-block-break", true)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(@NotNull BlockPlaceEvent event) {
        if (sessionManager.getSession(event.getPlayer()).isPresent()) {
            if (plugin.getConfig().getBoolean("protections.prevent-block-place", true)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEmpty(@NotNull PlayerBucketEmptyEvent event) {
        if (sessionManager.getSession(event.getPlayer()).isPresent()) {
            if (plugin.getConfig().getBoolean("protections.prevent-block-place", true)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketFill(@NotNull PlayerBucketFillEvent event) {
        if (sessionManager.getSession(event.getPlayer()).isPresent()) {
            if (plugin.getConfig().getBoolean("protections.prevent-block-break", true)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemDrop(@NotNull PlayerDropItemEvent event) {
        if (sessionManager.getSession(event.getPlayer()).isPresent()) {
            if (plugin.getConfig().getBoolean("protections.prevent-item-drop", true)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemPickup(@NotNull EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && sessionManager.getSession(player).isPresent()) {
            if (plugin.getConfig().getBoolean("protections.prevent-item-pickup", true)) {
                event.setCancelled(true);
            }
        }
    }
}
