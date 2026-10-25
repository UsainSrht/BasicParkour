package me.usainsrht.basicparkour.core.session;

import me.usainsrht.basicparkour.api.BasicParkourAPI;
import me.usainsrht.basicparkour.api.course.Checkpoint;
import me.usainsrht.basicparkour.api.course.ParkourCourse;
import me.usainsrht.basicparkour.api.event.CheckpointReachEvent;
import me.usainsrht.basicparkour.api.event.CourseCheckpointTeleportEvent;
import me.usainsrht.basicparkour.api.event.CourseCompleteEvent;
import me.usainsrht.basicparkour.api.event.CourseLeaveEvent;
import me.usainsrht.basicparkour.api.event.CoursePreCompleteEvent;
import me.usainsrht.basicparkour.api.event.CoursePreFailEvent;
import me.usainsrht.basicparkour.api.event.CourseRecordSaveEvent;
import me.usainsrht.basicparkour.api.event.CourseRestartEvent;
import me.usainsrht.basicparkour.api.event.CourseStartEvent;
import me.usainsrht.basicparkour.api.event.PlayerVisibilityModeChangeEvent;
import me.usainsrht.basicparkour.api.event.SessionFailEvent;
import me.usainsrht.basicparkour.api.session.ParkourSession;
import me.usainsrht.basicparkour.api.session.ParkourSession.FailReason;
import me.usainsrht.basicparkour.api.storage.PlayerRecord;
import me.usainsrht.basicparkour.core.listener.PlayerMoveListener;
import me.usainsrht.basicparkour.core.BasicParkourPlugin;
import me.usainsrht.basicparkour.core.config.MessageConfig;
import me.usainsrht.basicparkour.core.scheduling.FoliaScheduler;
import me.usainsrht.basicparkour.core.util.TimerFormatter;
import me.usainsrht.basicparkour.core.hotbar.HotbarManager;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Central manager for all active parkour sessions. Implements the game loop:
 * start → checkpoint advance → complete/fail → cleanup.
 *
 * <p>This class also implements {@link BasicParkourAPI.SessionManager}.</p>
 *
 * <h2>Thread Safety</h2>
 * Sessions are stored in a {@link ConcurrentHashMap}. State transitions within a session
 * use atomic CAS operations. All Bukkit API calls are dispatched to the correct scheduler
 * thread via {@link FoliaScheduler}.
 */
public final class SessionManager implements BasicParkourAPI.SessionManager {

    private final BasicParkourPlugin plugin;
    private final FoliaScheduler scheduler;
    private final MessageConfig messages;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    /** UUID → active session. Only one session per player at a time. */
    private final ConcurrentHashMap<UUID, ParkourSessionImpl> sessions = new ConcurrentHashMap<>();

    /** Manages the scoreboard team that suppresses player-vs-player collision. */
    private final ParkourTeamManager teamManager;

    /** Global timer display task handle. */
    private FoliaScheduler.RepeatingTask timerTask;

    /** Hotbar manager for utility items (Restart, Checkpoint, Visibility, Leave). */
    private HotbarManager hotbarManager;

    /** Set of player UUIDs currently executing an internal plugin teleport. */
    private final Set<UUID> internalTeleports = ConcurrentHashMap.newKeySet();

    /** Saved player flight states prior to starting parkour. */
    private final Map<UUID, Boolean> previousAllowFlight = new ConcurrentHashMap<>();

    /** Saved player food levels prior to starting parkour. */
    private final Map<UUID, Integer> previousFoodLevel = new ConcurrentHashMap<>();

    /** Saved player saturation levels prior to starting parkour. */
    private final Map<UUID, Float> previousSaturation = new ConcurrentHashMap<>();

    /** Saved player gamemodes prior to starting parkour. */
    private final Map<UUID, GameMode> previousGameMode = new ConcurrentHashMap<>();

    /** Per-player visibility preferences. */
    private final Map<UUID, String> playerVisibilityModes = new ConcurrentHashMap<>();

