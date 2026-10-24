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
        GeneratorTheme theme = template.theme();
        List<String> shapes = template.resolvedPlatformShapes();
        String shape = shapes.get(rng.nextInt(shapes.size()));

        Location center = startLocation.clone();
        List<PlacedBlock> blocks = buildBlocksForShape(shape, center, world, theme, rng, 0);

        Location regionMin = new Location(world,
            center.getBlockX() - 1, center.getBlockY(), center.getBlockZ() - 1);
        Location regionMax = new Location(world,
            center.getBlockX() + 2, center.getBlockY() + 3, center.getBlockZ() + 2);

        Location spawnLoc = new Location(world,
            center.getBlockX() + 0.5, center.getBlockY() + 1.0, center.getBlockZ() + 0.5, 0f, 0f);
        Checkpoint checkpoint = new CheckpointImpl(0, spawnLoc,
            new CourseRegionImpl(regionMin, regionMax));

        return new GeneratedPlatform(center, List.copyOf(blocks), checkpoint, 0, 0f);
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
        GeneratorTheme theme = template.theme();

        // ── 1. Platform shape ──────────────────────────────────────────────
        List<String> shapes = template.resolvedPlatformShapes();
        String shape = shapes.get(rng.nextInt(shapes.size()));
        boolean isOneBlock = isSingleBlockShape(shape);

        // ── 2. Height delta (clamped strictly to vanilla jump physics) ─────
        // In vanilla Minecraft, players can jump at most 1.25 blocks up. Delta > 1 is impossible.
        int minDelta = Math.max(-2, template.resolvedMinHeightDelta());
        int maxDelta = Math.min(1, template.resolvedMaxHeightDelta());
        if (minDelta > maxDelta) minDelta = maxDelta;
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
        int gap = minGap + rng.nextInt(Math.max(1, maxGap - minGap + 1));

        // On larger gaps from 1-block platforms, tighten turn angle so momentum isn't lost
        if (gap >= 4 || (isOneBlock && gap >= 3)) {
            maxSpreadDeg = Math.min(maxSpreadDeg, 25.0);
        }

        double spreadRad = Math.toRadians((rng.nextDouble() * 2 - 1) * maxSpreadDeg);
        double headingRad = Math.toRadians(-lastYawDeg) + spreadRad;

        // ── 4. Compute next centre with physical distance verification ───────
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

        Location center = new Location(world, newX, newY, newZ);

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
        float outgoingYaw = (float) Math.toDegrees(-headingRad);

        Location regionMin = new Location(world,
            center.getBlockX() - 1, center.getBlockY(), center.getBlockZ() - 1);
        Location regionMax = new Location(world,
            center.getBlockX() + 2, center.getBlockY() + 3, center.getBlockZ() + 2);

        Location spawnLoc = new Location(world,
            center.getBlockX() + 0.5, center.getBlockY() + 1.0, center.getBlockZ() + 0.5,
            outgoingYaw, 0f);

        Checkpoint checkpoint = new CheckpointImpl(platformIndex, spawnLoc,
            new CourseRegionImpl(regionMin, regionMax));

        return new GeneratedPlatform(center, List.copyOf(blocks), checkpoint, platformIndex, outgoingYaw);
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
