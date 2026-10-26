package me.usainsrht.basicparkour.core.listener;

import me.usainsrht.basicparkour.api.course.CourseRegion;
import me.usainsrht.basicparkour.api.course.ParkourCourse;
import me.usainsrht.basicparkour.api.modifier.BlockModifier;
import me.usainsrht.basicparkour.api.session.ParkourSession;
import me.usainsrht.basicparkour.api.session.SessionState;
import me.usainsrht.basicparkour.core.BasicParkourPlugin;
import me.usainsrht.basicparkour.core.session.SessionManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

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
    private final java.util.Map<java.util.UUID, Long> startRegionCooldown = new java.util.concurrent.ConcurrentHashMap<>();

    public void setStartCooldown(@NotNull java.util.UUID uuid, long ms) {
        startRegionCooldown.put(uuid, System.currentTimeMillis() + ms);
    }

    public boolean isStartOnCooldown(@NotNull java.util.UUID uuid) {
        Long until = startRegionCooldown.get(uuid);
        if (until == null) return false;
        if (System.currentTimeMillis() < until) return true;
        startRegionCooldown.remove(uuid);
        return false;
    }

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

        boolean inStart = isInStartRegion(course, player, to);
        boolean inCp = isInCurrentCheckpointRegion(session, player, to);
        boolean respawnImmune = sessionManager.isRespawnImmune(player.getUniqueId());

        if (plugin.getDebugManager().isDebug(player)) {
            plugin.getDebugManager().sendLiveActionBar(player, session, to, inStart, inCp, respawnImmune);
        }

        // ── 0. Anti-flight check ─────────────────────────────────────────────
        if (plugin.getConfig().getBoolean("restrictions.disable-flight", true) && player.isFlying()) {
            player.setAllowFlight(false);
            player.setFlying(false);
            sessionManager.failSession(player, ParkourSession.FailReason.VOID, "Anti-flight triggered (player.isFlying=true)");
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

        // Skip fail checks if player has respawn immunity
        if (respawnImmune) {
            return;
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
            sessionManager.failSession(player, ParkourSession.FailReason.VOID,
                String.format(java.util.Locale.ROOT, "Player Y (%.2f) below failY threshold (%.2f)", to.getY(), failY));
            return;
        }

        // ── 4. Block modifier check ───────────────────────────────────────────
        checkModifiers(player, session, to);
        if (session.getState() != SessionState.ACTIVE) return;

        // ── 5. Blocklist check ────────────────────────────────────────────────
        // Ignore blocklist checks while inside the start region or at the current checkpoint
        if (!inStart && !inCp) {
            checkBlockList(player, session, course, to);
        }
    }

    // -----------------------------------------------------------------------
    // Helper: check block modifiers at player feet
    // -----------------------------------------------------------------------

    private void checkModifiers(@NotNull Player player,
                                 @NotNull ParkourSession session,
                                 @NotNull Location to) {
        Block atFeet = to.getBlock();
        Block belowFeet = to.clone().subtract(0, 0.1, 0).getBlock();
        Block feetBlock = !atFeet.isEmpty() ? atFeet : belowFeet;
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
        if (isStartOnCooldown(player.getUniqueId())) return;
        for (ParkourCourse course : plugin.getBasicParkourAPI().getCourseRegistry().getCourses()) {
            // Ensure same world
            if (!course.getWorldName().equals(to.getWorld().getName())) continue;
            if (course.getStartRegion().overlaps(player.getBoundingBox()) || course.getStartRegion().contains(to)) {
                // When player walks into the start region, start right there without teleporting
                setStartCooldown(player.getUniqueId(), 1500L);
                sessionManager.startSession(player, course, false);
                return; // Start at most one course
            }
            if (course.getPortalRegion().isPresent()) {
                var portal = course.getPortalRegion().get();
                if (portal.overlaps(player.getBoundingBox()) || portal.contains(to)) {
                    setStartCooldown(player.getUniqueId(), 1500L);
                    sessionManager.startSession(player, course, course.isPortalTeleport());
                    return;
                }
            }
        }
    }

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

    private void checkBlockList(@NotNull Player player,
                                @NotNull ParkourSession session,
                                @NotNull ParkourCourse course,
                                @NotNull Location to) {
        Set<Material> blockList = course.getBlockList();
        if (blockList.isEmpty()) return;

        me.usainsrht.basicparkour.api.course.BlockListMode mode = course.getBlockListMode();

        // 1. Liquids (water, lava, etc.)
        Block inBlock = to.getBlock();
        if (inBlock.isLiquid()) {
            boolean isListed = blockList.contains(inBlock.getType());
            if (mode == me.usainsrht.basicparkour.api.course.BlockListMode.BLACKLIST && isListed) {
                sessionManager.failSession(player, ParkourSession.FailReason.BLOCK_LIST,
                    "In blacklisted liquid: " + inBlock.getType());
                return;
            } else if (mode == me.usainsrht.basicparkour.api.course.BlockListMode.WHITELIST && !isListed) {
                sessionManager.failSession(player, ParkourSession.FailReason.BLOCK_LIST,
                    "In non-whitelisted liquid: " + inBlock.getType());
                return;
            }
        }

        // 2. Blacklist mode body contact (e.g. touching fire or blacklisted block)
        if (mode == me.usainsrht.basicparkour.api.course.BlockListMode.BLACKLIST) {
            if (blockList.contains(inBlock.getType())) {
                sessionManager.failSession(player, ParkourSession.FailReason.BLOCK_LIST,
                    "Body contact with blacklisted block: " + inBlock.getType());
                return;
            }
        }

        // 3. Ground / standing check (only applies when player is on ground / stepping on blocks)
        if (player.isOnGround()) {
            Block centerFeet = to.getBlock();
            Block centerUnder = to.clone().subtract(0, 0.1, 0).getBlock();
            Block centerSupporting = !centerFeet.isEmpty() && !centerFeet.isPassable() ? centerFeet : centerUnder;
            Material centerMat = centerSupporting.getType();

            Set<Material> standingMats = getStandingMaterials(player, to);

            if (mode == me.usainsrht.basicparkour.api.course.BlockListMode.BLACKLIST) {
                if (blockList.contains(centerMat)) {
                    sessionManager.failSession(player, ParkourSession.FailReason.BLOCK_LIST,
                        "Standing on blacklisted block: " + centerMat);
                    return;
                }
                for (Material mat : standingMats) {
                    if (blockList.contains(mat)) {
                        sessionManager.failSession(player, ParkourSession.FailReason.BLOCK_LIST,
                            "Hitbox touches blacklisted block: " + mat);
                        return;
                    }
                }
            } else if (mode == me.usainsrht.basicparkour.api.course.BlockListMode.WHITELIST) {
                // If the block directly supporting player's center is whitelisted, this is a VALID jump!
                if (!centerSupporting.isEmpty() && blockList.contains(centerMat)) {
                    return; // Player landed squarely on a whitelisted block!
                }

                // If center is over air (edge overhang), check if ANY supporting block is whitelisted
                if (centerSupporting.isEmpty()) {
                    boolean hasWhitelisted = false;
                    for (Material mat : standingMats) {
                        if (blockList.contains(mat)) {
                            hasWhitelisted = true;
                            break;
                        }
                    }
                    if (hasWhitelisted) {
                        return; // Catching edge of a whitelisted block over air
                    }
                    if (!standingMats.isEmpty()) {
                        sessionManager.failSession(player, ParkourSession.FailReason.BLOCK_LIST,
                            "Not on whitelisted block. Standing on: " + standingMats + " (whitelist: " + blockList + ")");
                        return;
                    }
                } else {
                    // Center supporting block is solid and NOT in whitelist (e.g. landed on forbidden floor)
                    sessionManager.failSession(player, ParkourSession.FailReason.BLOCK_LIST,
                        "Standing on forbidden block " + centerMat + " (not in whitelist: " + blockList + ")");
                    return;
                }
            }
        }
    }

    @NotNull
    private Set<Material> getStandingMaterials(@NotNull Player player, @NotNull Location to) {
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

    // -----------------------------------------------------------------------
    // Helper: block equality check
    // -----------------------------------------------------------------------

    private static boolean sameBlock(@NotNull Location a, @NotNull Location b) {
        return a.getBlockX() == b.getBlockX()
            && a.getBlockY() == b.getBlockY()
            && a.getBlockZ() == b.getBlockZ();
    }
}
