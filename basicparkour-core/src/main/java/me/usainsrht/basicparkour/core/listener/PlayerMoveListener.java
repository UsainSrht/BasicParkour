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

        // Skip if only head moved (no block change)
        if (sameBlock(from, to)) return;

        Optional<ParkourSession> sessionOpt = sessionManager.getSession(player);

        if (sessionOpt.isEmpty()) {
            // Check start regions
            handleStartRegion(player, to);
            return;
        }

        ParkourSession session = sessionOpt.get();
        if (session.getState() != SessionState.ACTIVE) return;

        ParkourCourse course = session.getCourse();

        // ── 1. Void check ────────────────────────────────────────────────────
        if (to.getY() < to.getWorld().getMinHeight() - 5) {
            sessionManager.failSession(player, ParkourSession.FailReason.VOID);
            return;
        }

        // ── 2. End region check ──────────────────────────────────────────────
        if (course.getEndRegion().contains(to)) {
            sessionManager.completeSession(player);
            return;
        }

        // ── 3. Checkpoint region check ───────────────────────────────────────
        session.getNextCheckpoint().ifPresent(checkpoint -> {
            if (checkpoint.getTriggerRegion().contains(to)) {
                sessionManager.handleCheckpointReach(player, checkpoint);
            }
        });

        // ── 4. Block modifier check ───────────────────────────────────────────
        checkModifiers(player, session, to);
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
                plugin.getBasicParkourAPI().getModifierRegistry()
                    .getModifier(org.bukkit.NamespacedKey.fromString(overrideKey))
                    .ifPresent(mod -> mod.apply(player, session));
                return;
            }

            // Global modifier registry
            for (BlockModifier modifier : plugin.getBasicParkourAPI().getModifierRegistry().getModifiers()) {
                if (modifier.matches(block)) {
                    modifier.apply(player, session);
                    return;
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Helper: start region detection (no active session)
    // -----------------------------------------------------------------------

    private void handleStartRegion(@NotNull Player player, @NotNull Location to) {
        for (ParkourCourse course : plugin.getBasicParkourAPI().getCourseRegistry().getCourses()) {
            // Ensure same world
            if (!course.getWorldName().equals(to.getWorld().getName())) continue;
            if (course.getStartRegion().contains(to)) {
                sessionManager.startSession(player, course);
                return; // Start at most one course
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
