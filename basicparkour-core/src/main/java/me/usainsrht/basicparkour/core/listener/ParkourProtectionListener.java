package me.usainsrht.basicparkour.core.listener;

import me.usainsrht.basicparkour.api.course.ParkourCourse;
import me.usainsrht.basicparkour.api.session.ParkourSession;
import me.usainsrht.basicparkour.core.BasicParkourPlugin;
import me.usainsrht.basicparkour.core.session.SessionManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Provides comprehensive protections for players participating in parkour sessions.
 *
 * <p>Crucial for SMP, Skyblock, and Faction servers to prevent players from taking
 * lethal fall damage or other damage, starving, griefing/building on the course, or losing items.</p>
 */
public final class ParkourProtectionListener implements Listener {

    private final BasicParkourPlugin plugin;
    private final SessionManager sessionManager;

    public ParkourProtectionListener(@NotNull BasicParkourPlugin plugin, @NotNull SessionManager sessionManager) {
        this.plugin = plugin;
        this.sessionManager = sessionManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(@NotNull EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        var sessionOpt = sessionManager.getSession(player);
        if (sessionOpt.isEmpty()) {
            // Protect player while internal teleportation (respawn/fail) is in progress
            if (sessionManager.isInternalTeleport(player.getUniqueId())) {
                event.setCancelled(true);
                event.setDamage(0.0);
            }
            return;
        }

        ParkourSession session = sessionOpt.get();
        ParkourCourse course = session.getCourse();

        boolean preventDamage = course.isPreventDamage()
                .orElseGet(() -> plugin.getConfig().getBoolean("protections.prevent-damage", true));

        boolean fallDamageFails = course.isFallDamageFailsSession()
                .orElseGet(() -> plugin.getConfig().getBoolean("protections.fall-damage-fails-session", true));

        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            boolean preventFallDamage = course.isPreventDamage()
                    .orElseGet(() -> plugin.getConfig().getBoolean("protections.prevent-fall-damage", preventDamage));

            if (fallDamageFails) {
                sessionManager.failSession(player, ParkourSession.FailReason.VOID);
            }

            if (preventDamage || preventFallDamage) {
                event.setCancelled(true);
                event.setDamage(0.0);
                player.setFallDistance(0f);
            }
        } else {
            if (preventDamage) {
                event.setCancelled(true);
                event.setDamage(0.0);
                if (event.getCause() == EntityDamageEvent.DamageCause.FIRE
                        || event.getCause() == EntityDamageEvent.DamageCause.FIRE_TICK
                        || event.getCause() == EntityDamageEvent.DamageCause.LAVA) {
                    player.setFireTicks(0);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityCombust(@NotNull EntityCombustEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        var sessionOpt = sessionManager.getSession(player);
        if (sessionOpt.isPresent()) {
            boolean preventDamage = sessionOpt.get().getCourse().isPreventDamage()
                    .orElseGet(() -> plugin.getConfig().getBoolean("protections.prevent-damage", true));
            if (preventDamage) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(@NotNull PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (sessionManager.getSession(player).isPresent()) {
            sessionManager.failSession(player, ParkourSession.FailReason.VOID);
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
