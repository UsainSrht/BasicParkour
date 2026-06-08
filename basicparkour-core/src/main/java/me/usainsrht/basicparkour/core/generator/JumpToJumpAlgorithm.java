package me.usainsrht.basicparkour.core.generator;

import me.usainsrht.basicparkour.api.course.Checkpoint;
import me.usainsrht.basicparkour.api.generator.GeneratorTheme;
import me.usainsrht.basicparkour.api.generator.GeneratorTemplate;
import me.usainsrht.basicparkour.core.course.CheckpointImpl;
import me.usainsrht.basicparkour.core.course.CourseRegionImpl;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Stateless, <strong>purely mathematical</strong> algorithm that computes the next
 * platform location and block set for a jump-to-jump procedural parkour run.
 *
 * <h2>Thread-safety / Folia contract</h2>
 * <p><strong>This class makes zero Bukkit world reads.</strong> No {@code block.getType()},
 * no chunk loading, no entity access. Every method is safe to call from any thread,
 * including Folia async workers.</p>
 *
 * <p>Actual block placement (and capturing each block's original material for cleanup)
 * is delegated entirely to {@link GeneratorManagerImpl}, which dispatches those
 * operations to the correct region thread via {@link me.usainsrht.basicparkour.core.scheduling.FoliaScheduler#runAtLocation}.</p>
 *
 * <h2>Algorithm overview</h2>
 * <ol>
 *   <li>Pick a random horizontal angle relative to the current direction of travel
 *       (clamped to ±{@value #MAX_HEADING_SPREAD_DEG}° to prevent U-turns).</li>
 *   <li>Pick a horizontal gap in [minGap, maxGap].</li>
 *   <li>Pick a vertical delta in [minHeightDelta, maxHeightDelta].</li>
 *   <li>Compute the centre of the next platform from those values.</li>
 *   <li>Build the platform: 1–N blocks wide, material chosen from the theme palette.</li>
 *   <li>Optionally add an obstacle block on top.</li>
 *   <li>Produce a {@link GeneratedPlatform} describing all of the above.</li>
 * </ol>
 */
public final class JumpToJumpAlgorithm {

    /** How many degrees left or right from the current heading the next jump can deviate. */
    private static final double MAX_HEADING_SPREAD_DEG = 75.0;

    // ── Platform result ────────────────────────────────────────────────────

    /**
     * Result of generating a single platform — entirely async-safe.
     *
     * @param centerLocation  Centre block of the platform (at platform level).
     * @param placedBlocks    All blocks computed for this platform (desired materials only, no world reads).
     * @param checkpoint      Checkpoint whose trigger region is this platform.
     * @param platformIndex   Zero-based index of this platform in the run.
     * @param outgoingYawDeg  Heading leaving this platform (passed as {@code lastYawDeg} for the next call).
     */
    public record GeneratedPlatform(
        @NotNull Location centerLocation,
        @NotNull List<PlacedBlock> placedBlocks,
        @NotNull Checkpoint checkpoint,
        int platformIndex,
        float outgoingYawDeg
    ) {}

    private JumpToJumpAlgorithm() {} // static utility

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Generates the <em>first</em> platform at the given start location (index 0).
     * No jump physics apply — the platform is placed exactly at {@code startLocation}.
     *
     * <p><strong>Async-safe:</strong> no world reads.</p>
     *
     * @param template      generator template
     * @param world         the world (used only to construct Locations, not to read blocks)
     * @param startLocation desired centre of the start platform
     * @param rng           seeded random; caller owns its lifecycle
     * @return the generated start platform
     */
    @NotNull
    public static GeneratedPlatform generateStart(
        @NotNull GeneratorTemplate template,
        @NotNull World world,
        @NotNull Location startLocation,
        @NotNull Random rng
    ) {
        GeneratorTheme theme = template.theme();

        int minSize = template.resolvedPlatformMinSize();
        int maxSize = template.resolvedPlatformMaxSize();
        int platformSize = minSize + rng.nextInt(Math.max(1, maxSize - minSize + 1));

        Location center = startLocation.clone();
        List<PlacedBlock> blocks = new ArrayList<>();

        for (int bx = -(platformSize / 2); bx <= platformSize / 2; bx++) {
            for (int bz = -(platformSize / 2); bz <= platformSize / 2; bz++) {
                Location blockLoc = new Location(world,
                    center.getBlockX() + bx,
                    center.getBlockY(),
                    center.getBlockZ() + bz);
                // No block.getType() — desiredMaterial only
                blocks.add(new PlacedBlock(blockLoc, pickPlatformMaterial(theme, rng), 0));
            }
        }

        Location regionMin = new Location(world,
            center.getBlockX() - 2, center.getBlockY(), center.getBlockZ() - 2);
        Location regionMax = new Location(world,
            center.getBlockX() + 2, center.getBlockY() + 3, center.getBlockZ() + 2);

        Location spawnLoc = new Location(world,
            center.getBlockX() + 0.5, center.getBlockY() + 1, center.getBlockZ() + 0.5, 0f, 0f);
        Checkpoint checkpoint = new CheckpointImpl(0, spawnLoc,
            new CourseRegionImpl(regionMin, regionMax));

        return new GeneratedPlatform(center, List.copyOf(blocks), checkpoint, 0, 0f);
    }

    /**
     * Generates a single subsequent platform, chained from {@code lastCenter}.
     *
     * <p><strong>Async-safe:</strong> no world reads.</p>
     *
     * @param template      generator template
     * @param world         the world (used only to construct Locations)
     * @param lastCenter    centre of the previous platform
     * @param lastYawDeg    outgoing heading of the previous jump (Minecraft yaw degrees)
     * @param platformIndex zero-based index of this new platform
     * @param rng           seeded random; caller owns its lifecycle
     * @return the generated platform descriptor
     */
    @NotNull
    public static GeneratedPlatform generateNext(
        @NotNull GeneratorTemplate template,
        @NotNull World world,
        @NotNull Location lastCenter,
        float lastYawDeg,
        int platformIndex,
        @NotNull Random rng
    ) {
        GeneratorTheme theme = template.theme();

        // ── 1. Choose heading ──────────────────────────────────────────────
        double spreadRad = Math.toRadians(
            (rng.nextDouble() * 2 - 1) * MAX_HEADING_SPREAD_DEG
        );
        // Minecraft yaw: 0 = south (+Z), 90 = west (-X), negate for maths convention
        double headingRad = Math.toRadians(-lastYawDeg) + spreadRad;

        // ── 2. Choose gap and height ───────────────────────────────────────
        int minGap = template.resolvedMinGap();
        int maxGap = template.resolvedMaxGap();
        int gap = minGap + rng.nextInt(Math.max(1, maxGap - minGap + 1));

        int minDelta = template.resolvedMinHeightDelta();
        int maxDelta = template.resolvedMaxHeightDelta();
        int heightDelta = minDelta + rng.nextInt(Math.max(1, maxDelta - minDelta + 1));

        // ── 3. Compute next centre ─────────────────────────────────────────
        double dx = gap * Math.sin(headingRad);
        double dz = gap * Math.cos(headingRad);

        int newX = (int) Math.round(lastCenter.getX() + dx);
        int newY = lastCenter.getBlockY() + heightDelta;
        int newZ = (int) Math.round(lastCenter.getZ() + dz);

        Location center = new Location(world, newX, newY, newZ);

        // ── 4. Platform size ───────────────────────────────────────────────
        int minSize = template.resolvedPlatformMinSize();
        int maxSize = template.resolvedPlatformMaxSize();
        int platformSize = minSize + rng.nextInt(Math.max(1, maxSize - minSize + 1));

        // ── 5. Build block list (zero world reads) ─────────────────────────
        List<PlacedBlock> blocks = new ArrayList<>();

        for (int bx = -(platformSize / 2); bx <= platformSize / 2; bx++) {
            for (int bz = -(platformSize / 2); bz <= platformSize / 2; bz++) {
                Location blockLoc = new Location(world,
                    center.getBlockX() + bx,
                    center.getBlockY(),
                    center.getBlockZ() + bz);
                blocks.add(new PlacedBlock(blockLoc, pickPlatformMaterial(theme, rng), platformIndex));
            }
        }

        // ── 6. Obstacle block on top of centre (optional) ─────────────────
        if (!theme.obstacleMaterials().isEmpty()
                && rng.nextDouble() < template.resolvedObstacleChance()) {
            Location obstacleLoc = new Location(world,
                center.getBlockX(), center.getBlockY() + 1, center.getBlockZ());
            Material obstacleMat = pickFrom(theme.obstacleMaterials(), rng);
            blocks.add(new PlacedBlock(obstacleLoc, obstacleMat, platformIndex));
        }

        // ── 7. Checkpoint ──────────────────────────────────────────────────
        // Outgoing Minecraft yaw: convert headingRad back (negate again)
        float outgoingYaw = (float) Math.toDegrees(-headingRad);

        Location regionMin = new Location(world,
            center.getBlockX() - 1, center.getBlockY(), center.getBlockZ() - 1);
        Location regionMax = new Location(world,
            center.getBlockX() + 1, center.getBlockY() + 2, center.getBlockZ() + 1);

        Location spawnLoc = new Location(world,
            center.getBlockX() + 0.5, center.getBlockY() + 1, center.getBlockZ() + 0.5,
            outgoingYaw, 0f);

        Checkpoint checkpoint = new CheckpointImpl(platformIndex, spawnLoc,
            new CourseRegionImpl(regionMin, regionMax));

        return new GeneratedPlatform(center, List.copyOf(blocks), checkpoint, platformIndex, outgoingYaw);
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private static Material pickPlatformMaterial(@NotNull GeneratorTheme theme, @NotNull Random rng) {
        List<Material> palette = new ArrayList<>(theme.platformMaterials());
        palette.addAll(theme.accentMaterials());
        return pickFrom(palette, rng);
    }

    private static Material pickFrom(@NotNull List<Material> list, @NotNull Random rng) {
        return list.get(rng.nextInt(list.size()));
    }
}
