package me.usainsrht.basicparkour.core.generator;

import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Allocates non-overlapping 2D "slots" for generated parkour runs inside a dedicated world.
 *
 * <h2>Algorithm</h2>
 * <ol>
 *   <li>Pick a random (x, z) inside the configured radius.</li>
 *   <li>Compute the bounding rectangle for a run starting there
 *       (width = runway extent based on max gap and platform count).</li>
 *   <li>Check all existing active slots for overlap (with an extra padding buffer).</li>
 *   <li>If no overlap, reserve the slot and return the origin location.</li>
 *   <li>Retry up to {@link #MAX_ATTEMPTS} times before giving up.</li>
 * </ol>
 *
 * <p>Slots are released when the corresponding {@link GeneratedCourseInstanceImpl}
 * is deactivated (via {@link #release(int)}).</p>
 */
public final class SlotAllocator {

    private static final int MAX_ATTEMPTS = 64;

    /**
     * Represents a reserved rectangular slot in the XZ plane.
     *
     * @param id   unique slot id (auto-incremented)
     * @param minX inclusive min X
     * @param minZ inclusive min Z
     * @param maxX inclusive max X
     * @param maxZ inclusive max Z
     */
    public record Slot(int id, int minX, int minZ, int maxX, int maxZ) {
        boolean overlaps(@NotNull Slot other, int padding) {
            return minX - padding <= other.maxX + padding
                && maxX + padding >= other.minX - padding
                && minZ - padding <= other.maxZ + padding
                && maxZ + padding >= other.minZ - padding;
        }
    }

    private final int radius;
    private final int slotHalfWidth;
    private final int padding;
    private final int fixedY;

    private final List<Slot> activeSlots = new ArrayList<>();
    private int nextId = 0;
    private final Random slotRng = new Random();

    /**
     * @param radius        Maximum distance from origin in XZ to place a slot.
     * @param slotHalfWidth Half-width (and half-length) of each reserved bounding box.
     *                      Should be ≥ max-gap × max-platforms to guarantee separation.
     * @param padding       Extra clearance added around each slot when checking overlaps.
     * @param fixedY        The Y level at which generated runs are anchored.
     */
    public SlotAllocator(int radius, int slotHalfWidth, int padding, int fixedY) {
        this.radius       = radius;
        this.slotHalfWidth = slotHalfWidth;
        this.padding      = padding;
        this.fixedY       = fixedY;
    }

    /**
     * Attempts to allocate a slot and returns its origin {@link Location} in the given world.
     *
     * @param world the world (used only to produce a Location)
     * @return a location if successful, or {@code null} if no slot was found after retries
     */
    @Nullable
    public synchronized AllocatedSlot allocate(@NotNull org.bukkit.World world) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            int cx = (int) ((slotRng.nextDouble() * 2 - 1) * radius);
            int cz = (int) ((slotRng.nextDouble() * 2 - 1) * radius);

            Slot candidate = new Slot(nextId,
                cx - slotHalfWidth, cz - slotHalfWidth,
                cx + slotHalfWidth, cz + slotHalfWidth);

            boolean overlapping = false;
            for (Slot active : activeSlots) {
                if (candidate.overlaps(active, padding)) {
                    overlapping = true;
                    break;
                }
            }

            if (!overlapping) {
                activeSlots.add(candidate);
                nextId++;
                Location origin = new Location(world, cx + 0.5, fixedY, cz + 0.5);
                return new AllocatedSlot(candidate.id(), origin);
            }
        }
        return null; // no slot available
    }

    /**
     * Releases a previously allocated slot so its space can be reused.
     *
     * @param slotId the slot ID returned by {@link #allocate}
     */
    public synchronized void release(int slotId) {
        activeSlots.removeIf(s -> s.id() == slotId);
    }

    /**
     * Result of a successful allocation.
     *
     * @param slotId the opaque slot identifier (needed to call {@link #release})
     * @param origin the spawn / start location for the generated run
     */
    public record AllocatedSlot(int slotId, @NotNull Location origin) {}
}