    public SessionManager(@NotNull BasicParkourPlugin plugin,
                           @NotNull FoliaScheduler scheduler,
                           @NotNull MessageConfig messages) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.messages = messages;
        this.teamManager = new ParkourTeamManager(scheduler);
    }

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    /** Called by the plugin during onEnable to start the timer display loop. */
    public void start() {
        timerTask = scheduler.runRepeatingGlobal(this::broadcastTimers, 1L, 1L);
    }

    /**
     * Safely stops every active parkour session, making players leave cleanly,
     * restoring their survival state, inventories, collision, visibility,
     * and teleporting them back safely.
     */
    public void stopAllSessions() {
        for (ParkourSessionImpl session : new java.util.ArrayList<>(sessions.values())) {
            Player player = session.getPlayer();
            if (player != null && player.isOnline()) {
                leaveSession(player);
            } else if (player != null) {
                removeSession(player);
            }
        }
    }

    /** Called by the plugin during onDisable to stop tasks and clean up sessions. */
    public void shutdown() {
        if (timerTask != null) timerTask.cancel();
        stopAllSessions();
        teamManager.cleanup();
    }

    // -----------------------------------------------------------------------
    // BasicParkourAPI.SessionManager implementation
    // -----------------------------------------------------------------------

    @Override
    @Nullable
    public ParkourSession startSession(@NotNull Player player, @NotNull ParkourCourse course) {
        return startSession(player, course, true);
    }

    /**
     * Starts a new parkour session with configurable teleportation.
     */
    @Nullable
    public ParkourSession startSession(@NotNull Player player, @NotNull ParkourCourse course, boolean shouldTeleport) {
        return startSession(player, course, shouldTeleport, null);
    }

    /**
     * Starts a session, optionally overriding the location the player is returned to on
     * leave/complete/fail (used by generated runs, where the player is teleported before the session starts).
     */
    @Nullable
    public ParkourSession startSession(@NotNull Player player, @NotNull ParkourCourse course,
                                       boolean shouldTeleport, @Nullable Location joinLocationOverride) {
        // Disallow starting if already in a session
        ParkourSessionImpl existing = sessions.get(player.getUniqueId());
        if (existing != null) return existing;

        // Fire cancellable event
        CourseStartEvent event = new CourseStartEvent(player, course);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return null;

        // Create session
        ParkourSessionImpl session = new ParkourSessionImpl(player,
            joinLocationOverride != null ? joinLocationOverride.clone() : player.getLocation().clone(), course, this);
        sessions.put(player.getUniqueId(), session);

        // Flight restrictions
        if (plugin.getConfig().getBoolean("restrictions.disable-flight", true)) {
            previousAllowFlight.put(player.getUniqueId(), player.getAllowFlight());
            player.setAllowFlight(false);
            player.setFlying(false);
        }

        // Hunger freeze / full sprint
        if (plugin.getConfig().getBoolean("protections.prevent-hunger", true)) {
            previousFoodLevel.put(player.getUniqueId(), player.getFoodLevel());
            previousSaturation.put(player.getUniqueId(), player.getSaturation());
            player.setFoodLevel(20);
            player.setSaturation(20f);
        }

        // Gamemode enforcement
        String forceGm = plugin.getConfig().getString("restrictions.force-gamemode", "NONE").toUpperCase(java.util.Locale.ROOT);
        if (!forceGm.equals("NONE")) {
            try {
                GameMode gm = GameMode.valueOf(forceGm);
                previousGameMode.put(player.getUniqueId(), player.getGameMode());
                player.setGameMode(gm);
            } catch (IllegalArgumentException ignored) {}
        }

        // Hotbar items
        if (hotbarManager != null) {
            hotbarManager.onSessionStart(player);
        }

        // Teleport to start if requested and configured
        if (shouldTeleport) {
            Location startLoc = null;
            if (!course.getStartLocations().isEmpty()) {
                java.util.List<Location> locs = course.getStartLocations();
                startLoc = locs.get(new java.util.Random().nextInt(locs.size()));
            } else if (!(course instanceof me.usainsrht.basicparkour.api.generator.GeneratedParkourCourse)) {
                // For premade courses, fallback to start region center
                startLoc = course.getStartRegion().getCenter();
            }

            if (startLoc != null) {
                final Location finalStart = startLoc;
                markInternalTeleport(player.getUniqueId());
                scheduler.runOnEntity(player, () ->
                    player.teleportAsync(finalStart).thenRun(() -> clearInternalTeleport(player.getUniqueId())));
            }
        }

        // Add to no-collision team so parkour players can't push each other
        teamManager.addPlayer(player);

        // Apply TAB hook rules (collision rule override / nametag handling)
        if (plugin.getTabHook() != null) {
            plugin.getTabHook().onPlayerEnterSession(player);
        }

        // Apply visibility (hide other parkour players or make them translucent)
        applyVisibility(player);

        // Start ghost recording if enabled
        if (plugin.getConfig().getBoolean("ghost.enabled", false)) {
            plugin.getGhostRecorder().startRecording(player);
        }

        // If endless generated course, advance checkpoint to 0 immediately so block count starts at 0
        if (course instanceof me.usainsrht.basicparkour.api.generator.GeneratedParkourCourse gpc
                && gpc.getTemplate().lengthType() == me.usainsrht.basicparkour.api.generator.LengthType.INFINITE) {
            session.advanceCheckpoint();
        }

        // Notify player via YamlMessage with MiniPlaceholders support
        messages.send(messages.getCourseStart(), player,
            Placeholder.component("course", course.getDisplayName()));

        plugin.getLogger().info("[BasicParkour] Started session for %s on course '%s'".formatted(
            player.getName(), course.getId()));

        return session;
    }

    @Override
    @NotNull
    public Optional<ParkourSession> getSession(@NotNull Player player) {
        return Optional.ofNullable(sessions.get(player.getUniqueId()));
    }

    @Override
    @NotNull
    public Collection<ParkourSession> getActiveSessions() {
        return Collections.unmodifiableCollection(sessions.values());
    }

    public @NotNull ParkourTeamManager getTeamManager() {
        return teamManager;
    }

    // -----------------------------------------------------------------------
    // Game logic: checkpoint advance
    // -----------------------------------------------------------------------

    /**
     * Called by {@link PlayerMoveListener} when a player
     * enters a checkpoint's trigger region.
     *
     * @param player     the player
     * @param checkpoint the checkpoint they entered
     * @return {@code true} if the checkpoint was registered
     */
    public boolean handleCheckpointReach(@NotNull Player player, @NotNull Checkpoint checkpoint) {
        ParkourSessionImpl session = sessions.get(player.getUniqueId());
        if (session == null) return false;

        int currentIdx = session.getCurrentCheckpointIndex();
        int targetIdx = checkpoint.getIndex();
        if (targetIdx <= currentIdx) return false;

        boolean isGenerated = session.getCourse() instanceof me.usainsrht.basicparkour.api.generator.GeneratedParkourCourse;
        if (!isGenerated && targetIdx != currentIdx + 1) {
            // Standard courses require sequential checkpoint completion
            return false;
        }

        // Take snapshot before advancing
        var snapshot = session.getTimerSnapshot();

        // Fire event (cancellable)
        CheckpointReachEvent event = new CheckpointReachEvent(player, session, checkpoint, snapshot);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return false;

        // Advance to target checkpoint (handling skips)
        int gained = targetIdx - currentIdx;
        while (session.getCurrentCheckpointIndex() < targetIdx) {
            session.advanceCheckpoint();
        }

        boolean isEndless = session.getCourse() instanceof me.usainsrht.basicparkour.api.generator.GeneratedParkourCourse gpc
                && gpc.getTemplate().lengthType() == me.usainsrht.basicparkour.api.generator.LengthType.INFINITE;

        if (isEndless) {
            playBlockCountSound(player, targetIdx);
            sendBlockCountMessage(player, targetIdx, gained);
            if (plugin.getGeneratorManager() != null) {
                plugin.getGeneratorManager().notifyPlayerProgress(player, targetIdx);
            }
        } else {
            messages.send(messages.getCheckpointReach(), player,
                Placeholder.parsed("checkpoint", String.valueOf(checkpoint.getIndex() + 1)),
                Placeholder.parsed("time", snapshot.formatted()));
        }

        return true;
    }

    private void playBlockCountSound(@NotNull Player player, int blockCount) {
        if (!plugin.getConfig().getBoolean("generator.block-sound.enabled", true)) return;
        String soundKey = plugin.getConfig().getString("generator.block-sound.sound", "block.note_block.bass");
        float baseVolume = (float) plugin.getConfig().getDouble("generator.block-sound.volume", 0.8);
        boolean shepard = plugin.getConfig().getBoolean("generator.block-sound.shepard", true);

        if (!shepard) {
            float pitch = (float) plugin.getConfig().getDouble("generator.block-sound.pitch", 1.0);
            playSound(player, soundKey, baseVolume, pitch);
            return;
        }

        int cycle = Math.max(1, plugin.getConfig().getInt("generator.block-sound.shepard-cycle", 12));
        // Normalize block count so block 1 is step 0
        int step = Math.floorMod(blockCount - 1, cycle);
        double fraction = (double) step / cycle;

        // Two octaves spanning Minecraft's pitch range [0.5, 2.0]
        float pitchLow = (float) (0.5 * Math.pow(2.0, fraction));
        float pitchHigh = (float) (1.0 * Math.pow(2.0, fraction));

        // Equal-power sinusoidal crossfade (vLow^2 + vHigh^2 = baseVolume^2)
        double angle = fraction * (Math.PI / 2.0);
        float volLow = (float) (baseVolume * Math.sin(angle));
        float volHigh = (float) (baseVolume * Math.cos(angle));

        if (volLow > 0.01f) {
            playSound(player, soundKey, volLow, pitchLow);
        }
        if (volHigh > 0.01f) {
            playSound(player, soundKey, volHigh, pitchHigh);
        }
    }

    private void playSound(@NotNull Player player, @NotNull String soundKey, float volume, float pitch) {
        Location loc = player.getLocation();
        try {
            org.bukkit.Sound enumSound = org.bukkit.Sound.valueOf(soundKey.toUpperCase(java.util.Locale.ROOT).replace('.', '_'));
            player.playSound(loc, enumSound, org.bukkit.SoundCategory.PLAYERS, volume, pitch);
        } catch (IllegalArgumentException ex) {
            player.playSound(loc, soundKey.toLowerCase(java.util.Locale.ROOT), org.bukkit.SoundCategory.PLAYERS, volume, pitch);
        }
    }

    private void sendBlockCountMessage(@NotNull Player player, int totalBlocks, int gained) {
        String msg = plugin.getConfig().getString("generator.block-message", "");
        if (msg == null || msg.isBlank()) return;
        net.kyori.adventure.text.Component comp = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
            .deserialize(msg,
                Placeholder.parsed("blocks", String.valueOf(totalBlocks)),
                Placeholder.parsed("score", String.valueOf(totalBlocks)),
                Placeholder.parsed("gained", String.valueOf(gained)));
        player.sendMessage(comp);
    }

    // -----------------------------------------------------------------------
    // Game logic: complete
    // -----------------------------------------------------------------------

    /**
     * Called when a player enters the course's end region.
     * Completes the session, saves the record, runs rewards.
     *
     * @param player the player who completed the course
     */
    public void completeSession(@NotNull Player player) {
        ParkourSessionImpl session = sessions.get(player.getUniqueId());
        if (session == null) return;

        long initialElapsed = session.getElapsedMillis();
        java.util.List<String> initialRewards = new java.util.ArrayList<>(session.getCourse().getRewardCommands());

        // Fire CoursePreCompleteEvent (cancellable)
        CoursePreCompleteEvent preComplete = new CoursePreCompleteEvent(player, session, initialElapsed, initialRewards);
        Bukkit.getPluginManager().callEvent(preComplete);
        if (preComplete.isCancelled()) return;

        if (!session.tryComplete()) return; // Another thread already completed/failed

        long elapsed = preComplete.getElapsedMs();
        java.util.List<String> rewardsToRun = preComplete.getRewardCommands();
        boolean saveRecord = preComplete.isRecordBestTime();
        sessions.remove(player.getUniqueId());

        // Restore player survival state (flight, hunger, gamemode, inventory)
        restorePlayerState(player);

        // TAB hook restore
        if (plugin.getTabHook() != null) {
            plugin.getTabHook().onPlayerLeaveSession(player);
        }

        // Remove from no-collision team
        teamManager.removePlayer(player);

        // Restore visibility
        restoreVisibility(player);

        // Generator session cleanup
        if (plugin.getGeneratorManager() != null) {
            plugin.getGeneratorManager().onSessionEnd(player);
        }

        // Stop ghost recording
        if (plugin.getConfig().getBoolean("ghost.enabled", false)) {
            plugin.getGhostRecorder()
                .saveRecording(player, session.getCourse().getId(), elapsed)
                .exceptionally(ex -> {
                    plugin.getLogger().log(Level.WARNING, "Failed to save ghost recording", ex);
                    return null;
                });
        }

        // Teleport on completion if configured (e.g. custom completeLocation, JOIN_LOCATION, or START_LOCATION)
        Location compLoc = resolveCompleteLocation(session);
        String tpOnComplete = plugin.getConfig().getString("session.teleport-on-complete", "NONE").toUpperCase(java.util.Locale.ROOT);
        if (compLoc != null) {
            if (plugin.getGeneratorEntryListener() != null) {
                plugin.getGeneratorEntryListener().setEntryCooldown(player.getUniqueId(), 1000L);
            }
            markInternalTeleport(player.getUniqueId());
            scheduler.runOnEntity(player, () ->
                player.teleportAsync(compLoc).thenRun(() -> clearInternalTeleport(player.getUniqueId())));
        } else if (tpOnComplete.equals("JOIN_LOCATION")) {
            Location joinLoc = session.getJoinLocation();
            if (plugin.getGeneratorEntryListener() != null) {
                plugin.getGeneratorEntryListener().setEntryCooldown(player.getUniqueId(), 1000L);
            }
            markInternalTeleport(player.getUniqueId());
            scheduler.runOnEntity(player, () ->
                player.teleportAsync(joinLoc).thenRun(() -> clearInternalTeleport(player.getUniqueId())));
        } else if (tpOnComplete.equals("START_LOCATION")) {
            Location startLoc = null;
            if (!session.getCourse().getStartLocations().isEmpty()) {
                var locs = session.getCourse().getStartLocations();
                startLoc = locs.get(new java.util.Random().nextInt(locs.size()));
            } else if (!(session.getCourse() instanceof me.usainsrht.basicparkour.api.generator.GeneratedParkourCourse)) {
                startLoc = session.getCourse().getStartRegion().getCenter();
            }
            if (startLoc != null) {
                final Location finalStart = startLoc;
                markInternalTeleport(player.getUniqueId());
                scheduler.runOnEntity(player, () ->
                    player.teleportAsync(finalStart).thenRun(() -> clearInternalTeleport(player.getUniqueId())));
            }
        }

        // Save record asynchronously, then fire event back on main thread
        String recordCourseId = session.getCourse().getId();
        int recordBlocks = 0;
        if (session.getCourse() instanceof me.usainsrht.basicparkour.api.generator.GeneratedParkourCourse gpc) {
            recordCourseId = gpc.getTemplate().leaderboardCourseId();
            recordBlocks = session.getCourse().getCheckpoints().size();
        }
        final String targetCourseId = recordCourseId;
        final int finalRecordBlocks = recordBlocks;

        PlayerRecord newRecord = PlayerRecord.of(
            player.getUniqueId(), player.getName(),
            targetCourseId, elapsed, finalRecordBlocks
        );

        if (saveRecord) {
            plugin.getRepository().getPersonalBest(player.getUniqueId(), targetCourseId)
                .thenCompose(previousBest -> {
                    boolean isPotentialBest = previousBest.isEmpty() || elapsed < previousBest.get().timeMs();
                    CourseRecordSaveEvent saveEvent = new CourseRecordSaveEvent(player, newRecord, isPotentialBest);
                    Bukkit.getPluginManager().callEvent(saveEvent);
                    if (saveEvent.isCancelled()) {
                        return java.util.concurrent.CompletableFuture.completedFuture(false);
                    }
                    return plugin.getRepository().saveRecord(saveEvent.getRecord());
                })
                .thenAccept(isNewBest -> {
                    // Refresh holograms and top holder NPCs for this course
                    if (plugin.getHologramManager() != null) {
                        plugin.getHologramManager().onCourseComplete(targetCourseId, isNewBest);
                    }
                    if (plugin.getNpcManager() != null) {
                        plugin.getNpcManager().updateTopHolders(targetCourseId);
                    }

                    // Schedule event fire on appropriate thread
                    scheduler.runOnEntity(player, () -> {
                        CourseCompleteEvent ev = new CourseCompleteEvent(
                            player, session, elapsed, isNewBest, null, rewardsToRun
                        );
                        Bukkit.getPluginManager().callEvent(ev);

                        // Display completion message via YamlMessage
                        messages.send(messages.getCourseComplete(), player,
                            Placeholder.parsed("time", TimerFormatter.format(elapsed)),
                            Placeholder.component("course", session.getCourse().getDisplayName()));

                        if (isNewBest) {
                            messages.send(messages.getPersonalBest(), player,
                                Placeholder.parsed("time", TimerFormatter.format(elapsed)),
                                Placeholder.component("course", session.getCourse().getDisplayName()));
                        }

                        // Run reward commands
                        for (String cmd : rewardsToRun) {
                            String resolved = cmd.replace("{player}", player.getName());
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
                        }
                    });
                })
                .exceptionally(ex -> {
                    plugin.getLogger().log(Level.SEVERE, "Failed to save record for " + player.getName(), ex);
                    return null;
                });
        } else {
            scheduler.runOnEntity(player, () -> {
                CourseCompleteEvent ev = new CourseCompleteEvent(
                    player, session, elapsed, false, null, rewardsToRun
                );
                Bukkit.getPluginManager().callEvent(ev);

                messages.send(messages.getCourseComplete(), player,
                    Placeholder.parsed("time", TimerFormatter.format(elapsed)),
                    Placeholder.component("course", session.getCourse().getDisplayName()));

                for (String cmd : rewardsToRun) {
                    String resolved = cmd.replace("{player}", player.getName());
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
                }
            });
        }
    }

    // -----------------------------------------------------------------------
    // Game logic: fail / respawn
    // -----------------------------------------------------------------------

    /**
     * Called when a player's session should be failed (kill block, void, etc.).
     * Teleports the player instantly to their last checkpoint using the entity scheduler.
     *
     * @param player the player
     * @param reason the fail reason
     */
    public void failSession(@NotNull Player player, @NotNull FailReason reason) {
        ParkourSessionImpl session = sessions.get(player.getUniqueId());
        if (session == null) return;

        me.usainsrht.basicparkour.api.course.DieMode dieMode = session.getCourse().getDieMode();
        String onFail = plugin.getConfig().getString("session.on-fail", "COURSE").toUpperCase(java.util.Locale.ROOT);
        if (onFail.equals("CHECKPOINT")) {
            dieMode = me.usainsrht.basicparkour.api.course.DieMode.RESTART_DIE;
        } else if (onFail.equals("JOIN_LOCATION")) {
            dieMode = me.usainsrht.basicparkour.api.course.DieMode.FAIL_DIE;
        }

        Location initialRespawnLoc;
        if (dieMode == me.usainsrht.basicparkour.api.course.DieMode.FAIL_DIE) {
            initialRespawnLoc = resolveFailLocation(session);
        } else {
            // Determine respawn location: last checkpoint or start center
            initialRespawnLoc = session.getLastCheckpoint()
                .map(Checkpoint::getSpawnLocation)
                .orElseGet(() -> {
                    if (!session.getCourse().getStartLocations().isEmpty()) {
                        java.util.List<Location> locs = session.getCourse().getStartLocations();
                        return locs.get(new java.util.Random().nextInt(locs.size()));
                    }
                    return session.getCourse().getStartRegion().getCenter();
                });
        }

        // Fire CoursePreFailEvent (cancellable)
        CoursePreFailEvent preFail = new CoursePreFailEvent(player, session, reason, initialRespawnLoc);
        Bukkit.getPluginManager().callEvent(preFail);
        if (preFail.isCancelled()) return;

        reason = preFail.getReason();
        Location respawnLoc = preFail.getRespawnLocation();

        if (!session.tryFail()) return;

        me.usainsrht.basicparkour.api.generator.GeneratedParkourCourse gpc =
            session.getCourse() instanceof me.usainsrht.basicparkour.api.generator.GeneratedParkourCourse g ? g : null;
        boolean isEndless = gpc != null && gpc.getTemplate().lengthType() == me.usainsrht.basicparkour.api.generator.LengthType.INFINITE;
        int endlessBlocks = Math.max(0, session.getCurrentCheckpointIndex());
        long endlessElapsed = session.getElapsedMillis();

        if (isEndless) {
            // When endless run ends permanently on FAIL_DIE, record their personal best
            if (dieMode == me.usainsrht.basicparkour.api.course.DieMode.FAIL_DIE) {
                int blocks = endlessBlocks;
                long elapsed = endlessElapsed;
                String leaderboardCourseId = gpc.getTemplate().leaderboardCourseId();
                if (blocks > 0) {
                    PlayerRecord record = PlayerRecord.of(
                        player.getUniqueId(), player.getName(), leaderboardCourseId, elapsed, blocks
                    );
                    plugin.getRepository().saveRecord(record).thenAccept(isNewBest -> {
                        if (isNewBest) {
                            scheduler.runOnEntity(player, () -> {
                                messages.send(messages.getPersonalBestEndless(), player,
                                    Placeholder.parsed("blocks", String.valueOf(blocks)),
                                    Placeholder.parsed("time", TimerFormatter.format(elapsed)));
                            });
                        }
                        if (plugin.getHologramManager() != null) {
                            plugin.getHologramManager().onCourseComplete(leaderboardCourseId, isNewBest);
                        }
                        if (plugin.getNpcManager() != null) {
                            plugin.getNpcManager().updateTopHolders(leaderboardCourseId);
                        }
                    });
                }
            }
        }
        final me.usainsrht.basicparkour.api.course.DieMode finalDieMode = dieMode;

        if (dieMode == me.usainsrht.basicparkour.api.course.DieMode.FAIL_DIE) {
            restorePlayerState(player);
            if (plugin.getGeneratorEntryListener() != null) {
                plugin.getGeneratorEntryListener().setEntryCooldown(player.getUniqueId(), 1000L);
            }
        }

        // Reset session state to ACTIVE so the player can continue from checkpoint
        sessions.remove(player.getUniqueId());

        // Fire event
        Bukkit.getPluginManager().callEvent(new SessionFailEvent(player, session, reason));

        // Instant respawn via entity scheduler
        markInternalTeleport(player.getUniqueId());
        scheduler.runOnEntity(player, () -> {
            player.teleportAsync(respawnLoc).thenRun(() -> {
                clearInternalTeleport(player.getUniqueId());

                // Show fail message via YamlMessage
                if (isEndless) {
                    messages.send(messages.getSessionFailEndless(), player,
                        Placeholder.component("course", session.getCourse().getDisplayName()),
                        Placeholder.parsed("blocks", String.valueOf(endlessBlocks)),
                        Placeholder.parsed("score", String.valueOf(endlessBlocks)),
                        Placeholder.parsed("time", TimerFormatter.format(endlessElapsed)));
                } else {
                    messages.send(messages.getSessionFail(), player,
                        Placeholder.component("course", session.getCourse().getDisplayName()));
                }

                if (finalDieMode == me.usainsrht.basicparkour.api.course.DieMode.RESTART_DIE) {
                    // Restart a fresh session on the same course, preserving startTime and checkpoints
                    ParkourSessionImpl newSession = new ParkourSessionImpl(
                        player, session.getJoinLocation(), session.getCourse(), this, session.getStartTime()
                    );
                    int cpIdx = session.getCurrentCheckpointIndex();
                    for (int i = 0; i <= cpIdx; i++) {
                        newSession.advanceCheckpoint();
                    }
                    sessions.put(player.getUniqueId(), newSession);
                } else {
                    // FAIL_DIE: completely end session
                    if (plugin.getTabHook() != null) {
                        plugin.getTabHook().onPlayerLeaveSession(player);
                    }
                    teamManager.removePlayer(player);
                    restoreVisibility(player);
                    if (plugin.getConfig().getBoolean("ghost.enabled", false)) {
                        plugin.getGhostRecorder().discardRecording(player);
                    }
                    if (plugin.getGeneratorManager() != null) {
                        plugin.getGeneratorManager().onSessionEnd(player);
                    }
                }
            });
        });
    }

    /**
     * Forcibly removes a player's session without firing fail/complete events.
     * Used on player disconnect.
     *
     * @param player the player
     */
    public void removeSession(@NotNull Player player) {
        ParkourSessionImpl session = sessions.remove(player.getUniqueId());
        if (session != null) {
            session.tryInvalidate();
            restorePlayerState(player);
            if (plugin.getTabHook() != null) {
                plugin.getTabHook().onPlayerLeaveSession(player);
            }
            teamManager.removePlayer(player);
            restoreVisibility(player);
            if (plugin.getConfig().getBoolean("ghost.enabled", false)) {
                plugin.getGhostRecorder().discardRecording(player);
            }
            if (plugin.getGeneratorManager() != null) {
                plugin.getGeneratorManager().onSessionEnd(player);
            }
        }
    }

    /**
     * Called when a player executes /basicparkour leave or right-clicks the Leave hotbar item.
     * Ends the session cleanly and teleports the player back to where they started if configured.
     */
    @Override
    public void leaveSession(@NotNull Player player) {
        leaveSession(player, true);
    }

    @Override
    public void leaveSession(@NotNull Player player, boolean shouldTeleport) {
        leaveSession(player, shouldTeleport, CourseLeaveEvent.LeaveReason.COMMAND);
    }

    public void leaveSession(@NotNull Player player, boolean shouldTeleport, @NotNull CourseLeaveEvent.LeaveReason reason) {
        ParkourSessionImpl session = sessions.get(player.getUniqueId());
        if (session == null) {
            messages.send(messages.getNotInSession(), player);
            return;
        }

        Location exitLoc = resolveFailLocation(session);

        CourseLeaveEvent leaveEvent = new CourseLeaveEvent(player, session, reason, shouldTeleport, exitLoc);
        Bukkit.getPluginManager().callEvent(leaveEvent);
        if (leaveEvent.isCancelled()) return;

        shouldTeleport = leaveEvent.isTeleport();
        if (leaveEvent.getExitLocation() != null) {
            exitLoc = leaveEvent.getExitLocation();
        }

        if (session.getCourse() instanceof me.usainsrht.basicparkour.api.generator.GeneratedParkourCourse gpc
                && gpc.getTemplate().lengthType() == me.usainsrht.basicparkour.api.generator.LengthType.INFINITE) {
            int blocks = session.getCurrentCheckpointIndex();
            long elapsed = session.getElapsedMillis();
            String leaderboardCourseId = gpc.getTemplate().leaderboardCourseId();
            if (blocks > 0) {
                PlayerRecord record = PlayerRecord.of(
                    player.getUniqueId(), player.getName(), leaderboardCourseId, elapsed, blocks
                );
                plugin.getRepository().saveRecord(record).thenAccept(isNewBest -> {
                    if (isNewBest) {
                        scheduler.runOnEntity(player, () -> {
                            messages.send(messages.getPersonalBestEndless(), player,
                                Placeholder.parsed("blocks", String.valueOf(blocks)),
                                Placeholder.parsed("time", TimerFormatter.format(elapsed)));
                        });
                    }
                    if (plugin.getHologramManager() != null) {
                        plugin.getHologramManager().onCourseComplete(leaderboardCourseId, isNewBest);
                    }
                    if (plugin.getNpcManager() != null) {
                        plugin.getNpcManager().updateTopHolders(leaderboardCourseId);
                    }
                });
            }
        }
        boolean isGen = session.getCourse() instanceof me.usainsrht.basicparkour.api.generator.GeneratedParkourCourse;
        removeSession(player);
        messages.send(messages.getSessionLeave(), player);

        if (plugin.getGeneratorEntryListener() != null) {
            plugin.getGeneratorEntryListener().setEntryCooldown(player.getUniqueId(), 1000L);
        }

        if (shouldTeleport && (isGen || plugin.getConfig().getBoolean("session.teleport-on-leave", true))) {
            final Location target = exitLoc;
            markInternalTeleport(player.getUniqueId());
            scheduler.runOnEntity(player, () ->
                player.teleportAsync(target).thenRun(() -> clearInternalTeleport(player.getUniqueId())));
        }
    }

    /**
     * Resolves the location to teleport a player when failing or leaving a session.
     * Checks custom fail location, course/template fail-location, global session.fail-location/exit-location,
     * and falls back to session.joinLocation.
     */
    public @NotNull Location resolveFailLocation(@NotNull ParkourSessionImpl session) {
        if (session.getCustomFailLocation().isPresent()) {
            return session.getCustomFailLocation().get();
        }
        if (session.getCourse().getFailLocation().isPresent()) {
            return session.getCourse().getFailLocation().get();
        }
        Location globalFail = me.usainsrht.basicparkour.core.util.LocationParser.parse(plugin.getConfig().get("session.fail-location"));
        if (globalFail != null) return globalFail;
        Location globalExit = me.usainsrht.basicparkour.core.util.LocationParser.parse(plugin.getConfig().get("session.exit-location"));
        if (globalExit != null) return globalExit;
        return session.getJoinLocation();
    }

    /**
     * Resolves the location to teleport a player when completing a course.
     * Checks custom complete location, course/template complete-location, global session.complete-location/exit-location,
     * or returns null if not configured (allowing standard teleport-on-complete handling).
     */
    public @Nullable Location resolveCompleteLocation(@NotNull ParkourSessionImpl session) {
        if (session.getCustomCompleteLocation().isPresent()) {
            return session.getCustomCompleteLocation().get();
        }
        if (session.getCourse().getCompleteLocation().isPresent()) {
            return session.getCourse().getCompleteLocation().get();
        }
        Location globalComplete = me.usainsrht.basicparkour.core.util.LocationParser.parse(plugin.getConfig().get("session.complete-location"));
        if (globalComplete != null) return globalComplete;
        Location globalExit = me.usainsrht.basicparkour.core.util.LocationParser.parse(plugin.getConfig().get("session.exit-location"));
        if (globalExit != null) return globalExit;
        return null;
    }

    /**
     * Restarts the player's active session from the beginning.
     */
    @Override
    public void restartSession(@NotNull Player player) {
        restartSession(player, CourseRestartEvent.RestartReason.COMMAND);
    }

    public void restartSession(@NotNull Player player, @NotNull CourseRestartEvent.RestartReason reason) {
        ParkourSessionImpl session = sessions.get(player.getUniqueId());
        if (session == null) {
            messages.send(messages.getNotInSession(), player);
            return;
        }
        ParkourCourse course = session.getCourse();
        Location joinLoc = session.getJoinLocation();

        Location startLoc = null;
        if (!course.getStartLocations().isEmpty()) {
            var locs = course.getStartLocations();
            startLoc = locs.get(new java.util.Random().nextInt(locs.size()));
        } else if (!(course instanceof me.usainsrht.basicparkour.api.generator.GeneratedParkourCourse)) {
            startLoc = course.getStartRegion().getCenter();
        }

        CourseRestartEvent event = new CourseRestartEvent(player, session, reason, startLoc);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;

        startLoc = event.getStartLocation();

        session.tryInvalidate();
        sessions.remove(player.getUniqueId());
        if (plugin.getConfig().getBoolean("ghost.enabled", false)) {
            plugin.getGhostRecorder().discardRecording(player);
        }

        // Fresh session on the same course
        ParkourSessionImpl newSession = new ParkourSessionImpl(player, joinLoc, course, this);
        sessions.put(player.getUniqueId(), newSession);

        if (plugin.getConfig().getBoolean("ghost.enabled", false)) {
            plugin.getGhostRecorder().startRecording(player);
        }

        messages.send(messages.getSessionRestarted(), player,
            Placeholder.component("course", course.getDisplayName()));

        if (startLoc != null) {
            final Location finalStart = startLoc;
            markInternalTeleport(player.getUniqueId());
            scheduler.runOnEntity(player, () ->
                player.teleportAsync(finalStart).thenRun(() -> clearInternalTeleport(player.getUniqueId())));
        }
    }

    /**
     * Teleports the player back to their last checkpoint (or start).
     */
    @Override
    public void teleportToCheckpoint(@NotNull Player player) {
        ParkourSessionImpl session = sessions.get(player.getUniqueId());
        if (session == null) {
            messages.send(messages.getNotInSession(), player);
            return;
        }
        Location targetLoc = session.getLastCheckpoint()
            .map(Checkpoint::getSpawnLocation)
            .orElseGet(() -> {
                if (!session.getCourse().getStartLocations().isEmpty()) {
                    var locs = session.getCourse().getStartLocations();
                    return locs.get(new java.util.Random().nextInt(locs.size()));
                }
                return session.getCourse().getStartRegion().getCenter();
            });

        CourseCheckpointTeleportEvent event = new CourseCheckpointTeleportEvent(player, session, targetLoc);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;

        targetLoc = event.getTargetLocation();

        messages.send(messages.getTeleportedToCheckpoint(), player);
        markInternalTeleport(player.getUniqueId());
        final Location finalTarget = targetLoc;
        scheduler.runOnEntity(player, () ->
            player.teleportAsync(finalTarget).thenRun(() -> clearInternalTeleport(player.getUniqueId())));
    }

    @Override
    public boolean pauseSession(@NotNull Player player) {
        ParkourSessionImpl session = sessions.get(player.getUniqueId());
        return session != null && session.pause();
    }

    @Override
    public boolean resumeSession(@NotNull Player player) {
        ParkourSessionImpl session = sessions.get(player.getUniqueId());
        return session != null && session.resume();
    }

    /**
     * Cycles the player's visibility mode between ALL -> TRANSLUCENT -> NONE -> ALL.
     */
    @Override
    public @NotNull String cycleVisibility(@NotNull Player player) {
        String current = getVisibilityMode(player);
        String next = switch (current) {
            case "ALL" -> "TRANSLUCENT";
            case "TRANSLUCENT" -> "NONE";
            default -> "ALL";
        };
        PlayerVisibilityModeChangeEvent event = new PlayerVisibilityModeChangeEvent(player, current, next);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return current;

        String finalMode = event.getNewMode();
        playerVisibilityModes.put(player.getUniqueId(), finalMode);
        applyVisibility(player);
        messages.send(messages.getVisibilityToggled(), player,
            Placeholder.parsed("mode", finalMode));
        return finalMode;
    }

    @Override
    public @NotNull String getVisibilityMode(@NotNull Player player) {
        return playerVisibilityModes.computeIfAbsent(player.getUniqueId(),
            u -> plugin.getConfig().getString("visibility.default", "ALL").toUpperCase(java.util.Locale.ROOT));
    }

    @Override
    public void setVisibilityMode(@NotNull Player player, @NotNull String mode) {
        String current = getVisibilityMode(player);
        String target = mode.toUpperCase(java.util.Locale.ROOT);
        if (current.equalsIgnoreCase(target)) return;

        PlayerVisibilityModeChangeEvent event = new PlayerVisibilityModeChangeEvent(player, current, target);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;

        playerVisibilityModes.put(player.getUniqueId(), event.getNewMode());
        applyVisibility(player);
    }

    public boolean isInternalTeleport(@NotNull UUID uuid) {
        return internalTeleports.contains(uuid);
    }

    public void markInternalTeleport(@NotNull UUID uuid) {
        internalTeleports.add(uuid);
    }

    public void clearInternalTeleport(@NotNull UUID uuid) {
        internalTeleports.remove(uuid);
    }

    public void setHotbarManager(@Nullable HotbarManager hotbarManager) {
        this.hotbarManager = hotbarManager;
    }

    public @Nullable HotbarManager getHotbarManager() {
        return hotbarManager;
    }

    private void restorePlayerState(@NotNull Player player) {
        Boolean prevFlight = previousAllowFlight.remove(player.getUniqueId());
        if (prevFlight != null) {
            player.setAllowFlight(prevFlight);
        }

        Integer prevFood = previousFoodLevel.remove(player.getUniqueId());
        if (prevFood != null) {
            player.setFoodLevel(prevFood);
        }

        Float prevSat = previousSaturation.remove(player.getUniqueId());
        if (prevSat != null) {
            player.setSaturation(prevSat);
        }

        GameMode prevGm = previousGameMode.remove(player.getUniqueId());
        if (prevGm != null) {
            player.setGameMode(prevGm);
        }

        if (hotbarManager != null) {
            hotbarManager.onSessionEnd(player);
        }
    }

    // -----------------------------------------------------------------------
    // Timer display (called every tick by GlobalRegionScheduler)
    // -----------------------------------------------------------------------

    private void broadcastTimers() {
        sessions.forEach((uuid, session) -> {
            Player player = session.getPlayer();
            if (!player.isOnline()) return;
            var snapshot = session.getTimerSnapshot();
            String timeFormatted = snapshot.formatted() + (session.isPaused() ? " <gray>[PAUSED]</gray>" : "");
            boolean isEndless = session.getCourse() instanceof me.usainsrht.basicparkour.api.generator.GeneratedParkourCourse gpc
                    && gpc.getTemplate().lengthType() == me.usainsrht.basicparkour.api.generator.LengthType.INFINITE;

            if (isEndless) {
                int blocks = Math.max(0, session.getCurrentCheckpointIndex());
                messages.send(messages.getTimerDisplayEndless(), player,
                    Placeholder.parsed("time", timeFormatted),
                    Placeholder.parsed("blocks", String.valueOf(blocks)),
                    Placeholder.parsed("score", String.valueOf(blocks)));
            } else {
                messages.send(messages.getTimerDisplay(), player,
                    Placeholder.parsed("time", timeFormatted),
                    Placeholder.parsed("checkpoint", String.valueOf(snapshot.checkpointIndex() + 1)),
                    Placeholder.parsed("total", String.valueOf(session.getCourse().getCheckpoints().size())));
            }
        });
    }

    // -----------------------------------------------------------------------
    // Visibility management
    // -----------------------------------------------------------------------

    /** Tracks which players were hidden from which viewers by BasicParkour. */
    private final Map<UUID, Set<UUID>> hiddenByParkour = new ConcurrentHashMap<>();

    private void applyVisibility(@NotNull Player newPlayer) {
        String mode = getVisibilityMode(newPlayer);
        if (mode.equals("TRANSLUCENT")) {
            newPlayer.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.INVISIBILITY,
                org.bukkit.potion.PotionEffect.INFINITE_DURATION,
                0, false, false, false
            ));
        } else {
            newPlayer.removePotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY);
        }

        // Hide other parkour runners in the same world from each other, safely respecting Paper's vanish API
        for (ParkourSessionImpl other : sessions.values()) {
            Player otherPlayer = other.getPlayer();
            if (otherPlayer.equals(newPlayer)) continue;
            if (!otherPlayer.isOnline()) continue;
            if (!otherPlayer.getWorld().equals(newPlayer.getWorld())) continue;

            // Only hide if Paper vanish API says player is currently visible
            if (newPlayer.canSee(otherPlayer)) {
                newPlayer.hidePlayer(plugin, otherPlayer);
                hiddenByParkour.computeIfAbsent(newPlayer.getUniqueId(), k -> ConcurrentHashMap.newKeySet()).add(otherPlayer.getUniqueId());
            }
            if (otherPlayer.canSee(newPlayer)) {
                otherPlayer.hidePlayer(plugin, newPlayer);
                hiddenByParkour.computeIfAbsent(otherPlayer.getUniqueId(), k -> ConcurrentHashMap.newKeySet()).add(newPlayer.getUniqueId());
            }
        }

        // Hide blocks of other generated parkour runs in the same world
        if (plugin.getGeneratorManager() != null) {
            plugin.getGeneratorManager().updateBlockVisibilityForPlayer(newPlayer);
        }
    }

    private void restoreVisibility(@NotNull Player leavingPlayer) {
        leavingPlayer.removePotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY);
        playerVisibilityModes.remove(leavingPlayer.getUniqueId());

        // Restore players that this leaving player had hidden (safely, only those hidden by BasicParkour)
        Set<UUID> hiddenByLeaving = hiddenByParkour.remove(leavingPlayer.getUniqueId());
        if (hiddenByLeaving != null) {
            for (UUID targetUuid : hiddenByLeaving) {
                Player target = Bukkit.getPlayer(targetUuid);
                if (target != null && target.isOnline()) {
                    leavingPlayer.showPlayer(plugin, target);
                }
            }
        }

        // Restore this leaving player to any other players who hid them via BasicParkour
        for (Map.Entry<UUID, Set<UUID>> entry : hiddenByParkour.entrySet()) {
            if (entry.getValue().remove(leavingPlayer.getUniqueId())) {
                Player viewer = Bukkit.getPlayer(entry.getKey());
                if (viewer != null && viewer.isOnline()) {
                    viewer.showPlayer(plugin, leavingPlayer);
                }
            }
        }

        // Restore block visibility so as a spectator they can see generated parkour blocks
        if (plugin.getGeneratorManager() != null) {
            plugin.getGeneratorManager().updateBlockVisibilityForPlayer(leavingPlayer);
        }
    }
}
