package me.usainsrht.basicparkour.core.generator;

import me.usainsrht.basicparkour.api.course.Checkpoint;
import me.usainsrht.basicparkour.api.generator.GeneratorDifficulty;
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
     * Computes the maximum physically jumpable horizontal Euclidean distance (center-to-center)
     * in vanilla Minecraft without status effects for a given height delta and turning angle.
     *
     * <p>Vanilla physics constants:
     * <ul>
     *   <li>+1 height: player has ~8 ticks before falling below y=1.0. Max displacement is ~2.8m,
     *       allowing at most center distance 4.0 (3 air blocks) in a straight line.</li>
     *   <li>Flat jump: player has ~12 ticks. Max displacement is ~3.8m,
     *       allowing center distance 5.0 (the classic 4-block jump) in a straight line.</li>
     *   <li>Downward jumps: player has increased air time (14-18 ticks), allowing center distances
     *       up to 5.4 (-1), 5.8 (-2), and 6.2 (-3) in straight lines.</li>
     *   <li>Turning angles redirect forward sprint momentum; max jumpable distance decreases
     *       progressively as turn angle widens.</li>
     * </ul>
     */
    public static double getMaxAllowedDistance(int heightDelta, double absTurnDeg) {
        if (heightDelta >= 1) {
            if (absTurnDeg <= 12.0) return 4.05; // 3 air blocks (straight +1 sprint jump)
            if (absTurnDeg <= 25.0) return 3.35; // 2 air blocks
            if (absTurnDeg <= 40.0) return 2.75; // 1-2 air blocks
            return 2.20; // 1 air block
        } else if (heightDelta == 0) {
            if (absTurnDeg <= 15.0) return 5.08; // 4 air blocks (classic 4-block jump!)
            if (absTurnDeg <= 30.0) return 4.35; // 3 air blocks
            if (absTurnDeg <= 45.0) return 3.70; // 2-3 air blocks
            if (absTurnDeg <= 65.0) return 3.10; // 2 air blocks
            return 2.45; // 1 air block (sharp corner jump)
        } else if (heightDelta == -1) {
            if (absTurnDeg <= 15.0) return 5.45;
            if (absTurnDeg <= 30.0) return 4.70;
            if (absTurnDeg <= 45.0) return 4.10;
            if (absTurnDeg <= 65.0) return 3.40;
            return 2.60;
        } else if (heightDelta == -2) {
            if (absTurnDeg <= 15.0) return 5.85;
            if (absTurnDeg <= 30.0) return 5.10;
            if (absTurnDeg <= 45.0) return 4.30;
            if (absTurnDeg <= 65.0) return 3.60;
            return 2.80;
        } else {
            // heightDelta <= -3
            if (absTurnDeg <= 15.0) return 6.20;
            if (absTurnDeg <= 30.0) return 5.30;
            if (absTurnDeg <= 45.0) return 4.50;
            if (absTurnDeg <= 65.0) return 3.70;
            return 2.90;
        }
    }

    /**
     * Minimum safe horizontal Euclidean distance (center-to-center) to prevent platform collisions.
     */
    public static double getMinAllowedDistance(int heightDelta) {
        return heightDelta == 0 ? 2.0 : 1.8;
    }

    /**
     * Generates a single subsequent platform, chained from {@code lastCenter} with directional cone,
     * optional corridor boundary, height tethering, overlap/collision avoidance, and full physics validation.
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
        GeneratorDifficulty difficulty = template.difficulty();

        // ── 1. Platform shape (weighted by difficulty if choices are available) ───
        List<String> shapes = template.resolvedPlatformShapes();
        String shape = selectShapeForDifficulty(shapes, difficulty, rng);
        boolean isOneBlock = isSingleBlockShape(shape);

        // ── 2. Height delta selection (clamped strictly to vanilla physics + tethering) ───
        // In vanilla Minecraft, maximum jump height is 1.25 blocks (+1 elevation). Delta > 1 is impossible.
        int minDelta = Math.max(-3, template.resolvedMinHeightDelta());
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

        int heightDelta = selectHeightDelta(minDelta, maxDelta, difficulty, rng);

        // ── 3. Gap boundaries based on height delta & difficulty ─────────────
        int minGap = Math.max(2, template.resolvedMinGap());
        int maxGap = Math.max(minGap, template.resolvedMaxGap());

        // Vanilla physics limit: +1 jump CANNOT exceed gap 4 (3 air blocks)
        if (heightDelta >= 1) {
            maxGap = Math.min(maxGap, 4);
        } else if (heightDelta <= -2 && (difficulty == GeneratorDifficulty.EXPERT || maxGap >= 5)) {
            // Downward jumps have increased clearance; expert drops can extend to gap 6
            maxGap = Math.min(maxGap + (heightDelta <= -3 ? 2 : 1), 6);
        }
        if (minGap > maxGap) minGap = maxGap;

        // ── 3b. Directional Cone & Corridor constraints ───────────────────────
        double directionCone = template.resolvedDirectionCone();
        float effectiveBaseYaw = template.resolvedDirectionYaw(baseYawDeg);
        double baseMaxTurn = template.resolvedMaxTurnAngle();

        // ── 4. Candidate generation with physics feasibility & overlap verification ─
        Location center = null;
        float outgoingYaw = lastYawDeg;
        int attempts = 0;
        int maxAttempts = (recentCenters != null && !recentCenters.isEmpty()) ? 12 : 1;

        while (attempts < maxAttempts) {
            attempts++;

            // Target gap tailored to difficulty distribution
            int targetGap = selectGapForDifficulty(minGap, maxGap, heightDelta, difficulty, rng);

            // Turn angle spread is constrained by jump distance and verticality to ensure jumpability
            double allowedSpread = baseMaxTurn;
            if (targetGap >= 5) {
                // Max sprint jumps (e.g. 4-block flat jump) require straight-line sprint momentum
                allowedSpread = Math.min(allowedSpread, 12.0);
            } else if (targetGap == 4 && heightDelta >= 1) {
                // +1 jump with 3 air blocks requires straight trajectory
                allowedSpread = Math.min(allowedSpread, 10.0);
            } else if (targetGap == 4) {
                // 3 air blocks flat
                allowedSpread = Math.min(allowedSpread, 25.0);
            } else if (heightDelta >= 1) {
                allowedSpread = Math.min(allowedSpread, 35.0);
            }

            double minTurn = -allowedSpread;
            double maxTurn = allowedSpread;

            if (directionCone < 360.0) {
                double halfCone = Math.max(0.0, directionCone / 2.0);
                float currentDeltaFromBase = normalizeDegrees(lastYawDeg - effectiveBaseYaw);

                minTurn = Math.max(-allowedSpread, -halfCone - currentDeltaFromBase);
                maxTurn = Math.min(allowedSpread, halfCone - currentDeltaFromBase);

                if (minTurn > maxTurn) {
                    if (currentDeltaFromBase > 0) {
                        minTurn = -allowedSpread;
                        maxTurn = Math.max(-allowedSpread, -halfCone - currentDeltaFromBase);
                    } else {
                        maxTurn = allowedSpread;
                        minTurn = Math.min(allowedSpread, halfCone - currentDeltaFromBase);
                    }
                    if (minTurn > maxTurn) {
                        double steer = -Math.signum(currentDeltaFromBase) * allowedSpread;
                        minTurn = steer;
                        maxTurn = steer;
                    }
                }
            }

            // Lateral corridor constraint
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
                    minTurn = Math.max(minTurn, -currentDeltaFromBase);
                } else if (lateralOffset < -halfCorridor * 0.7) {
                    maxTurn = Math.min(maxTurn, -currentDeltaFromBase);
                }
                if (minTurn > maxTurn) {
                    double mid = (minTurn + maxTurn) / 2.0;
                    minTurn = mid;
                    maxTurn = mid;
                }
            }

            // Turn angle sampling with rhythm awareness
            double turnDelta;
            if (minTurn >= maxTurn) {
                turnDelta = minTurn;
            } else {
                turnDelta = sampleTurnDelta(minTurn, maxTurn, difficulty, rng);
            }

            float candYaw = normalizeDegrees(lastYawDeg + (float) turnDelta);
            double headingRad = Math.toRadians(-candYaw);

            double dx = targetGap * Math.sin(headingRad);
            double dz = targetGap * Math.cos(headingRad);

            int newX = (int) Math.round(lastCenter.getX() + dx);
            int newY = lastCenter.getBlockY() + heightDelta;
            int newZ = (int) Math.round(lastCenter.getZ() + dz);

            // ── Enforce strict vanilla jump physics limits ───────────────────
            double actualDist = Math.hypot(newX - lastCenter.getX(), newZ - lastCenter.getZ());
            double maxAllowed = getMaxAllowedDistance(heightDelta, Math.abs(turnDelta));
            double minAllowed = getMinAllowedDistance(heightDelta);

            if (actualDist > maxAllowed && actualDist > 0.001) {
                double scale = (maxAllowed - 0.03) / actualDist;
                newX = (int) Math.round(lastCenter.getX() + (newX - lastCenter.getX()) * scale);
                newZ = (int) Math.round(lastCenter.getZ() + (newZ - lastCenter.getZ()) * scale);
                actualDist = Math.hypot(newX - lastCenter.getX(), newZ - lastCenter.getZ());

                while (actualDist > maxAllowed) {
                    int diffX = newX - lastCenter.getBlockX();
                    int diffZ = newZ - lastCenter.getBlockZ();
                    if (Math.abs(diffX) >= Math.abs(diffZ) && Math.abs(diffX) > 0) {
                        newX -= Integer.signum(diffX);
                    } else if (Math.abs(diffZ) > 0) {
                        newZ -= Integer.signum(diffZ);
                    } else {
                        break;
                    }
                    actualDist = Math.hypot(newX - lastCenter.getX(), newZ - lastCenter.getZ());
                }
            }

            if (actualDist < minAllowed && actualDist > 0.001) {
                double scale = minAllowed / actualDist;
                newX = (int) Math.round(lastCenter.getX() + (newX - lastCenter.getX()) * scale);
                newZ = (int) Math.round(lastCenter.getZ() + (newZ - lastCenter.getZ()) * scale);
            }

            // Verify forward movement when constrained by a directional cone (< 180 degrees)
            if (directionCone <= 180.0) {
                double radBase = Math.toRadians(-effectiveBaseYaw);
                double fwdX = Math.sin(radBase);
                double fwdZ = Math.cos(radBase);
                double stepFwd = (newX - lastCenter.getX()) * fwdX + (newZ - lastCenter.getZ()) * fwdZ;
                if (stepFwd <= 0.05 && attempts < maxAttempts) {
                    continue; // Ensure positive forward progress along base direction
                }
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
                    continue;
                }
            }

            center = candCenter;
            outgoingYaw = candYaw;
            break;
        }

        if (center == null) {
            double radBase = Math.toRadians(-effectiveBaseYaw);
            int safeStepX = (int) Math.round(3.0 * Math.sin(radBase));
            int safeStepZ = (int) Math.round(3.0 * Math.cos(radBase));
            center = new Location(world, lastCenter.getX() + safeStepX, lastCenter.getY(), lastCenter.getZ() + safeStepZ);
            outgoingYaw = effectiveBaseYaw;
        }

        // ── 5. Build platform blocks ───────────────────────────────────────
        List<PlacedBlock> blocks = new ArrayList<>(buildBlocksForShape(shape, center, world, theme, rng, platformIndex));

        // ── 6. Obstacles (perimeter blocks or head-hitters) ─────────────────
        if (!theme.obstacleMaterials().isEmpty() && rng.nextDouble() < template.resolvedObstacleChance()) {
            if (!isOneBlock) {
                // Multi-block platform: place obstacle on perimeter edge
                int ox = rng.nextBoolean() ? 1 : -1;
                int oz = rng.nextBoolean() ? 1 : -1;
                Location obstacleLoc = new Location(world,
                    center.getBlockX() + ox, center.getBlockY() + 1, center.getBlockZ() + oz);
                Material obstacleMat = pickFrom(theme.obstacleMaterials(), rng);
                blocks.add(new PlacedBlock(obstacleLoc, obstacleMat, platformIndex));
            } else if (difficulty == GeneratorDifficulty.HARD || difficulty == GeneratorDifficulty.EXPERT) {
                // Single 1x1 block in Hard/Expert: optional head-hitter ceiling obstacle 2 blocks above
                if (heightDelta <= 0 && rng.nextDouble() < 0.35) {
                    Location ceilingLoc = new Location(world,
                        center.getBlockX(), center.getBlockY() + 2, center.getBlockZ());
                    Material obstacleMat = pickFrom(theme.obstacleMaterials(), rng);
                    blocks.add(new PlacedBlock(ceilingLoc, obstacleMat, platformIndex));
                }
            }
        }

        // ── 7. Checkpoint ──────────────────────────────────────────────────
        Checkpoint checkpoint = buildPlatformCheckpoint(world, platformIndex, center, outgoingYaw, blocks);

        return new GeneratedPlatform(center, List.copyOf(blocks), checkpoint, platformIndex, outgoingYaw);
    }

    // ── Helper methods for difficulty-driven generation ─────────────────────

    private static String selectShapeForDifficulty(
        @NotNull List<String> shapes,
        @NotNull GeneratorDifficulty difficulty,
        @NotNull Random rng
    ) {
        if (shapes.isEmpty()) return "1x1";
        if (shapes.size() == 1) return shapes.get(0);

        return switch (difficulty) {
            case EASY -> {
                // Bias heavily towards larger, forgiving shapes
                List<String> large = shapes.stream().filter(s -> !isSingleBlockShape(s)).toList();
                yield !large.isEmpty() && rng.nextDouble() < 0.85
                    ? large.get(rng.nextInt(large.size()))
                    : shapes.get(rng.nextInt(shapes.size()));
            }
            case MEDIUM -> shapes.get(rng.nextInt(shapes.size()));
            case HARD, EXPERT -> {
                // Bias heavily towards 1x1 precision landing blocks
                List<String> single = shapes.stream().filter(JumpToJumpAlgorithm::isSingleBlockShape).toList();
                yield !single.isEmpty() && rng.nextDouble() < 0.85
                    ? single.get(rng.nextInt(single.size()))
                    : shapes.get(rng.nextInt(shapes.size()));
            }
        };
    }

    private static int selectHeightDelta(
        int minDelta, int maxDelta,
        @NotNull GeneratorDifficulty difficulty,
        @NotNull Random rng
    ) {
        if (minDelta >= maxDelta) return minDelta;

        double roll = rng.nextDouble();
        int delta = 0;
        switch (difficulty) {
            case EASY -> {
                // 80% flat, 20% down 1
                delta = roll < 0.80 ? 0 : -1;
            }
            case MEDIUM -> {
                // 55% flat, 25% up 1, 20% down 1
                if (roll < 0.55) delta = 0;
                else if (roll < 0.80) delta = 1;
                else delta = -1;
            }
            case HARD -> {
                // 40% flat, 25% up 1, 20% down 1, 15% down 2
                if (roll < 0.40) delta = 0;
                else if (roll < 0.65) delta = 1;
                else if (roll < 0.85) delta = -1;
                else delta = -2;
            }
            case EXPERT -> {
                // 35% flat, 25% up 1, 15% down 1, 15% down 2, 10% down 3
                if (roll < 0.35) delta = 0;
                else if (roll < 0.60) delta = 1;
                else if (roll < 0.75) delta = -1;
                else if (roll < 0.90) delta = -2;
                else delta = -3;
            }
        }
        return Math.max(minDelta, Math.min(maxDelta, delta));
    }

    private static int selectGapForDifficulty(
        int minGap, int maxGap, int heightDelta,
        @NotNull GeneratorDifficulty difficulty,
        @NotNull Random rng
    ) {
        if (minGap >= maxGap) return minGap;

        double roll = rng.nextDouble();
        int gap = minGap;
        switch (difficulty) {
            case EASY -> {
                // 65% gap 2 (1 air block), 35% gap 3 (2 air blocks)
                gap = roll < 0.65 ? 2 : 3;
            }
            case MEDIUM -> {
                // 25% gap 2, 45% gap 3, 30% gap 4
                if (roll < 0.25) gap = 2;
                else if (roll < 0.70) gap = 3;
                else gap = 4;
            }
            case HARD -> {
                // 30% gap 3, 45% gap 4, 25% gap 5
                if (roll < 0.30) gap = 3;
                else if (roll < 0.75) gap = 4;
                else gap = 5;
            }
            case EXPERT -> {
                // 20% gap 3, 40% gap 4, 35% gap 5, 5% gap 6 (on drops)
                if (roll < 0.20) gap = 3;
                else if (roll < 0.60) gap = 4;
                else if (roll < 0.95) gap = 5;
                else gap = 6;
            }
        }
        return Math.max(minGap, Math.min(maxGap, gap));
    }

    private static double sampleTurnDelta(
        double minTurn, double maxTurn,
        @NotNull GeneratorDifficulty difficulty,
        @NotNull Random rng
    ) {
        if (minTurn >= maxTurn) return minTurn;

        if (difficulty == GeneratorDifficulty.EASY) {
            // Easy parkour: 55% chance of nearly straight forward jump
            if (rng.nextDouble() < 0.55 && minTurn <= 0 && maxTurn >= 0) {
                double tightMin = Math.max(minTurn, -5.0);
                double tightMax = Math.min(maxTurn, 5.0);
                return tightMin + rng.nextDouble() * (tightMax - tightMin);
            }
        }

        return minTurn + rng.nextDouble() * (maxTurn - minTurn);
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
