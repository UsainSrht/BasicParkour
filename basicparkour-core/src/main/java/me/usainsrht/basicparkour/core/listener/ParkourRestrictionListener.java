package me.usainsrht.basicparkour.core.listener;

import io.papermc.paper.event.entity.EntityKnockbackEvent;
import io.papermc.paper.event.entity.EntityLungeEvent;
import me.usainsrht.basicparkour.api.course.ParkourCourse;
import me.usainsrht.basicparkour.api.event.CourseLeaveEvent;
import me.usainsrht.basicparkour.api.session.ParkourSession;
import me.usainsrht.basicparkour.core.BasicParkourPlugin;
import me.usainsrht.basicparkour.core.session.SessionManager;
import net.kyori.adventure.audience.Audience;
import org.bukkit.Material;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRiptideEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Enforces anti-cheat restrictions and protections during parkour sessions (disabling flight,
 * Elytra gliding, ender pearls &amp; projectile launches, knockback, unauthorized commands,
 * and cleanly ending sessions when external teleports occur).
 */
public final class ParkourRestrictionListener implements Listener {

    private final BasicParkourPlugin plugin;
    private final SessionManager sessionManager;

    public ParkourRestrictionListener(@NotNull BasicParkourPlugin plugin, @NotNull SessionManager sessionManager) {
        this.plugin = plugin;
        this.sessionManager = sessionManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onToggleFlight(@NotNull PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (sessionManager.getSession(player).isPresent() &&
            plugin.getConfig().getBoolean("restrictions.disable-flight", true)) {
            event.setCancelled(true);
            player.setAllowFlight(false);
            player.setFlying(false);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onToggleGlide(@NotNull EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        Optional<ParkourSession> sessionOpt = sessionManager.getSession(player);
        if (sessionOpt.isEmpty()) return;

        ParkourCourse course = sessionOpt.get().getCourse();
        boolean disableElytra = course.isPreventElytra().orElseGet(() ->
            plugin.getConfig().getBoolean("restrictions.disable-elytra", true));

        if (disableElytra) {
            event.setCancelled(true);
            player.setGliding(false);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onKnockback(@NotNull EntityKnockbackEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        Optional<ParkourSession> sessionOpt = sessionManager.getSession(player);
        if (sessionOpt.isEmpty()) return;

        ParkourCourse course = sessionOpt.get().getCourse();
        boolean preventKnockback = course.isPreventKnockback().orElseGet(() ->
            plugin.getConfig().getBoolean("protections.prevent-knockback",
                plugin.getConfig().getBoolean("restrictions.cancel-knockback", true)));

        if (preventKnockback) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectileLaunch(@NotNull ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player player)) return;
        Optional<ParkourSession> sessionOpt = sessionManager.getSession(player);
        if (sessionOpt.isEmpty()) return;

        ParkourCourse course = sessionOpt.get().getCourse();
        boolean preventProjectiles = course.isPreventProjectileLaunch().orElseGet(() ->
            plugin.getConfig().getBoolean("restrictions.disable-projectile-launch",
                plugin.getConfig().getBoolean("restrictions.disable-ender-pearls", true)));

        if (preventProjectiles) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(@NotNull PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Optional<ParkourSession> sessionOpt = sessionManager.getSession(player);
        if (sessionOpt.isEmpty()) return;

        ParkourCourse course = sessionOpt.get().getCourse();
        boolean disableProjectiles = course.isPreventProjectileLaunch().orElseGet(() ->
            plugin.getConfig().getBoolean("restrictions.disable-projectile-launch",
                plugin.getConfig().getBoolean("restrictions.disable-ender-pearls", true)));

        if (disableProjectiles && event.getItem() != null) {
            Material type = event.getItem().getType();
            if (type == Material.ENDER_PEARL || type == Material.CHORUS_FRUIT) {
                event.setCancelled(true);
            }
        }
    }

    /** Trident Riptide launches are cancelled during parkour ({@code restrictions.disable-riptide}). */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRiptide(@NotNull PlayerRiptideEvent event) {
        if (!plugin.getConfig().getBoolean("restrictions.disable-riptide", true)) return;
        if (sessionManager.getSession(event.getPlayer()).isEmpty()) return;
        event.setCancelled(true);
    }

    /**
     * Potion effects are banned during parkour: new effects (potions, beacons, conduits, food, commands...) are
     * refused unless whitelisted. Removing effects is always allowed, and so is the plugin's own invisibility.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPotionEffect(@NotNull EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (sessionManager.getSession(player).isEmpty()) return;
        if (!sessionManager.isPotionBanActive()) return;

        EntityPotionEffectEvent.Action action = event.getAction();
        if (action != EntityPotionEffectEvent.Action.ADDED && action != EntityPotionEffectEvent.Action.CHANGED) return;
        if (sessionManager.isApplyingOwnEffect(player.getUniqueId())) return;

        PotionEffect incoming = event.getNewEffect();
        if (incoming == null || sessionManager.isPotionEffectWhitelisted(incoming.getType())) return;
        event.setCancelled(true);
    }

    /** Spear Lunge boosts are cancelled during parkour ({@code restrictions.disable-lunge}). */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLunge(@NotNull EntityLungeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (sessionManager.getSession(player).isEmpty()) return;
        if (!plugin.getConfig().getBoolean("restrictions.disable-lunge", true)) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommandPreprocess(@NotNull PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (sessionManager.getSession(player).isEmpty()) return;

        String mode = plugin.getConfig().getString("restrictions.commands.mode", "WHITELIST").toUpperCase(Locale.ROOT);
        if ("DISABLED".equals(mode)) return;

        String raw = event.getMessage().startsWith("/") ? event.getMessage().substring(1) : event.getMessage();
        String cmdName = raw.split("\\s+")[0].toLowerCase(Locale.ROOT);
        if (cmdName.contains(":")) {
            cmdName = cmdName.substring(cmdName.indexOf(':') + 1);
        }

        if ("WHITELIST".equals(mode)) {
            List<String> whitelist = plugin.getConfig().getStringList("restrictions.commands.whitelist");
            boolean allowed = whitelist.stream().anyMatch(cmdName::equalsIgnoreCase);
            if (!allowed) {
                event.setCancelled(true);
                plugin.getMessages().getCommandBlocked().send((Audience) player);
            }
        } else if ("BLACKLIST".equals(mode)) {
            List<String> blacklist = plugin.getConfig().getStringList("restrictions.commands.blacklist");
            boolean blocked = blacklist.stream().anyMatch(cmdName::equalsIgnoreCase);
            if (blocked) {
                event.setCancelled(true);
                plugin.getMessages().getCommandBlocked().send((Audience) player);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(@NotNull PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        Optional<ParkourSession> sessionOpt = sessionManager.getSession(player);
        if (sessionOpt.isEmpty()) return;

        // If this was an internal teleport executed by BasicParkour, permit it
        if (sessionManager.isInternalTeleport(player.getUniqueId())) return;

        ParkourCourse course = sessionOpt.get().getCourse();
        boolean teleportLeaves = course.isTeleportLeavesSession().orElseGet(() ->
            plugin.getConfig().getBoolean("session.teleport-leaves-session",
                plugin.getConfig().getBoolean("session.cancel-on-external-teleport", true)));

        if (teleportLeaves) {
            // Player warped away via /home, /spawn, /tp, death respawn, portal, eating chorus fruit, etc.
            sessionManager.leaveSession(player, false, CourseLeaveEvent.LeaveReason.TELEPORT);
        }
    }
}
