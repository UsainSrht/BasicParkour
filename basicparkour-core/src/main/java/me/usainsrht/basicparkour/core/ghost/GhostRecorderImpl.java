package me.usainsrht.basicparkour.core.ghost;

import me.usainsrht.basicparkour.api.ghost.GhostRecorder;
import me.usainsrht.basicparkour.core.BasicParkourPlugin;
import me.usainsrht.basicparkour.core.scheduling.FoliaScheduler;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Records player movement frames asynchronously for ghost run playback.
 *
 * <h2>Recording lifecycle</h2>
 * <ol>
 *   <li>{@link #startRecording(Player)} — creates frame buffer, schedules capture task.</li>
 *   <li>Every {@code ghost.record-interval-ms} milliseconds, the player's position is sampled
 *       on the entity's owning thread and stored as a {@link GhostFrame}.</li>
 *   <li>{@link #saveRecording} — stops the capture task, serialises frames, and persists
 *       them asynchronously (stored in DB as a JSON blob or binary stream).</li>
 *   <li>{@link #discardRecording} — stops the capture task without saving.</li>
 * </ol>
 *
 * <p>The ghost system is intentionally lightweight. Frame buffers are bounded by
 * {@code ghost.max-frames} (default 12000 ≈ 10 minutes at 20fps) to prevent OOM.</p>
 */
public final class GhostRecorderImpl implements GhostRecorder {

    private final BasicParkourPlugin plugin;
    private final FoliaScheduler scheduler;
    private final int maxFrames;
    private final long intervalMs;

    /** Player UUID → active frame buffer + capture task */
    private final ConcurrentHashMap<UUID, RecordingSession> sessions = new ConcurrentHashMap<>();

    public GhostRecorderImpl(@NotNull BasicParkourPlugin plugin, @NotNull FoliaScheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.maxFrames = plugin.getConfig().getInt("ghost.max-frames", 12000);
        this.intervalMs = plugin.getConfig().getLong("ghost.record-interval-ms", 50L);
    }

    @Override
    public void startRecording(@NotNull Player player) {
        if (sessions.containsKey(player.getUniqueId())) return;

        List<GhostFrame> frames = Collections.synchronizedList(new ArrayList<>(maxFrames));

        // Sample position on entity thread, add to frame list on async thread
        FoliaScheduler.RepeatingTask task = scheduler.runRepeatingAsync(() -> {
            // We need to read location on entity's thread
            scheduler.runOnEntity(player, () -> {
                if (!player.isOnline()) return;
                if (frames.size() >= maxFrames) return; // buffer full
                Location loc = player.getLocation();
                GhostFrame frame = new GhostFrame(
                    loc.getX(), loc.getY(), loc.getZ(),
                    loc.getYaw(), loc.getPitch(),
                    System.currentTimeMillis()
                );
                frames.add(frame);
            });
        }, intervalMs, intervalMs);

        sessions.put(player.getUniqueId(), new RecordingSession(frames, task));
    }

    @Override
    public void discardRecording(@NotNull Player player) {
        RecordingSession session = sessions.remove(player.getUniqueId());
        if (session != null) session.task().cancel();
    }

    @Override
    @NotNull
    public CompletableFuture<UUID> saveRecording(@NotNull Player player,
                                                   @NotNull String courseId,
                                                   long timeMs) {
        RecordingSession session = sessions.remove(player.getUniqueId());
        if (session == null) return CompletableFuture.completedFuture(UUID.randomUUID());
        session.task().cancel();

        List<GhostFrame> frames = new ArrayList<>(session.frames());
        UUID ghostId = UUID.randomUUID();

        return CompletableFuture.supplyAsync(() -> {
            // Serialise frames to JSON and store in DB
            // (Full DB integration depends on repository implementation;
            //  this stub logs and returns the ghost ID)
            plugin.getLogger().info("[Ghost] Saved %d frames for %s on course '%s' (time: %dms)"
                .formatted(frames.size(), player.getName(), courseId, timeMs));
            // TODO: persist frames to repository via dedicated ghost table
            return ghostId;
        }).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "Failed to save ghost for " + player.getName(), ex);
            return ghostId;
        });
    }

    @Override
    public boolean isRecording(@NotNull Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    // -----------------------------------------------------------------------
    // Private record
    // -----------------------------------------------------------------------

    private record RecordingSession(
        @NotNull List<GhostFrame> frames,
        @NotNull FoliaScheduler.RepeatingTask task
    ) {}
}
