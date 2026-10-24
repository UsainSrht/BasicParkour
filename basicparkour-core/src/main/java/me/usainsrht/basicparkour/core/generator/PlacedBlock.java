package me.usainsrht.basicparkour.core.generator;

import org.bukkit.Location;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

/**
 * A single block that was (or will be) placed by the generator into the world.
 *
 * <h2>Thread-safety contract</h2>
 * <p>{@code desiredMaterial} is the material the generator <em>wants</em> to place — it is
 * computed purely mathematically on the async thread and requires no Bukkit world access.</p>
 *
 * <p>The block's <em>original</em> material (needed for cleanup) is captured at placement time
 * on the correct region thread and stored separately in
 * {@link GeneratedCourseInstanceImpl#originalMaterials}.</p>
 *
 * @param location        The exact block location (defensively cloned).
 * @param desiredMaterial The material the generator wants to place here.
 * @param platformIndex   Zero-based index of the platform this block belongs to.
 *                        Used by the sliding window to determine which platforms are "behind" the player.
 * @param placedAtMillis  Timestamp in milliseconds when this block was staged/placed.
 */
public record PlacedBlock(
    @NotNull Location location,
    @NotNull Material desiredMaterial,
    int platformIndex,
    long placedAtMillis
) {
    public PlacedBlock(@NotNull Location location, @NotNull Material desiredMaterial, int platformIndex) {
        this(location, desiredMaterial, platformIndex, System.currentTimeMillis());
    }

    /** Creates a snapshot — location is defensively cloned so mutations don't affect us. */
    public PlacedBlock {
        location = location.clone();
    }
}
