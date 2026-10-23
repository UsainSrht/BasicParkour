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

    /** Active recording sessions: player UUID -> recording session */
    private final ConcurrentHashMap<UUID, RecordingSession> sessions = new ConcurrentHashMap<>();
    /** Active playback sessions: player UUID -> playback session */
    private final ConcurrentHashMap<UUID, PlaybackSession> playbacks = new ConcurrentHashMap<>();
    /** Players who toggled ghosts off */
    private final Set<UUID> ghostDisabled = ConcurrentHashMap.newKeySet();

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
        long startTime = System.currentTimeMillis();

        // Sample position on entity thread, add to frame list
        FoliaScheduler.RepeatingTask task = scheduler.runRepeatingAsync(() -> {
            scheduler.runOnEntity(player, () -> {
                if (!player.isOnline()) return;
                if (frames.size() >= maxFrames) return;
                Location loc = player.getLocation();
                GhostFrame frame = new GhostFrame(
                    loc.getX(), loc.getY(), loc.getZ(),
                    loc.getYaw(), loc.getPitch(),
                    System.currentTimeMillis() - startTime
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
        stopPlayback(player);
    }

    @Override
    @NotNull
    public CompletableFuture<UUID> saveRecording(@NotNull Player player,
                                                   @NotNull String courseId,
                                                   long timeMs) {
        RecordingSession session = sessions.remove(player.getUniqueId());
        stopPlayback(player);

        if (session == null || session.frames().isEmpty()) {
            return CompletableFuture.completedFuture(UUID.randomUUID());
        }
        session.task().cancel();

        List<GhostFrame> frames = new ArrayList<>(session.frames());
        UUID ghostId = UUID.randomUUID();

        return CompletableFuture.supplyAsync(() -> {
            try {
                byte[] data = GhostFrame.serializeList(frames);
                plugin.getRepository().saveGhostRecord(player.getUniqueId(), player.getName(), courseId, timeMs, data);
                plugin.getLogger().info("[Ghost] Saved %d frames for %s on course '%s' (%d ms)"
                    .formatted(frames.size(), player.getName(), courseId, timeMs));
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to serialize and save ghost for " + player.getName(), ex);
            }
            return ghostId;
        });
    }

    @Override
    public boolean isRecording(@NotNull Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    // ── Ghost Playback ──────────────────────────────────────────────────────

    public void startPlayback(@NotNull Player player, @NotNull String courseId) {
        if (!plugin.getConfig().getBoolean("ghost.playback.enabled", true)) return;
        if (ghostDisabled.contains(player.getUniqueId())) return;

        stopPlayback(player);

        String mode = plugin.getConfig().getString("ghost.playback.mode", "SERVER_BEST");
        CompletableFuture<Optional<byte[]>> ghostDataFuture = mode.equalsIgnoreCase("PERSONAL_BEST")
                ? plugin.getRepository().getPlayerGhost(player.getUniqueId(), courseId)
                : plugin.getRepository().getBestGhost(courseId);

        ghostDataFuture.thenAccept(opt -> {
            if (opt.isEmpty()) return;
            try {
                List<GhostFrame> frames = GhostFrame.deserializeList(opt.get());
                if (frames.isEmpty()) return;

                scheduler.runOnEntity(player, () -> {
                    if (!player.isOnline()) return;

                    GhostFrame first = frames.get(0);
                    Location spawnLoc = new Location(player.getWorld(), first.x(), first.y(), first.z(), first.yaw(), first.pitch());

                    org.bukkit.entity.ArmorStand stand = player.getWorld().spawn(spawnLoc, org.bukkit.entity.ArmorStand.class, as -> {
                        as.setVisible(false);
                        as.setGravity(false);
                        as.setCanMove(false);
                        as.setBasePlate(false);
                        as.setArms(true);
                        as.setSmall(false);
                        as.setPersistent(false);
                        as.customName(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                            .deserialize("<aqua>👻 Ghost Run</aqua>"));
                        as.setCustomNameVisible(true);

                        // Equipment
                        org.bukkit.inventory.EntityEquipment eq = as.getEquipment();
                        eq.setHelmet(new org.bukkit.inventory.ItemStack(org.bukkit.Material.GLASS));
                        eq.setChestplate(new org.bukkit.inventory.ItemStack(org.bukkit.Material.CHAINMAIL_CHESTPLATE));
                        eq.setLeggings(new org.bukkit.inventory.ItemStack(org.bukkit.Material.CHAINMAIL_LEGGINGS));
                        eq.setBoots(new org.bukkit.inventory.ItemStack(org.bukkit.Material.CHAINMAIL_BOOTS));

                        // Add to parkour team so it shares translucent visibility with parkour players
                        plugin.getSessionManager().getTeamManager().addEntity(as);

                        // If ghost translucency is enabled, apply invisibility so it renders as a see-through ghost
                        if (plugin.getConfig().getBoolean("ghost.playback.translucent", true)) {
                            as.addPotionEffect(new org.bukkit.potion.PotionEffect(
                                org.bukkit.potion.PotionEffectType.INVISIBILITY,
                                org.bukkit.potion.PotionEffect.INFINITE_DURATION,
                                0, false, false, false
                            ));
                        }
                    });

                    long startTime = System.currentTimeMillis();
                    int[] frameCursor = {0};

                    FoliaScheduler.RepeatingTask playbackTask = scheduler.runRepeatingOnEntity(stand, () -> {
                        if (!player.isOnline() || !stand.isValid()) {
                            stopPlayback(player);
                            return;
                        }

                        long elapsed = System.currentTimeMillis() - startTime;
                        int idx = frameCursor[0];

                        while (idx < frames.size() - 1 && frames.get(idx + 1).timestamp() <= elapsed) {
                            idx++;
                        }
                        frameCursor[0] = idx;

                        if (idx >= frames.size() - 1) {
                            stopPlayback(player);
                            return;
                        }

                        GhostFrame current = frames.get(idx);
                        Location targetLoc = new Location(stand.getWorld(), current.x(), current.y(), current.z(), current.yaw(), current.pitch());
                        stand.teleportAsync(targetLoc);
                    }, 1L, 1L);

                    playbacks.put(player.getUniqueId(), new PlaybackSession(stand, playbackTask));
                });
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load ghost frames for playback", e);
            }
        });
    }

    public void stopPlayback(@NotNull Player player) {
        PlaybackSession session = playbacks.remove(player.getUniqueId());
        if (session != null) {
            session.task().cancel();
            plugin.getSessionManager().getTeamManager().removeEntity(session.entity());
            if (session.entity().isValid()) {
                scheduler.runOnEntity(session.entity(), () -> session.entity().remove());
            }
        }
    }

    @Override
    public boolean togglePlayerGhost(@NotNull UUID playerUuid) {
        if (ghostDisabled.contains(playerUuid)) {
            ghostDisabled.remove(playerUuid);
            return true; // now enabled
        } else {
            ghostDisabled.add(playerUuid);
            Player p = org.bukkit.Bukkit.getPlayer(playerUuid);
            if (p != null) stopPlayback(p);
            return false; // now disabled
        }
    }

    @Override
    public boolean isPlayerGhostEnabled(@NotNull UUID playerUuid) {
        return !ghostDisabled.contains(playerUuid);
    }

    public boolean toggleGhost(@NotNull Player player) {
        return togglePlayerGhost(player.getUniqueId());
    }

    public void cleanup() {
        sessions.values().forEach(s -> s.task().cancel());
        sessions.clear();
        playbacks.values().forEach(p -> {
            p.task().cancel();
            plugin.getSessionManager().getTeamManager().removeEntity(p.entity());
            if (p.entity().isValid()) p.entity().remove();
        });
        playbacks.clear();
    }

    // -----------------------------------------------------------------------
    // Private records
    // -----------------------------------------------------------------------

    private record RecordingSession(
        @NotNull List<GhostFrame> frames,
        @NotNull FoliaScheduler.RepeatingTask task
    ) {}

    private record PlaybackSession(
        @NotNull org.bukkit.entity.ArmorStand entity,
        @NotNull FoliaScheduler.RepeatingTask task
    ) {}
}
