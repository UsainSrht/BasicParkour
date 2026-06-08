package me.usainsrht.basicparkour.core.generator;

import me.usainsrht.basicparkour.api.course.Checkpoint;
import me.usainsrht.basicparkour.api.generator.*;
import me.usainsrht.basicparkour.api.session.ParkourSession;
import me.usainsrht.basicparkour.core.BasicParkourPlugin;
import me.usainsrht.basicparkour.core.course.CourseRegionImpl;
import me.usainsrht.basicparkour.core.scheduling.FoliaScheduler;
import me.usainsrht.basicparkour.core.session.SessionManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * Core implementation of {@link GeneratorManager}.
 *
 * <h2>Folia-safe generation pipeline</h2>
 * <ol>
 *   <li><strong>Async math phase</strong> — {@link JumpToJumpAlgorithm} computes all platform
 *       positions and block lists (no Bukkit world reads). Safe on any thread.</li>
 *   <li><strong>Region-thread placement phase</strong> — for each unique chunk, a
 *       {@link FoliaScheduler#runAtLocation} call reads + captures the original block material,
 *       then sets the desired material. One {@link CompletableFuture} per chunk.</li>
 *   <li><strong>Session start</strong> — once all chunks are done, the session is started on
 *       the player's entity thread.</li>
 *   <li><strong>Sliding window</strong> (optional) — a global repeating task generates new
 *       platforms ahead and tears down old ones behind the player, dispatching each chunk
 *       operation on its own region thread.</li>
 *   <li><strong>Cleanup</strong> — after the configured delay, blocks are restored to their
 *       original materials, per-chunk, on region threads.</li>
 * </ol>
 */
public final class GeneratorManagerImpl implements GeneratorManager {

    private static final String COURSE_ID_PREFIX = "generated_";

    private final BasicParkourPlugin plugin;
    private final FoliaScheduler scheduler;
    private final SessionManager sessionManager;

    private final ConcurrentHashMap<String, GeneratorTemplate> templates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, GeneratedCourseInstanceImpl> instances = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, String> playerCourseMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SlotAllocator> slotAllocators = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SlotAllocatorKey> instanceSlotKeys = new ConcurrentHashMap<>();
    private final AtomicInteger courseIdSeq = new AtomicInteger(0);

    public GeneratorManagerImpl(
        @NotNull BasicParkourPlugin plugin,
        @NotNull FoliaScheduler scheduler,
        @NotNull SessionManager sessionManager
    ) {
        this.plugin         = plugin;
        this.scheduler      = scheduler;
        this.sessionManager = sessionManager;
    }

    // ── Template registry ──────────────────────────────────────────────────

    public void loadTemplates(@NotNull List<GeneratorTemplate> loaded) {
        loaded.forEach(t -> templates.put(t.id().toLowerCase(), t));
        plugin.getLogger().info("[Generator] Loaded " + templates.size() + " generator template(s).");
    }

    @Override @NotNull public Collection<GeneratorTemplate> getTemplates() {
        return Collections.unmodifiableCollection(templates.values());
    }

    @Override @NotNull public Optional<GeneratorTemplate> getTemplate(@NotNull String id) {
        return Optional.ofNullable(templates.get(id.toLowerCase()));
    }

    // ── Instance tracking ──────────────────────────────────────────────────

    @Override @NotNull public Collection<GeneratedCourseInstance> getActiveInstances() {
        return Collections.unmodifiableCollection(instances.values());
    }

    @Override @NotNull public Optional<GeneratedCourseInstance> getInstance(@NotNull Player player) {
        String courseId = playerCourseMap.get(player.getUniqueId());
        if (courseId == null) return Optional.empty();
        return Optional.ofNullable(instances.get(courseId));
    }

    // ── Generation ─────────────────────────────────────────────────────────

    @Override @NotNull
    public CompletableFuture<@Nullable ParkourSession> generate(
        @NotNull Player player, @NotNull GeneratorTemplate template
    ) {
        long seed = template.seed() != null ? template.seed() : new Random().nextLong();
        return generate(player, template, seed);
    }

    @Override @NotNull
    public CompletableFuture<@Nullable ParkourSession> generate(
        @NotNull Player player, @NotNull GeneratorTemplate template, long customSeed
    ) {
        if (sessionManager.getSession(player).isPresent()) {
            player.sendMessage(net.kyori.adventure.text.Component.text(
                "§cYou are already in a parkour session."));
            return CompletableFuture.completedFuture(null);
        }
        if (playerCourseMap.containsKey(player.getUniqueId())) {
            return CompletableFuture.completedFuture(null);
        }

        // ── Phase 1: async math (no Bukkit world access) ───────────────────
        return CompletableFuture.supplyAsync(() -> {
            try {
                return doAsyncMath(player, template, customSeed);
            } catch (Exception ex) {
                plugin.getLogger().log(Level.SEVERE,
                    "[Generator] Math phase failed for " + player.getName(), ex);
                return null;
            }
        })
        // ── Phase 2: place blocks on region threads ────────────────────────
        .thenCompose(data -> {
            if (data == null) return CompletableFuture.completedFuture(null);
            return placeBlocksAndStartSession(player, template, data);
        })
        .exceptionally(ex -> {
            plugin.getLogger().log(Level.SEVERE, "[Generator] Unhandled error during generation", ex);
            return null;
        });
    }

    // ── Phase 1: pure async math ───────────────────────────────────────────

    /**
     * Intermediate data holder — all computed async, zero Bukkit world reads.
     */
    private record GenerationData(
        @NotNull World world,
        @NotNull Location startLoc,
        @NotNull List<JumpToJumpAlgorithm.GeneratedPlatform> platforms,
        @NotNull String courseId,
        long seed,
        @NotNull SlotAllocatorResult slotResult
    ) {}

    private record SlotAllocatorResult(int slotId, @NotNull String worldName) {}

    @Nullable
    private GenerationData doAsyncMath(
        @NotNull Player player,
        @NotNull GeneratorTemplate template,
        long seed
    ) {
        // Resolve world — Bukkit.getWorld() is safe from async (name lookup only)
        World world = resolveWorld(template);
        if (world == null) {
            plugin.getLogger().warning("[Generator] World not found for template '" + template.id() + "'");
            return null;
        }

        // Resolve start location + slot
        SlotAllocatorResult slotResult;
        Location startLoc;

        if (template.entryMode() == EntryMode.DEDICATED_WORLD) {
            int maxGap      = template.resolvedMaxGap();
            int maxPlatforms = template.lengthType() == LengthType.FIXED
                ? template.fixedLength()
                : template.slidingWindowAhead() * 3;
            int slotHalfWidth = maxGap * maxPlatforms
                + plugin.getConfig().getInt("generator.dedicated-world-padding", 64);
            int radius   = template.dedicatedWorldRadius();
            int fixedY   = plugin.getConfig().getInt("generator.dedicated-world-y", 80);
            int padding  = plugin.getConfig().getInt("generator.dedicated-world-padding", 64);

            SlotAllocator allocator = slotAllocators.computeIfAbsent(world.getName(),
                k -> new SlotAllocator(radius, slotHalfWidth, padding, fixedY));

            SlotAllocator.AllocatedSlot slot = allocator.allocate(world);
            if (slot == null) {
                plugin.getLogger().warning("[Generator] No slot available for template '" + template.id() + "'");
                return null;
            }
            slotResult = new SlotAllocatorResult(slot.slotId(), world.getName());
            startLoc   = slot.origin();
        } else {
            // MARKER mode — read from config (no world API needed)
            org.bukkit.configuration.ConfigurationSection sec =
                plugin.getConfig().getConfigurationSection("generator.entry-points." + template.id());
            if (sec == null) {
                plugin.getLogger().warning("[Generator] No marker for template '"
                    + template.id() + "'. Use /basicparkour setentry.");
                return null;
            }
            double x = sec.getDouble("x"), y = sec.getDouble("y"), z = sec.getDouble("z");
            startLoc   = new Location(world, x, y, z);
            slotResult = new SlotAllocatorResult(-1, world.getName());
        }

        // ── Pure math: generate all platforms ──────────────────────────────
        Random rng = new Random(seed);
        int platformCount = template.lengthType() == LengthType.FIXED
            ? template.fixedLength()
            : template.slidingWindowAhead() * 2;

        List<JumpToJumpAlgorithm.GeneratedPlatform> platforms = new ArrayList<>();

        JumpToJumpAlgorithm.GeneratedPlatform first =
            JumpToJumpAlgorithm.generateStart(template, world, startLoc, rng);
        platforms.add(first);

        Location lastCenter = first.centerLocation();
        float lastYaw       = first.outgoingYawDeg();

        for (int i = 1; i < platformCount; i++) {
            JumpToJumpAlgorithm.GeneratedPlatform p =
                JumpToJumpAlgorithm.generateNext(template, world, lastCenter, lastYaw, i, rng);
            platforms.add(p);
            lastCenter = p.centerLocation();
            lastYaw    = p.outgoingYawDeg();
        }

        String courseId = COURSE_ID_PREFIX + courseIdSeq.getAndIncrement();
        return new GenerationData(world, startLoc, platforms, courseId, seed, slotResult);
    }

    // ── Phase 2: place blocks on region threads, then start session ────────

    @NotNull
    private CompletableFuture<@Nullable ParkourSession> placeBlocksAndStartSession(
        @NotNull Player player,
        @NotNull GeneratorTemplate template,
        @NotNull GenerationData data
    ) {
        // Build course + instance from async data (no world reads needed)
        List<Checkpoint> checkpoints = new ArrayList<>();
        for (JumpToJumpAlgorithm.GeneratedPlatform p : data.platforms()) {
            checkpoints.add(p.checkpoint());
        }

        Location fc = data.platforms().get(0).centerLocation();
        CourseRegionImpl startRegion = new CourseRegionImpl(
            fc.clone().add(-2, 0, -2), fc.clone().add(2, 3, 2));

        CourseRegionImpl endRegion;
        if (template.lengthType() == LengthType.FIXED) {
            Location lc = data.platforms().get(data.platforms().size() - 1).centerLocation();
            endRegion = new CourseRegionImpl(lc.clone().add(-2, 0, -2), lc.clone().add(2, 3, 2));
        } else {
            Location dummy = new Location(data.world(), 0, data.world().getMinHeight() - 100, 0);
            endRegion = new CourseRegionImpl(dummy, dummy);
        }

        GeneratedParkourCourseImpl course = new GeneratedParkourCourseImpl(
            data.courseId(), template, data.seed(), data.world().getName(),
            startRegion, endRegion, checkpoints);

        GeneratedCourseInstanceImpl instance = new GeneratedCourseInstanceImpl(course, data.seed());

        // Stage all blocks into the instance's placed list (still async — no world reads)
        for (JumpToJumpAlgorithm.GeneratedPlatform platform : data.platforms()) {
            for (PlacedBlock pb : platform.placedBlocks()) {
                instance.placedBlocks.add(pb);
            }
            instance.placedPlatformCount.incrementAndGet();
        }

        // ── Dispatch one placement task per unique chunk, on its region thread ──
        // Group blocks by chunk key so we make one runAtLocation call per chunk
        Map<String, List<PlacedBlock>> byChunk = new LinkedHashMap<>();
        synchronized (instance.placedBlocks) {
            for (PlacedBlock pb : instance.placedBlocks) {
                String chunkKey = pb.location().getWorld().getName() + ","
                    + pb.location().getChunk().getX() + ","
                    + pb.location().getChunk().getZ();
                byChunk.computeIfAbsent(chunkKey, k -> new ArrayList<>()).add(pb);
            }
        }

        List<CompletableFuture<Void>> placeFutures = new ArrayList<>();
        for (Map.Entry<String, List<PlacedBlock>> entry : byChunk.entrySet()) {
            List<PlacedBlock> chunk = entry.getValue();
            CompletableFuture<Void> f = new CompletableFuture<>();
            // Use the first block's location to identify the region
            scheduler.runAtLocation(chunk.get(0).location(), () -> {
                for (PlacedBlock pb : chunk) {
                    instance.placeBlock(pb);   // reads original + sets desired — on region thread
                }
                f.complete(null);
            });
            placeFutures.add(f);
        }

        // Record slot key for later release
        if (data.slotResult().slotId() >= 0) {
            instanceSlotKeys.put(data.courseId(),
                new SlotAllocatorKey(data.slotResult().worldName(), data.slotResult().slotId()));
        }

        // ── Wait for all placements, then start session on entity thread ───
        final Location finalLastCenter = data.platforms().get(data.platforms().size() - 1).centerLocation().clone();
        final float    finalLastYaw    = data.platforms().get(data.platforms().size() - 1).outgoingYawDeg();

        return CompletableFuture.allOf(placeFutures.toArray(new CompletableFuture[0]))
            .thenCompose(_v -> {
                CompletableFuture<ParkourSession> sessionFuture = new CompletableFuture<>();

                // Register course
                try {
                    plugin.getBasicParkourAPI().getCourseRegistry().registerCourse(course);
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().warning("[Generator] Course ID collision: " + data.courseId());
                    sessionFuture.complete(null);
                    return sessionFuture;
                }

                instances.put(data.courseId(), instance);
                instance.addPlayer(player);
                playerCourseMap.put(player.getUniqueId(), data.courseId());

                // Teleport + start session on entity thread
                scheduler.runOnEntity(player, () -> {
                    Location spawn = data.platforms().get(0).checkpoint().getSpawnLocation();
                    player.teleportAsync(spawn).thenRun(() ->
                        scheduler.runOnEntity(player, () -> {
                            ParkourSession session = sessionManager.startSession(player, course);
                            sessionFuture.complete(session);

                            if (template.blockMode() == BlockMode.SLIDING_WINDOW) {
                                // Separate rng for sliding window so it continues from where math left off
                                Random slidingRng = new Random(data.seed());
                                // Fast-forward past already-generated platforms
                                for (int i = 0; i < data.platforms().size() - 1; i++) {
                                    JumpToJumpAlgorithm.generateNext(template, data.world(),
                                        new Location(data.world(), 0, 0, 0), 0, i, slidingRng);
                                }
                                attachSlidingWindow(instance, template, player, data.world(),
                                    slidingRng, finalLastCenter, finalLastYaw);
                            }
                        })
                    );
                });

                return sessionFuture;
            });
    }

    // ── Sliding window ─────────────────────────────────────────────────────

    private void attachSlidingWindow(
        @NotNull GeneratedCourseInstanceImpl instance,
        @NotNull GeneratorTemplate template,
        @NotNull Player player,
        @NotNull World world,
        @NotNull Random rng,
        @NotNull Location initialLastCenter,
        float initialLastYaw
    ) {
        Location[] lastCenter = {initialLastCenter.clone()};
        float[]    lastYaw    = {initialLastYaw};
        int[]      nextIndex  = {instance.placedPlatformCount.get()};

        FoliaScheduler.RepeatingTask task = scheduler.runRepeatingGlobal(() -> {
            if (!instance.isActive()) return;
            if (!player.isOnline()) return;

            Location playerLoc = player.getLocation();
            int playerPlatformIndex = getPlayerPlatformIndex(instance, playerLoc);

            // Remove platforms that are sufficiently behind the player
            int removeUpTo = playerPlatformIndex - template.slidingWindowAhead();
            if (removeUpTo > instance.slidingWindowHead) {
                final int removeTo = removeUpTo - 1;
                // Dispatch per-chunk restoration on region threads
                Map<String, List<PlacedBlock>> toRemoveByChunk = new LinkedHashMap<>();
                synchronized (instance.placedBlocks) {
                    for (PlacedBlock pb : instance.placedBlocks) {
                        if (pb.platformIndex() <= removeTo) {
                            String key = pb.location().getWorld().getName() + ","
                                + pb.location().getChunk().getX() + ","
                                + pb.location().getChunk().getZ();
                            toRemoveByChunk.computeIfAbsent(key, k -> new ArrayList<>()).add(pb);
                        }
                    }
                }
                for (List<PlacedBlock> chunk : toRemoveByChunk.values()) {
                    scheduler.runAtLocation(chunk.get(0).location(), () ->
                        instance.restoreBlocksUpTo(removeTo, plugin.getLogger()));
                }
                instance.slidingWindowHead = removeUpTo;
            }

            // Generate new platform if player is within range of the last placed
            int aheadNeeded = playerPlatformIndex + template.slidingWindowAhead();
            if (aheadNeeded > nextIndex[0] - 1) {
                // ── Pure math (on global thread, but no world reads) ────────
                JumpToJumpAlgorithm.GeneratedPlatform newPlatform =
                    JumpToJumpAlgorithm.generateNext(
                        template, world, lastCenter[0], lastYaw[0], nextIndex[0], rng);

                lastCenter[0] = newPlatform.centerLocation().clone();
                lastYaw[0]    = newPlatform.outgoingYawDeg();
                nextIndex[0]++;

                // Stage into instance (not yet placed)
                for (PlacedBlock pb : newPlatform.placedBlocks()) {
                    instance.placedBlocks.add(pb);
                }
                instance.placedPlatformCount.incrementAndGet();

                // ── Dispatch placement per chunk ────────────────────────────
                Map<String, List<PlacedBlock>> newByChunk = new LinkedHashMap<>();
                for (PlacedBlock pb : newPlatform.placedBlocks()) {
                    String key = pb.location().getWorld().getName() + ","
                        + pb.location().getChunk().getX() + ","
                        + pb.location().getChunk().getZ();
                    newByChunk.computeIfAbsent(key, k -> new ArrayList<>()).add(pb);
                }
                for (List<PlacedBlock> chunkBlocks : newByChunk.values()) {
                    scheduler.runAtLocation(chunkBlocks.get(0).location(), () -> {
                        for (PlacedBlock pb : chunkBlocks) {
                            instance.placeBlock(pb);
                        }
                    });
                }
            }
        }, 10L, 10L);

        instance.slidingTask = task;
    }

    private int getPlayerPlatformIndex(
        @NotNull GeneratedCourseInstanceImpl instance,
        @NotNull Location playerLoc
    ) {
        List<Checkpoint> cps = instance.getCourse().getCheckpoints();
        int closest      = Math.max(0, instance.slidingWindowHead);
        double closestDist = Double.MAX_VALUE;
        for (int i = instance.slidingWindowHead; i < cps.size(); i++) {
            Location spawn = cps.get(i).getSpawnLocation();
            if (!spawn.getWorld().equals(playerLoc.getWorld())) continue;
            double dist = spawn.distanceSquared(playerLoc);
            if (dist < closestDist) {
                closestDist = dist;
                closest = i;
            }
        }
        return closest;
    }

    // ── Session end / cleanup ──────────────────────────────────────────────

    public void onSessionEnd(@NotNull Player player) {
        String courseId = playerCourseMap.remove(player.getUniqueId());
        if (courseId == null) return;

        GeneratedCourseInstanceImpl instance = instances.get(courseId);
        if (instance == null) return;

        instance.removePlayer(player);

        // Shared mode: stay alive while other players are still on it
        if (!instance.isEmpty()) return;

        if (!instance.tryDeactivate()) return;

        if (instance.slidingTask != null) instance.slidingTask.cancel();

        int  delaySec = instance.getCourse().getTemplate().cleanupDelaySeconds();
        long delayMs  = delaySec * 1000L;

        plugin.getBasicParkourAPI().getCourseRegistry().unregisterCourse(courseId);
        instances.remove(courseId);

        SlotAllocatorKey slotKey = instanceSlotKeys.remove(courseId);
        if (slotKey != null) {
            SlotAllocator alloc = slotAllocators.get(slotKey.worldName());
            if (alloc != null) alloc.release(slotKey.slotId());
        }

        plugin.getLogger().info("[Generator] Scheduling cleanup for '" + courseId + "' in " + delaySec + "s.");

        // Async delay → then dispatch per-chunk region tasks for the actual restoration
        scheduler.runAsyncLater(() -> dispatchCleanup(instance), delayMs);
    }

    private void dispatchCleanup(@NotNull GeneratedCourseInstanceImpl instance) {
        // Group placed blocks by chunk; dispatch one region-thread task per chunk
        Map<String, List<PlacedBlock>> byChunk = new LinkedHashMap<>();
        synchronized (instance.placedBlocks) {
            for (PlacedBlock pb : instance.placedBlocks) {
                String key = pb.location().getWorld().getName() + ","
                    + pb.location().getChunk().getX() + ","
                    + pb.location().getChunk().getZ();
                byChunk.computeIfAbsent(key, k -> new ArrayList<>()).add(pb);
            }
        }
        for (List<PlacedBlock> chunk : byChunk.values()) {
            scheduler.runAtLocation(chunk.get(0).location(), () ->
                instance.restoreBlocks(plugin.getLogger()));
        }
    }

    // ── World resolution ───────────────────────────────────────────────────

    @Nullable
    private World resolveWorld(@NotNull GeneratorTemplate template) {
        if (template.entryMode() == EntryMode.DEDICATED_WORLD) {
            if (template.dedicatedWorldName() == null) return null;
            return Bukkit.getWorld(template.dedicatedWorldName());
        }
        String markerWorld = plugin.getConfig().getString(
            "generator.entry-points." + template.id() + ".world");
        if (markerWorld == null) return null;
        return Bukkit.getWorld(markerWorld);
    }

    // ── Shutdown ───────────────────────────────────────────────────────────

    public void shutdown() {
        for (GeneratedCourseInstanceImpl instance : instances.values()) {
            if (instance.slidingTask != null) instance.slidingTask.cancel();
            instance.tryDeactivate();
            // On shutdown we're on the main/global thread — call restoreBlocks directly
            instance.restoreBlocks(plugin.getLogger());
        }
        instances.clear();
        playerCourseMap.clear();
        instanceSlotKeys.clear();
    }

    // ── Records ────────────────────────────────────────────────────────────

    private record SlotAllocatorKey(@NotNull String worldName, int slotId) {}
}
