package me.usainsrht.basicparkour.core.listener;

import me.usainsrht.basicparkour.api.course.ParkourCourse;
import me.usainsrht.basicparkour.api.modifier.BlockModifier;
import me.usainsrht.basicparkour.api.session.ParkourSession;
import me.usainsrht.basicparkour.api.session.SessionState;
import me.usainsrht.basicparkour.core.BasicParkourPlugin;
import me.usainsrht.basicparkour.core.session.SessionManager;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Core movement listener that drives the parkour game loop.
 *
 * <p>On every {@link PlayerMoveEvent} for a player with an active session, this listener:</p>
 * <ol>
 *   <li>Checks if the player entered the course's end region → complete.</li>
 *   <li>Checks if the player entered the next checkpoint's trigger region → advance.</li>
 *   <li>Checks if the block below (or at) the player's feet matches a block modifier → apply.</li>
 *   <li>Checks if the player is below the void threshold → fail.</li>
 * </ol>
 *
 * <p>For players without an active session:</p>
 * <ul>
 *   <li>Checks if the player entered any course's start region → start session.</li>
 * </ul>
 *
 * <h2>Performance Note</h2>
 * {@code PlayerMoveEvent} fires very frequently. We skip processing when the player has
 * not moved to a new block ({@code from.toBlockLocation().equals(to.toBlockLocation())})
 * to reduce redundant checks. Modifier checks use direct {@link Block#getType()} lookup
 * which is O(1) and does not trigger chunk loads.
 */
public final class PlayerMoveListener implements Listener {

    private final BasicParkourPlugin plugin;
    private final SessionManager sessionManager;

    public PlayerMoveListener(@NotNull BasicParkourPlugin plugin, @NotNull SessionManager sessionManager) {
        this.plugin = plugin;
        this.sessionManager = sessionManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(@NotNull PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();

        // Skip if position hasn't changed (e.g. only camera rotation)
        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) return;

        Optional<ParkourSession> sessionOpt = sessionManager.getSession(player);

        if (sessionOpt.isEmpty()) {
            // Check start regions
            handleStartRegion(player, to);
            return;
        }

        ParkourSession session = sessionOpt.get();
        if (session.getState() != SessionState.ACTIVE) return;

        ParkourCourse course = session.getCourse();

        // ── 0. Anti-flight check ─────────────────────────────────────────────
        if (plugin.getConfig().getBoolean("restrictions.disable-flight", true) && player.isFlying()) {
            player.setAllowFlight(false);
            player.setFlying(false);
            sessionManager.failSession(player, ParkourSession.FailReason.VOID);
            return;
        }

        // ── 0b. Anti-elytra check ────────────────────────────────────────────
        boolean disableElytra = course.isPreventElytra().orElseGet(() ->
            plugin.getConfig().getBoolean("restrictions.disable-elytra", true));
        if (disableElytra && player.isGliding()) {
            player.setGliding(false);
        }

        // ── 1. End region check ──────────────────────────────────────────────
        if (course.getEndRegion().overlaps(player.getBoundingBox()) || course.getEndRegion().contains(to)) {
            sessionManager.completeSession(player);
            return;
        }

        // ── 2. Checkpoint region check (supports skipping in generated courses) ──
        java.util.List<me.usainsrht.basicparkour.api.course.Checkpoint> checkpoints = course.getCheckpoints();
        int currentIdx = session.getCurrentCheckpointIndex();
        boolean isGenerated = course instanceof me.usainsrht.basicparkour.api.generator.GeneratedParkourCourse;

        if (isGenerated) {
            // Check upcoming checkpoints when player successfully steps (lands on the ground) on the platform
            if (player.isOnGround()) {
                for (int i = currentIdx + 1; i < checkpoints.size(); i++) {
                    me.usainsrht.basicparkour.api.course.Checkpoint cp = checkpoints.get(i);
                    if (cp.getTriggerRegion().overlaps(player.getBoundingBox()) || cp.getTriggerRegion().contains(to)) {
                        sessionManager.handleCheckpointReach(player, cp);
                        break;
                    }
                }
            }
        } else {
            session.getNextCheckpoint().ifPresent(checkpoint -> {
                if (checkpoint.getTriggerRegion().overlaps(player.getBoundingBox()) || checkpoint.getTriggerRegion().contains(to)) {
                    sessionManager.handleCheckpointReach(player, checkpoint);
                }
            });
        }

        // ── 3. Void check ────────────────────────────────────────────────────
        double failY = to.getWorld().getMinHeight() - 5;
        if (course.getMinY().isPresent()) {
            failY = course.getMinY().get();
        } else if (isGenerated) {
            var lastCp = session.getLastCheckpoint().orElse(null);
            var nextCp = session.getNextCheckpoint().orElse(null);
            double refY = to.getY();
            if (lastCp != null && nextCp != null) {
                refY = Math.min(lastCp.getSpawnLocation().getY(), nextCp.getSpawnLocation().getY());
            } else if (lastCp != null) {
                refY = lastCp.getSpawnLocation().getY();
            } else if (nextCp != null) {
                refY = nextCp.getSpawnLocation().getY();
            }
            failY = refY - 3.5;
        }
        
        if (to.getY() < failY) {
            sessionManager.failSession(player, ParkourSession.FailReason.VOID);
            return;
        }

        // ── 4. Block modifier check ───────────────────────────────────────────
        checkModifiers(player, session, to);
        if (session.getState() != SessionState.ACTIVE) return;

        // ── 5. Blocklist check ────────────────────────────────────────────────
        Block feetBlock = to.getBlock().getRelative(0, -1, 0);
        if (!feetBlock.isEmpty()) {
            java.util.Set<org.bukkit.Material> blockList = course.getBlockList();
            boolean isListed = blockList.contains(feetBlock.getType());
            if (course.getBlockListMode() == me.usainsrht.basicparkour.api.course.BlockListMode.BLACKLIST) {
                if (isListed) {
                    sessionManager.failSession(player, ParkourSession.FailReason.BLOCK_LIST);
                    return;
                }
            } else if (course.getBlockListMode() == me.usainsrht.basicparkour.api.course.BlockListMode.WHITELIST) {
                if (!isListed && !blockList.isEmpty()) {
                    sessionManager.failSession(player, ParkourSession.FailReason.BLOCK_LIST);
                    return;
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Helper: check block modifiers at player feet
    // -----------------------------------------------------------------------

    private void checkModifiers(@NotNull Player player,
                                 @NotNull ParkourSession session,
                                 @NotNull Location to) {
        // Check the block the player is standing on (feet block - 1)
        Block feetBlock = to.getBlock().getRelative(0, -1, 0);
        Block headBlock = to.clone().add(0, 1, 0).getBlock();

        ParkourCourse course = session.getCourse();

        // Course-specific modifier overrides take priority
        for (Block block : new Block[]{feetBlock, headBlock}) {
            String overrideKey = course.getModifierOverrides().get(block.getType());
            if (overrideKey != null) {
                var modOpt = plugin.getBasicParkourAPI().getModifierRegistry()
                    .getModifier(org.bukkit.NamespacedKey.fromString(overrideKey));
                if (modOpt.isPresent()) {
                    triggerModifier(player, session, block, modOpt.get());
                    return;
                }
            }

            // Global modifier registry
            for (BlockModifier modifier : plugin.getBasicParkourAPI().getModifierRegistry().getModifiers()) {
                if (modifier.matches(block)) {
                    triggerModifier(player, session, block, modifier);
                    return;
                }
            }
        }
    }

    private void triggerModifier(@NotNull Player player,
                                 @NotNull ParkourSession session,
                                 @NotNull Block block,
                                 @NotNull BlockModifier modifier) {
        me.usainsrht.basicparkour.api.event.BlockModifierTriggerEvent event =
            new me.usainsrht.basicparkour.api.event.BlockModifierTriggerEvent(player, session, block, modifier);
        org.bukkit.Bukkit.getPluginManager().callEvent(event);
        if (!event.isCancelled()) {
            modifier.apply(player, session);
        }
    }

    // -----------------------------------------------------------------------
    // Helper: start region detection (no active session)
    // -----------------------------------------------------------------------

    private void handleStartRegion(@NotNull Player player, @NotNull Location to) {
        for (ParkourCourse course : plugin.getBasicParkourAPI().getCourseRegistry().getCourses()) {
            // Ensure same world
            if (!course.getWorldName().equals(to.getWorld().getName())) continue;
            if (course.getStartRegion().overlaps(player.getBoundingBox()) || course.getStartRegion().contains(to)) {
                // When player walks into the start region, start right there without teleporting
                sessionManager.startSession(player, course, false);
                return; // Start at most one course
            }
            if (course.getPortalRegion().isPresent()) {
                var portal = course.getPortalRegion().get();
                if (portal.overlaps(player.getBoundingBox()) || portal.contains(to)) {
                    sessionManager.startSession(player, course, course.isPortalTeleport());
                    return;
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Helper: block equality check
    // -----------------------------------------------------------------------

    private static boolean sameBlock(@NotNull Location a, @NotNull Location b) {
        return a.getBlockX() == b.getBlockX()
            && a.getBlockY() == b.getBlockY()
            && a.getBlockZ() == b.getBlockZ();
    }
}
