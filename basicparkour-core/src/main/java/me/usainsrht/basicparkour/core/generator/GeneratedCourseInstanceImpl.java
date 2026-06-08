package me.usainsrht.basicparkour.core.generator;

import me.usainsrht.basicparkour.api.generator.GeneratedCourseInstance;
import me.usainsrht.basicparkour.api.generator.GeneratedParkourCourse;
import me.usainsrht.basicparkour.core.scheduling.FoliaScheduler;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Internal implementation of {@link GeneratedCourseInstance}.
 *
 * <h2>Folia thread-safety</h2>
 * <ul>
 *   <li>{@link #placedBlocks} is written by the async generation phase (adding computed blocks)
 *       and read/written by region threads during placement and cleanup. Access is guarded by
 *       the list's own monitor via {@code synchronized(placedBlocks)}.</li>
 *   <li>{@link #originalMaterials} is written <em>only</em> on region threads (at placement time)
 *       and read on region threads (at cleanup time). It is thread-safe via {@link ConcurrentHashMap}.</li>
 * </ul>
 */
public final class GeneratedCourseInstanceImpl implements GeneratedCourseInstance {

    private final GeneratedParkourCourseImpl course;
    private final long seed;

    /**
     * Computed blocks (desired materials). Populated on the async math thread, then
     * iterated by region threads for placement. After placement, entries are never mutated.
     */
    final List<PlacedBlock> placedBlocks = Collections.synchronizedList(new ArrayList<>());

    /**
     * Original material at each block location, captured <em>on the region thread</em>
     * just before placement. Keyed by "world,x,y,z" string to avoid Location.hashCode issues.
     * Used for cleanup restoration.
     */
    final ConcurrentHashMap<String, Material> originalMaterials = new ConcurrentHashMap<>();

    /** How many platforms have been fully placed. */
    final AtomicInteger placedPlatformCount = new AtomicInteger(0);

    /** Players currently on this instance. */
    private final Set<UUID> playerUuids = ConcurrentHashMap.newKeySet();
    private final Set<Player> playerSet = ConcurrentHashMap.newKeySet();

    private final AtomicBoolean active = new AtomicBoolean(true);

    /** Index of the first platform not yet removed by the sliding window. */
    volatile int slidingWindowHead = 0;

    /** Handle for the sliding-window repeating task. Null for FULL_UPFRONT. */
    volatile FoliaScheduler.RepeatingTask slidingTask;

    public GeneratedCourseInstanceImpl(
        @NotNull GeneratedParkourCourseImpl course,
        long seed
    ) {
        this.course = course;
        this.seed   = seed;
    }

    // ── GeneratedCourseInstance (API) ──────────────────────────────────────

    @Override @NotNull public GeneratedParkourCourse getCourse() { return course; }

    @Override @NotNull @Unmodifiable
    public Set<Player> getPlayers() { return Collections.unmodifiableSet(playerSet); }

    @Override public int getPlacedPlatformCount() { return placedPlatformCount.get(); }
    @Override public long getSeed()               { return seed; }
    @Override public boolean isActive()           { return active.get(); }

    // ── Mutable state ──────────────────────────────────────────────────────

    public void addPlayer(@NotNull Player player) {
        playerUuids.add(player.getUniqueId());
        playerSet.add(player);
    }

    public void removePlayer(@NotNull Player player) {
        playerUuids.remove(player.getUniqueId());
        playerSet.remove(player);
    }

    public boolean hasPlayer(@NotNull Player player) {
        return playerUuids.contains(player.getUniqueId());
    }

    public boolean isEmpty() { return playerUuids.isEmpty(); }

    /** Marks this instance as inactive. Idempotent; returns true only on first call. */
    public boolean tryDeactivate() { return active.compareAndSet(true, false); }

    // ── Block placement helper (called on region thread) ───────────────────

    /**
     * Places a single block on the current region thread:
     * <ol>
     *   <li>Reads and records the original material.</li>
     *   <li>Sets the desired material.</li>
     * </ol>
     * <strong>MUST be called on the region thread that owns the block's chunk.</strong>
     */
    public void placeBlock(@NotNull PlacedBlock pb) {
        org.bukkit.block.Block block = pb.location().getBlock();
        String key = locationKey(pb.location());
        originalMaterials.putIfAbsent(key, block.getType()); // capture original
        block.setType(pb.desiredMaterial(), false);
    }

    // ── Cleanup (called on region thread owning each block's chunk) ────────

    /**
     * Restores all placed blocks to their original material.
     * <strong>MUST be called on the correct region thread for each block.</strong>
     * {@link GeneratorManagerImpl} dispatches this per-chunk.
     */
    public void restoreBlocks(@NotNull Logger logger) {
        int count = 0;
        synchronized (placedBlocks) {
            for (PlacedBlock pb : placedBlocks) {
                try {
                    String key = locationKey(pb.location());
                    Material original = originalMaterials.getOrDefault(key, Material.AIR);
                    pb.location().getBlock().setType(original, false);
                    count++;
                } catch (Exception ex) {
                    logger.warning("[Generator] Failed to restore block at " + pb.location() + ": " + ex.getMessage());
                }
            }
            placedBlocks.clear();
        }
        originalMaterials.clear();
        logger.info("[Generator] Restored " + count + " blocks for instance of course '" + course.getId() + "'.");
    }

    /**
     * Restores only blocks belonging to platforms with index {@code <= upToPlatformIndex}.
     * Used by the sliding window to clean up platforms the player has passed.
     * <strong>MUST be called on the region thread owning each block's chunk.</strong>
     */
    public void restoreBlocksUpTo(int upToPlatformIndex, @NotNull Logger logger) {
        synchronized (placedBlocks) {
            placedBlocks.removeIf(pb -> {
                if (pb.platformIndex() <= upToPlatformIndex) {
                    try {
                        String key = locationKey(pb.location());
                        Material original = originalMaterials.remove(key);
                        pb.location().getBlock().setType(original != null ? original : Material.AIR, false);
                    } catch (Exception ex) {
                        logger.warning("[Generator] Sliding cleanup failed at " + pb.location());
                    }
                    return true;
                }
                return false;
            });
        }
    }

    @NotNull
    public GeneratedParkourCourseImpl getCourseImpl() { return course; }

    // ── Utility ────────────────────────────────────────────────────────────

    static String locationKey(@NotNull Location loc) {
        return loc.getWorld().getName() + ","
            + loc.getBlockX() + ","
            + loc.getBlockY() + ","
            + loc.getBlockZ();
    }
}
