package me.usainsrht.basicparkour.core.debug;

import me.usainsrht.basicparkour.api.course.Checkpoint;
import me.usainsrht.basicparkour.api.course.CourseRegion;
import me.usainsrht.basicparkour.api.course.ParkourCourse;
import me.usainsrht.basicparkour.api.session.ParkourSession;
import me.usainsrht.basicparkour.core.BasicParkourPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages in-game and console debug diagnostics for BasicParkour.
 *
 * <p>Provides live action-bar HUD feedback and comprehensive chat/console diagnostics
 * for physics, block collisions, whitelist/blacklist checks, regions, and fail events.</p>
 */
public final class DebugManager {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final BasicParkourPlugin plugin;
    private final Set<UUID> debugPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lastActionBarUpdate = new ConcurrentHashMap<>();
    private volatile boolean consoleDebug = false;

    public DebugManager(@NotNull BasicParkourPlugin plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Debug toggle & status
    // -------------------------------------------------------------------------

    public boolean isDebug(@NotNull UUID uuid) {
        return debugPlayers.contains(uuid);
    }

    public boolean isDebug(@Nullable Player player) {
        return player != null && isDebug(player.getUniqueId());
    }

    public boolean toggleDebug(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        if (debugPlayers.contains(uuid)) {
            debugPlayers.remove(uuid);
            lastActionBarUpdate.remove(uuid);
            player.sendMessage(MINI.deserialize("<red>[BasicParkour] Debug mode disabled.</red>"));
            return false;
        } else {
            debugPlayers.add(uuid);
            player.sendMessage(MINI.deserialize(
                "<green>[BasicParkour] Debug mode <bold>ENABLED</bold>!</green>\n"
                + "<gray>• Live action-bar HUD is active during parkour runs.\n"
                + "• Full diagnostic reports will be sent on fail/checkpoint/start events.</gray>"
            ));
            return true;
        }
    }

    public void setDebug(@NotNull UUID uuid, boolean enabled) {
        if (enabled) {
            debugPlayers.add(uuid);
        } else {
            debugPlayers.remove(uuid);
            lastActionBarUpdate.remove(uuid);
        }
    }

    public boolean isConsoleDebug() {
        return consoleDebug;
    }

    public void setConsoleDebug(boolean enabled) {
        this.consoleDebug = enabled;
    }

    public boolean toggleConsoleDebug() {
        this.consoleDebug = !this.consoleDebug;
        return this.consoleDebug;
    }

    public boolean hasAnyDebugSubscriber() {
        return consoleDebug || !debugPlayers.isEmpty();
    }

    // -------------------------------------------------------------------------
    // Broadcast helper
    // -------------------------------------------------------------------------

    private void broadcast(@Nullable Player contextPlayer, @NotNull Component message) {
        if (contextPlayer != null && isDebug(contextPlayer)) {
            contextPlayer.sendMessage(message);
        }
        for (UUID uuid : debugPlayers) {
            if (contextPlayer != null && contextPlayer.getUniqueId().equals(uuid)) continue;
            Player other = Bukkit.getPlayer(uuid);
            if (other != null && other.isOnline()) {
                other.sendMessage(message);
            }
        }
        if (consoleDebug) {
            plugin.getComponentLogger().info(message);
        }
    }

    // -------------------------------------------------------------------------
    // Live Action Bar HUD
    // -------------------------------------------------------------------------

    public void sendLiveActionBar(@NotNull Player player,
                                  @NotNull ParkourSession session,
                                  @NotNull Location loc,
                                  boolean inStart,
                                  boolean inCp,
                                  boolean respawnImmune) {
        if (!isDebug(player)) return;

        long now = System.currentTimeMillis();
        Long last = lastActionBarUpdate.get(player.getUniqueId());
        if (last != null && (now - last) < 100L) {
            return; // Throttle to 10 updates/sec
        }
        lastActionBarUpdate.put(player.getUniqueId(), now);

        Block centerFeet = loc.getBlock();
        Block centerUnder = loc.clone().subtract(0, 0.1, 0).getBlock();
        Material centerMat = !centerFeet.isEmpty() && !centerFeet.isPassable() ? centerFeet.getType() : centerUnder.getType();

        Set<Material> standing = getStandingMaterialsQuick(player, loc);

        String groundColor = player.isOnGround() ? "<green>YES</green>" : "<gray>NO</gray>";
        String startColor = inStart ? "<green>IN</green>" : "<gray>OUT</gray>";
        String cpColor = inCp ? "<green>IN</green>" : "<gray>OUT</gray>";
        String immColor = respawnImmune ? "<gold>IMMUNE</gold>" : "<gray>NO</gray>";

        String text = String.format(
            Locale.ROOT,
            "<gold>[DEBUG]</gold> <yellow>Y:%.2f</yellow> G:%s | Under:<aqua>%s</aqua> | Stand:<white>%s</white> | Start:%s CP:%s Imm:%s",
            loc.getY(),
            groundColor,
            centerMat.name(),
            standing.isEmpty() ? "[]" : standing.toString(),
            startColor,
            cpColor,
            immColor
        );

        player.sendActionBar(MINI.deserialize(text));
    }

    // -------------------------------------------------------------------------
    // Diagnostic events
    // -------------------------------------------------------------------------

    public void onSessionFail(@NotNull Player player,
                              @NotNull ParkourSession session,
                              @NotNull ParkourSession.FailReason reason,
                              @Nullable String detail,
                              @Nullable Location respawnLoc) {
        if (!hasAnyDebugSubscriber()) return;

        ParkourCourse course = session.getCourse();
        Location loc = player.getLocation();

        Block feetBlock = loc.getBlock();
        Block underBlock = loc.clone().subtract(0, 0.1, 0).getBlock();
        Block centerSupporting = !feetBlock.isEmpty() && !feetBlock.isPassable() ? feetBlock : underBlock;

        Set<Material> standing = getStandingMaterialsQuick(player, loc);
        Set<Material> blockList = course.getBlockList();

        boolean inStart = isInStartRegion(course, player, loc);
        boolean inCp = isInCurrentCheckpointRegion(session, player, loc);
        boolean respawnImmune = plugin.getSessionManager().isRespawnImmune(player.getUniqueId());

        StringBuilder sb = new StringBuilder();
        sb.append("<dark_gray>━━━</dark_gray> <red><bold>BasicParkour Debug: Session Failed</bold></red> <dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>\n");
        sb.append(String.format(Locale.ROOT, "<gray>Player: <white>%s</white> | Course: <yellow>%s</yellow> (Mode: <aqua>%s</aqua>)</gray>\n",
            player.getName(), course.getId(), course.getBlockListMode()));
        sb.append(String.format(Locale.ROOT, "<gray>Reason: <red><bold>%s</bold></red> | Detail: <gold>%s</gold></gray>\n",
            reason.name(), detail != null ? detail : "N/A"));
        sb.append(String.format(Locale.ROOT, "<gray>Position: <white>%s, %.3f, %.3f, %.3f</white> (Pitch: %.1f, Yaw: %.1f)</gray>\n",
            loc.getWorld() != null ? loc.getWorld().getName() : "?", loc.getX(), loc.getY(), loc.getZ(), loc.getPitch(), loc.getYaw()));
        sb.append(String.format(Locale.ROOT, "<gray>Status: onGround=<white>%b</white>, fallDistance=<white>%.2f</white>, isFlying=<white>%b</white></gray>\n",
            player.isOnGround(), player.getFallDistance(), player.isFlying()));
        sb.append(String.format(Locale.ROOT, "<gray>Regions: inStart=<white>%b</white>, inCheckpoint=<white>%b</white>, respawnImmune=<white>%b</white></gray>\n",
            inStart, inCp, respawnImmune));
        sb.append(String.format(Locale.ROOT, "<gray>Center Supporting Block: <aqua>%s</aqua> at (%d, %d, %d)</gray>\n",
            centerSupporting.getType(), centerSupporting.getX(), centerSupporting.getY(), centerSupporting.getZ()));
        sb.append(String.format(Locale.ROOT, "<gray>Feet Block: <aqua>%s</aqua> | Under Feet Block: <aqua>%s</aqua></gray>\n",
            feetBlock.getType(), underBlock.getType()));
        sb.append(String.format(Locale.ROOT, "<gray>Sampled Standing Materials: <white>%s</white></gray>\n",
            standing.isEmpty() ? "[EMPTY]" : standing.toString()));
        sb.append(String.format(Locale.ROOT, "<gray>Course Block List (<yellow>%s</yellow>): <light_purple>%s</light_purple></gray>\n",
            course.getBlockListMode(), blockList.isEmpty() ? "[NONE]" : blockList.toString()));
        if (respawnLoc != null) {
            sb.append(String.format(Locale.ROOT, "<gray>Die Mode: <gold>%s</gold> -> Respawn Location: <white>%s, %.2f, %.2f, %.2f</white></gray>\n",
                course.getDieMode(), respawnLoc.getWorld() != null ? respawnLoc.getWorld().getName() : "?",
                respawnLoc.getX(), respawnLoc.getY(), respawnLoc.getZ()));
        }
        sb.append("<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</dark_gray>");

        broadcast(player, MINI.deserialize(sb.toString()));
    }

    public void onSessionStart(@NotNull Player player, @NotNull ParkourCourse course, boolean teleported) {
        if (!hasAnyDebugSubscriber()) return;
        String msg = String.format(Locale.ROOT,
            "<gray>[BasicParkour Debug] <green>Session Started:</green> Player <white>%s</white> on course <yellow>%s</yellow> (teleported=%b)</gray>",
            player.getName(), course.getId(), teleported);
        broadcast(player, MINI.deserialize(msg));
    }

    public void onCheckpointReach(@NotNull Player player, @NotNull Checkpoint checkpoint, @NotNull String formattedTime) {
        if (!hasAnyDebugSubscriber()) return;
        String msg = String.format(Locale.ROOT,
            "<gray>[BasicParkour Debug] <aqua>Checkpoint Reached:</aqua> Player <white>%s</white> reached CP #%d at <yellow>%s</yellow></gray>",
            player.getName(), checkpoint.getIndex() + 1, formattedTime);
        broadcast(player, MINI.deserialize(msg));
    }

    public void onSessionComplete(@NotNull Player player, @NotNull ParkourCourse course, @NotNull String formattedTime) {
        if (!hasAnyDebugSubscriber()) return;
        String msg = String.format(Locale.ROOT,
            "<gray>[BasicParkour Debug] <gold>Course Completed:</gold> Player <white>%s</white> finished <yellow>%s</yellow> in <green>%s</green></gray>",
            player.getName(), course.getId(), formattedTime);
        broadcast(player, MINI.deserialize(msg));
    }

    public void onFallDamage(@NotNull Player player, double damage, float fallDistance, boolean failsSession) {
        if (!hasAnyDebugSubscriber()) return;
        String msg = String.format(Locale.ROOT,
            "<gray>[BasicParkour Debug] <red>Fall Damage:</red> Player <white>%s</white> took <gold>%.2f</gold> dmg (fallDist=%.2f, failsSession=%b)</gray>",
            player.getName(), damage, fallDistance, failsSession);
        broadcast(player, MINI.deserialize(msg));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private boolean isInStartRegion(@NotNull ParkourCourse course, @NotNull Player player, @NotNull Location loc) {
        CourseRegion start = course.getStartRegion();
        if (loc.getWorld() == null || !course.getWorldName().equals(loc.getWorld().getName())) return false;
        if (start.overlaps(player.getBoundingBox()) || start.contains(loc)) return true;
        double x = loc.getX();
        double y = loc.getY();
        double z = loc.getZ();
        return x >= (start.getMin().getX() - 0.3) && x <= (start.getMax().getX() + 0.3)
            && z >= (start.getMin().getZ() - 0.3) && z <= (start.getMax().getZ() + 0.3)
            && y >= (start.getMin().getY() - 0.5) && y <= (start.getMax().getY() + 2.5);
    }

    private boolean isInCurrentCheckpointRegion(@NotNull ParkourSession session, @NotNull Player player, @NotNull Location loc) {
        return session.getLastCheckpoint().map(cp -> {
            CourseRegion region = cp.getTriggerRegion();
            if (loc.getWorld() == null || !region.getMin().getWorld().equals(loc.getWorld())) return false;
            if (region.overlaps(player.getBoundingBox()) || region.contains(loc)) return true;
            double x = loc.getX();
            double y = loc.getY();
            double z = loc.getZ();
            return x >= (region.getMin().getX() - 0.3) && x <= (region.getMax().getX() + 0.3)
                && z >= (region.getMin().getZ() - 0.3) && z <= (region.getMax().getZ() + 0.3)
                && y >= (region.getMin().getY() - 0.5) && y <= (region.getMax().getY() + 2.5);
        }).orElse(false);
    }

    @NotNull
    private Set<Material> getStandingMaterialsQuick(@NotNull Player player, @NotNull Location to) {
        Set<Material> mats = new HashSet<>();
        org.bukkit.util.BoundingBox box = player.getBoundingBox();

        double minX = box.getMinX() + 0.05;
        double maxX = box.getMaxX() - 0.05;
        double minZ = box.getMinZ() + 0.05;
        double maxZ = box.getMaxZ() - 0.05;
        double footY = to.getY();

        double[] sampleX = (minX < maxX) ? new double[]{minX, (minX + maxX) / 2.0, maxX} : new double[]{to.getX()};
        double[] sampleZ = (minZ < maxZ) ? new double[]{minZ, (minZ + maxZ) / 2.0, maxZ} : new double[]{to.getZ()};

        org.bukkit.World world = to.getWorld();
        if (world == null) return mats;

        for (double sx : sampleX) {
            for (double sz : sampleZ) {
                Location sampleLoc = new Location(world, sx, footY, sz);
                Block atFeet = sampleLoc.getBlock();
                if (!atFeet.isEmpty() && !atFeet.isPassable()) {
                    mats.add(atFeet.getType());
                } else {
                    Block underFeet = sampleLoc.clone().subtract(0, 0.1, 0).getBlock();
                    if (!underFeet.isEmpty()) {
                        mats.add(underFeet.getType());
                    }
                }
            }
        }
        return mats;
    }
}
