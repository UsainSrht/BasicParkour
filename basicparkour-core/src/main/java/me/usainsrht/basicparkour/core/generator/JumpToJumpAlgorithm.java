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
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
 * <h2>Vanilla Jump Physics Guarantee</h2>
 * <p>All generated jumps are strictly verified to ensure they are physically possible in
 * vanilla Minecraft without potion effects or external aids:</p>
 * <ul>
 *   <li>Vertical height delta is capped to at most +1 block (max vanilla jump is 1.25 blocks).</li>
 *   <li>Horizontal gap is bounded by height delta: max 3.0 blocks when jumping +1 up;
 *       max 4.0 blocks when flat; max 4.2 blocks when jumping down.</li>
 *   <li>Sharp turns are restricted on large gaps to preserve player sprint momentum.</li>
 *   <li>Obstacles are never placed directly on top of 1x1 landing platforms.</li>
 * </ul>
 */
public final class JumpToJumpAlgorithm {

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
     * @param template      generator template
     * @param world         the world (used only to construct Locations)
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
        return generateStart(template, world, startLocation, 0f, rng);
    }

    /**
     * Generates the <em>first</em> platform at the given start location (index 0) with an explicit heading.
     *
     * @param template       generator template
     * @param world          the world (used only to construct Locations)
     * @param startLocation  desired centre of the start platform
     * @param initialYawDeg  outgoing heading leaving the start platform (e.g. player walk direction)
     * @param rng            seeded random; caller owns its lifecycle
     * @return the generated start platform
     */
    @NotNull
    public static GeneratedPlatform generateStart(
        @NotNull GeneratorTemplate template,
        @NotNull World world,
        @NotNull Location startLocation,
        float initialYawDeg,
        @NotNull Random rng
    ) {
        GeneratorTheme theme = template.theme();
        List<String> shapes = template.resolvedPlatformShapes();
        String shape = shapes.get(rng.nextInt(shapes.size()));

        Location center = startLocation.clone();
        List<PlacedBlock> blocks = buildBlocksForShape(shape, center, world, theme, rng, 0);

        Checkpoint checkpoint = buildPlatformCheckpoint(world, 0, center, initialYawDeg, blocks);

        return new GeneratedPlatform(center, List.copyOf(blocks), checkpoint, 0, initialYawDeg);
    }

    /**
     * Normalizes an angle in degrees to the range (-180, 180].
     */
    public static float normalizeDegrees(float degrees) {
        degrees = degrees % 360f;
        if (degrees > 180f) degrees -= 360f;
        if (degrees <= -180f) degrees += 360f;
        return degrees;
    }

    /**
     * Generates a single subsequent platform, chained from {@code lastCenter}.
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
        return generateNext(template, world, lastCenter, lastYawDeg, lastYawDeg, null, null, platformIndex, rng);
    }

    /**
     * Generates a single subsequent platform, chained from {@code lastCenter} with an explicit base heading.
     */
    @NotNull
    public static GeneratedPlatform generateNext(
        @NotNull GeneratorTemplate template,
        @NotNull World world,
        @NotNull Location lastCenter,
        float lastYawDeg,
        float baseYawDeg,
        int platformIndex,
        @NotNull Random rng
    ) {
        return generateNext(template, world, lastCenter, lastYawDeg, baseYawDeg, null, null, platformIndex, rng);
    }

    /**
     * Generates a single subsequent platform, chained from {@code lastCenter} with directional cone,
     * optional corridor boundary, height tethering, and overlap/collision avoidance.
     *
     * @param template      generator template
     * @param world         the world (used only to construct Locations)
     * @param lastCenter    centre of the previous platform
     * @param lastYawDeg    outgoing heading of the previous jump (Minecraft yaw degrees)
     * @param baseYawDeg    base heading of the run (reference angle for cone projection)
     * @param originCenter  start location of the run (used for corridor / cone origin / height tethering)
     * @param recentCenters recent platform center locations for self-overlap / collision avoidance
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
        float baseYawDeg,
        @Nullable Location originCenter,
        @Nullable List<Location> recentCenters,
        int platformIndex,
        @NotNull Random rng
    ) {
        GeneratorTheme theme = template.theme();

        // ── 1. Platform shape ──────────────────────────────────────────────
        List<String> shapes = template.resolvedPlatformShapes();
        String shape = shapes.get(rng.nextInt(shapes.size()));
        boolean isOneBlock = isSingleBlockShape(shape);

        // ── 2. Height delta (clamped strictly to vanilla jump physics + optional tethering) ─────
        // In vanilla Minecraft, players can jump at most 1.25 blocks up. Delta > 1 is impossible.
        int minDelta = Math.max(-2, template.resolvedMinHeightDelta());
        int maxDelta = Math.min(1, template.resolvedMaxHeightDelta());
        if (minDelta > maxDelta) minDelta = maxDelta;

        int maxHeightDev = template.resolvedMaxHeightDeviation();
        if (maxHeightDev > 0 && originCenter != null) {
            int currentYOffset = lastCenter.getBlockY() - originCenter.getBlockY();
            if (currentYOffset >= maxHeightDev) {
                maxDelta = Math.min(maxDelta, 0);
                minDelta = Math.min(minDelta, maxDelta);
            } else if (currentYOffset <= -maxHeightDev) {
                minDelta = Math.max(minDelta, 0);
                maxDelta = Math.max(minDelta, maxDelta);
            }
        }
        int heightDelta = minDelta + rng.nextInt(Math.max(1, maxDelta - minDelta + 1));

        // ── 3. Maximum distance and heading turn constraints ────────────────
        double maxDist;
        double maxSpreadDeg;
        if (heightDelta == 1) {
            // Jumping up 1 block: max horizontal jump in vanilla is 3.0 blocks
            maxDist = 3.0;
            maxSpreadDeg = 35.0;
        } else if (heightDelta == 0) {
            // Flat jump: 4.0 blocks is the vanilla limit
            maxDist = 4.0;
            maxSpreadDeg = 45.0;
        } else {
            // Jumping down: player has more clearance
            maxDist = 4.2;
            maxSpreadDeg = 50.0;
        }

        int minGap = Math.max(2, template.resolvedMinGap());
        int maxGap = Math.min((int) maxDist, Math.max(minGap, template.resolvedMaxGap()));

        if (maxGap >= 4 || isOneBlock) {
            maxSpreadDeg = Math.min(maxSpreadDeg, 30.0);
        }

        // ── 3b. Directional Cone & Corridor constraints ───────────────────────
        double directionCone = template.resolvedDirectionCone();
        float effectiveBaseYaw = template.resolvedDirectionYaw(baseYawDeg);

        double minTurn = -maxSpreadDeg;
        double maxTurn = maxSpreadDeg;

        if (directionCone < 360.0) {
            double halfCone = Math.max(0.0, directionCone / 2.0);
            float currentDeltaFromBase = normalizeDegrees(lastYawDeg - effectiveBaseYaw);

            minTurn = Math.max(-maxSpreadDeg, -halfCone - currentDeltaFromBase);
            maxTurn = Math.min(maxSpreadDeg, halfCone - currentDeltaFromBase);

            if (minTurn > maxTurn) {
                // If last yaw was outside cone, steer directly back towards baseYaw
                if (currentDeltaFromBase > 0) {
                    minTurn = -maxSpreadDeg;
                    maxTurn = Math.max(-maxSpreadDeg, -halfCone - currentDeltaFromBase);
                } else {
                    maxTurn = maxSpreadDeg;
                    minTurn = Math.min(maxSpreadDeg, halfCone - currentDeltaFromBase);
                }
                if (minTurn > maxTurn) {
                    double steer = -Math.signum(currentDeltaFromBase) * maxSpreadDeg;
                    minTurn = steer;
                    maxTurn = steer;
                }
            }
        }

        // Optional lateral corridor constraint
        int corridorWidth = template.resolvedCorridorWidth();
        if (corridorWidth > 0 && originCenter != null) {
            double halfCorridor = corridorWidth / 2.0;
            double rad = Math.toRadians(effectiveBaseYaw);
            double fx = -Math.sin(rad);
            double fz = Math.cos(rad);

            double dxOrigin = lastCenter.getX() - originCenter.getX();
            double dzOrigin = lastCenter.getZ() - originCenter.getZ();
            double lateralOffset = fz * dxOrigin - fx * dzOrigin;

            float currentDeltaFromBase = normalizeDegrees(lastYawDeg - effectiveBaseYaw);

            if (lateralOffset > halfCorridor * 0.7) {
                // Drifting to positive lateral offset -> force relative yaw >= 0 to steer back
                minTurn = Math.max(minTurn, -currentDeltaFromBase);
            } else if (lateralOffset < -halfCorridor * 0.7) {
                // Drifting to negative lateral offset -> force relative yaw <= 0 to steer back
                maxTurn = Math.min(maxTurn, -currentDeltaFromBase);
            }
            if (minTurn > maxTurn) {
                double mid = (minTurn + maxTurn) / 2.0;
                minTurn = mid;
                maxTurn = mid;
            }
        }

        // ── 4. Compute next centre with physical distance & overlap verification ─
        Location center = null;
        float outgoingYaw = lastYawDeg;
        int attempts = 0;
        int maxAttempts = (recentCenters != null && !recentCenters.isEmpty()) ? 10 : 1;

        while (attempts < maxAttempts) {
            attempts++;
            int gap = minGap + rng.nextInt(Math.max(1, maxGap - minGap + 1));
            double currentMinTurn = minTurn;
            double currentMaxTurn = maxTurn;

            if (gap >= 4 || (isOneBlock && gap >= 3)) {
                // Tighter turns on max sprint jumps
                double clampedMin = Math.max(currentMinTurn, -25.0);
                double clampedMax = Math.min(currentMaxTurn, 25.0);
                if (clampedMin <= clampedMax) {
                    currentMinTurn = clampedMin;
                    currentMaxTurn = clampedMax;
                }
            }

            double turnDelta = (currentMinTurn >= currentMaxTurn)
                ? currentMinTurn
                : currentMinTurn + rng.nextDouble() * (currentMaxTurn - currentMinTurn);

            float candYaw = normalizeDegrees(lastYawDeg + (float) turnDelta);
            double headingRad = Math.toRadians(-candYaw);

            double dx = gap * Math.sin(headingRad);
            double dz = gap * Math.cos(headingRad);

            int newX = (int) Math.round(lastCenter.getX() + dx);
            int newY = lastCenter.getBlockY() + heightDelta;
            int newZ = (int) Math.round(lastCenter.getZ() + dz);

            // Ensure resulting distance is physically jumpable
            double actualDist = Math.hypot(newX - lastCenter.getX(), newZ - lastCenter.getZ());
            if (actualDist > maxDist && actualDist > 0.001) {
                double scale = (maxDist - 0.15) / actualDist;
                newX = (int) Math.round(lastCenter.getX() + (newX - lastCenter.getX()) * scale);
                newZ = (int) Math.round(lastCenter.getZ() + (newZ - lastCenter.getZ()) * scale);
            } else if (actualDist < 2.0 && actualDist > 0.001) {
                double scale = 2.0 / actualDist;
                newX = (int) Math.round(lastCenter.getX() + (newX - lastCenter.getX()) * scale);
                newZ = (int) Math.round(lastCenter.getZ() + (newZ - lastCenter.getZ()) * scale);
            }

            Location candCenter = new Location(world, newX, newY, newZ);

            // Check collision/overlap with recent platforms
            if (recentCenters != null && !recentCenters.isEmpty()) {
                boolean collides = false;
                for (Location rc : recentCenters) {
                    if (rc == null) continue;
                    double dSq = Math.pow(newX - rc.getX(), 2) + Math.pow(newZ - rc.getZ(), 2);
                    int dy = Math.abs(newY - rc.getBlockY());
                    if (dSq < 2.25 && dy <= 2) {
                        collides = true;
                        break;
                    }
                }
                if (collides && attempts < maxAttempts) {
                    continue; // retry another angle/gap
                }
            }

            center = candCenter;
            outgoingYaw = candYaw;
            break;
        }

        if (center == null) {
            center = new Location(world, lastCenter.getX(), lastCenter.getY(), lastCenter.getZ() + 3);
        }

        // ── 5. Build platform blocks ───────────────────────────────────────
        List<PlacedBlock> blocks = new ArrayList<>(buildBlocksForShape(shape, center, world, theme, rng, platformIndex));

        // ── 6. Obstacle block on top (never on 1x1 landing blocks) ─────────
        if (!isOneBlock && !theme.obstacleMaterials().isEmpty()
                && rng.nextDouble() < template.resolvedObstacleChance()) {
            // Place obstacle at perimeter edge (not center) so landing area is clear
            int ox = rng.nextBoolean() ? 1 : -1;
            int oz = rng.nextBoolean() ? 1 : -1;
            Location obstacleLoc = new Location(world,
                center.getBlockX() + ox, center.getBlockY() + 1, center.getBlockZ() + oz);
            Material obstacleMat = pickFrom(theme.obstacleMaterials(), rng);
            blocks.add(new PlacedBlock(obstacleLoc, obstacleMat, platformIndex));
        }

        // ── 7. Checkpoint ──────────────────────────────────────────────────
        Checkpoint checkpoint = buildPlatformCheckpoint(world, platformIndex, center, outgoingYaw, blocks);

        return new GeneratedPlatform(center, List.copyOf(blocks), checkpoint, platformIndex, outgoingYaw);
    }

    private static Checkpoint buildPlatformCheckpoint(
        @Nullable World world,
        int platformIndex,
        @NotNull Location center,
        float outgoingYaw,
        @NotNull List<PlacedBlock> blocks
    ) {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        int platY = center.getBlockY();
        for (PlacedBlock b : blocks) {
            if (b.location().getBlockY() == platY) {
                minX = Math.min(minX, b.location().getBlockX());
                maxX = Math.max(maxX, b.location().getBlockX());
                minZ = Math.min(minZ, b.location().getBlockZ());
                maxZ = Math.max(maxZ, b.location().getBlockZ());
            }
        }
        if (minX == Integer.MAX_VALUE) {
            minX = center.getBlockX();
            maxX = center.getBlockX();
            minZ = center.getBlockZ();
            maxZ = center.getBlockZ();
        }

        // Region tightly covers the standing volume directly above the platform surface:
        // [minX, maxX + 1] horizontally, and [platY + 1.0, platY + 3.0] vertically.
        Location regionMin = new Location(world, minX, platY + 1.0, minZ);
        Location regionMax = new Location(world, maxX + 1.0, platY + 3.0, maxZ + 1.0);

        Location spawnLoc = new Location(world,
            center.getBlockX() + 0.5, center.getBlockY() + 1.0, center.getBlockZ() + 0.5,
            outgoingYaw, 0f);

        return new CheckpointImpl(platformIndex, spawnLoc,
            new CourseRegionImpl(regionMin, regionMax));
    }

    // ── Platform shape generation ──────────────────────────────────────────

    private static boolean isSingleBlockShape(@NotNull String shape) {
        String s = shape.trim().toUpperCase(Locale.ROOT);
        return s.equals("1X1") || s.equals("1") || s.equals("SINGLE");
    }

    private static List<PlacedBlock> buildBlocksForShape(
        @NotNull String shape,
        @NotNull Location center,
        @NotNull World world,
        @NotNull GeneratorTheme theme,
        @NotNull Random rng,
        int platformIndex
    ) {
        List<PlacedBlock> blocks = new ArrayList<>();
        String s = shape.trim().toUpperCase(Locale.ROOT);
        switch (s) {
            case "1X1", "1", "SINGLE" -> {
                blocks.add(new PlacedBlock(center.clone(), pickPlatformMaterial(theme, rng), platformIndex));
            }
            case "2X2", "2" -> {
                for (int x = 0; x <= 1; x++) {
                    for (int z = 0; z <= 1; z++) {
                        Location loc = new Location(world,
                            center.getBlockX() + x, center.getBlockY(), center.getBlockZ() + z);
                        blocks.add(new PlacedBlock(loc, pickPlatformMaterial(theme, rng), platformIndex));
                    }
                }
            }
            case "3X3", "3" -> {
                for (int bx = -1; bx <= 1; bx++) {
                    for (int bz = -1; bz <= 1; bz++) {
                        Location loc = new Location(world,
                            center.getBlockX() + bx, center.getBlockY(), center.getBlockZ() + bz);
                        blocks.add(new PlacedBlock(loc, pickPlatformMaterial(theme, rng), platformIndex));
                    }
                }
            }
            case "2X1" -> {
                blocks.add(new PlacedBlock(center.clone(), pickPlatformMaterial(theme, rng), platformIndex));
                blocks.add(new PlacedBlock(new Location(world,
                    center.getBlockX(), center.getBlockY(), center.getBlockZ() + 1),
                    pickPlatformMaterial(theme, rng), platformIndex));
            }
            case "3X1" -> {
                for (int bz = -1; bz <= 1; bz++) {
                    Location loc = new Location(world,
                        center.getBlockX(), center.getBlockY(), center.getBlockZ() + bz);
                    blocks.add(new PlacedBlock(loc, pickPlatformMaterial(theme, rng), platformIndex));
                }
            }
            case "CROSS", "PLUS" -> {
                blocks.add(new PlacedBlock(center.clone(), pickPlatformMaterial(theme, rng), platformIndex));
                blocks.add(new PlacedBlock(new Location(world, center.getBlockX() + 1, center.getBlockY(), center.getBlockZ()),
                    pickPlatformMaterial(theme, rng), platformIndex));
                blocks.add(new PlacedBlock(new Location(world, center.getBlockX() - 1, center.getBlockY(), center.getBlockZ()),
                    pickPlatformMaterial(theme, rng), platformIndex));
                blocks.add(new PlacedBlock(new Location(world, center.getBlockX(), center.getBlockY(), center.getBlockZ() + 1),
                    pickPlatformMaterial(theme, rng), platformIndex));
                blocks.add(new PlacedBlock(new Location(world, center.getBlockX(), center.getBlockY(), center.getBlockZ() - 1),
                    pickPlatformMaterial(theme, rng), platformIndex));
            }
            default -> {
                blocks.add(new PlacedBlock(center.clone(), pickPlatformMaterial(theme, rng), platformIndex));
            }
        }
        return blocks;
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
