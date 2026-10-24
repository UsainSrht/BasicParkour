package me.usainsrht.basicparkour.core.session;

import me.usainsrht.basicparkour.api.BasicParkourAPI;
import me.usainsrht.basicparkour.api.course.Checkpoint;
import me.usainsrht.basicparkour.api.course.ParkourCourse;
import me.usainsrht.basicparkour.api.event.CheckpointReachEvent;
import me.usainsrht.basicparkour.api.event.CourseCompleteEvent;
import me.usainsrht.basicparkour.api.event.CourseStartEvent;
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

        // Must be the next expected checkpoint
        int expected = session.getCurrentCheckpointIndex() + 1;
        if (checkpoint.getIndex() != expected) return false;

        // Take snapshot before advancing
        var snapshot = session.getTimerSnapshot();

        // Fire event (cancellable)
        CheckpointReachEvent event = new CheckpointReachEvent(player, session, checkpoint, snapshot);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return false;

        // Advance
        session.advanceCheckpoint();

        // Notify player via YamlMessage for standard courses
        // Endless courses display continuous live block count via timer-display-endless
        boolean isEndless = session.getCourse() instanceof me.usainsrht.basicparkour.api.generator.GeneratedParkourCourse gpc
                && gpc.getTemplate().lengthType() == me.usainsrht.basicparkour.api.generator.LengthType.INFINITE;
        if (!isEndless) {
            messages.send(messages.getCheckpointReach(), player,
                Placeholder.parsed("checkpoint", String.valueOf(checkpoint.getIndex() + 1)),
                Placeholder.parsed("time", snapshot.formatted()));
        }

        return true;
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
        if (!session.tryComplete()) return; // Another thread already completed/failed

        long elapsed = session.getElapsedMillis();
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

        // Teleport on completion if configured (e.g. JOIN_LOCATION or START_LOCATION)
        String tpOnComplete = plugin.getConfig().getString("session.teleport-on-complete", "NONE").toUpperCase(java.util.Locale.ROOT);
        if (tpOnComplete.equals("JOIN_LOCATION")) {
            Location joinLoc = session.getJoinLocation();
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

        plugin.getRepository().getPersonalBest(player.getUniqueId(), targetCourseId)
            .thenCompose(previousBest -> plugin.getRepository().saveRecord(newRecord)
                .thenAccept(isNewBest -> {
                    // Refresh holograms and top holder NPCs for this course
                    if (plugin.getHologramManager() != null) {
                        plugin.getHologramManager().refreshCourseHolograms(targetCourseId);
                    }
                    if (plugin.getNpcManager() != null) {
                        plugin.getNpcManager().updateTopHolders(targetCourseId);
                    }

                    // Schedule event fire on appropriate thread
                    scheduler.runOnEntity(player, () -> {
                        CourseCompleteEvent ev = new CourseCompleteEvent(
                            player, session, elapsed, isNewBest, previousBest.orElse(null)
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
                        for (String cmd : session.getCourse().getRewardCommands()) {
                            String resolved = cmd.replace("{player}", player.getName());
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
                        }
                    });
                })
            )
            .exceptionally(ex -> {
                plugin.getLogger().log(Level.SEVERE, "Failed to save record for " + player.getName(), ex);
                return null;
            });
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
        if (!session.tryFail()) return;

        me.usainsrht.basicparkour.api.course.DieMode dieMode = session.getCourse().getDieMode();
        String onFail = plugin.getConfig().getString("session.on-fail", "COURSE").toUpperCase(java.util.Locale.ROOT);
        if (onFail.equals("CHECKPOINT")) {
            dieMode = me.usainsrht.basicparkour.api.course.DieMode.RESTART_DIE;
        } else if (onFail.equals("JOIN_LOCATION")) {
            dieMode = me.usainsrht.basicparkour.api.course.DieMode.FAIL_DIE;
        }
        if (session.getCourse() instanceof me.usainsrht.basicparkour.api.generator.GeneratedParkourCourse gpc
                && gpc.getTemplate().lengthType() == me.usainsrht.basicparkour.api.generator.LengthType.INFINITE) {
            dieMode = me.usainsrht.basicparkour.api.course.DieMode.FAIL_DIE;
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
                        plugin.getHologramManager().refreshCourseHolograms(leaderboardCourseId);
                    }
                    if (plugin.getNpcManager() != null) {
                        plugin.getNpcManager().updateTopHolders(leaderboardCourseId);
                    }
                });
            }
        }
        final me.usainsrht.basicparkour.api.course.DieMode finalDieMode = dieMode;

        Location respawnLoc;
        if (dieMode == me.usainsrht.basicparkour.api.course.DieMode.FAIL_DIE) {
            respawnLoc = session.getJoinLocation();
            restorePlayerState(player);
        } else {
            // Determine respawn location: last checkpoint or start center
            respawnLoc = session.getLastCheckpoint()
                .map(Checkpoint::getSpawnLocation)
                .orElseGet(() -> {
                    if (!session.getCourse().getStartLocations().isEmpty()) {
                        java.util.List<Location> locs = session.getCourse().getStartLocations();
                        return locs.get(new java.util.Random().nextInt(locs.size()));
                    }
                    return session.getCourse().getStartRegion().getCenter();
                });
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
                messages.send(messages.getSessionFail(), player,
                    Placeholder.component("course", session.getCourse().getDisplayName()));

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
    public void leaveSession(@NotNull Player player) {
        ParkourSessionImpl session = sessions.get(player.getUniqueId());
        if (session == null) {
            messages.send(messages.getNotInSession(), player);
            return;
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
                        plugin.getHologramManager().refreshCourseHolograms(leaderboardCourseId);
                    }
                    if (plugin.getNpcManager() != null) {
                        plugin.getNpcManager().updateTopHolders(leaderboardCourseId);
                    }
                });
            }
        }
        Location joinLoc = session.getJoinLocation();
        boolean isGen = session.getCourse() instanceof me.usainsrht.basicparkour.api.generator.GeneratedParkourCourse;
        removeSession(player);
        messages.send(messages.getSessionLeave(), player);

        if (isGen || plugin.getConfig().getBoolean("session.teleport-on-leave", true)) {
            markInternalTeleport(player.getUniqueId());
            scheduler.runOnEntity(player, () ->
                player.teleportAsync(joinLoc).thenRun(() -> clearInternalTeleport(player.getUniqueId())));
        }
    }

    /**
     * Restarts the player's active session from the beginning.
     */
    public void restartSession(@NotNull Player player) {
        ParkourSessionImpl session = sessions.get(player.getUniqueId());
        if (session == null) {
            messages.send(messages.getNotInSession(), player);
            return;
        }
        ParkourCourse course = session.getCourse();
        Location joinLoc = session.getJoinLocation();

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

        Location startLoc = null;
        if (!course.getStartLocations().isEmpty()) {
            var locs = course.getStartLocations();
            startLoc = locs.get(new java.util.Random().nextInt(locs.size()));
        } else if (!(course instanceof me.usainsrht.basicparkour.api.generator.GeneratedParkourCourse)) {
            startLoc = course.getStartRegion().getCenter();
        }

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

        messages.send(messages.getTeleportedToCheckpoint(), player);
        markInternalTeleport(player.getUniqueId());
        scheduler.runOnEntity(player, () ->
            player.teleportAsync(targetLoc).thenRun(() -> clearInternalTeleport(player.getUniqueId())));
    }

    /**
     * Cycles the player's visibility mode between ALL -> TRANSLUCENT -> NONE -> ALL.
     */
    public @NotNull String cycleVisibility(@NotNull Player player) {
        String current = getVisibilityMode(player);
        String next = switch (current) {
            case "ALL" -> "TRANSLUCENT";
            case "TRANSLUCENT" -> "NONE";
            default -> "ALL";
        };
        setVisibilityMode(player, next);
        applyVisibility(player);
        messages.send(messages.getVisibilityToggled(), player,
            Placeholder.parsed("mode", next));
        return next;
    }

    public @NotNull String getVisibilityMode(@NotNull Player player) {
        return playerVisibilityModes.computeIfAbsent(player.getUniqueId(),
            u -> plugin.getConfig().getString("visibility.default", "ALL").toUpperCase(java.util.Locale.ROOT));
    }

    public void setVisibilityMode(@NotNull Player player, @NotNull String mode) {
        playerVisibilityModes.put(player.getUniqueId(), mode.toUpperCase(java.util.Locale.ROOT));
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
            boolean isEndless = session.getCourse() instanceof me.usainsrht.basicparkour.api.generator.GeneratedParkourCourse gpc
                    && gpc.getTemplate().lengthType() == me.usainsrht.basicparkour.api.generator.LengthType.INFINITE;

            if (isEndless) {
                int blocks = session.getCurrentCheckpointIndex();
                messages.send(messages.getTimerDisplayEndless(), player,
                    Placeholder.parsed("time", snapshot.formatted()),
                    Placeholder.parsed("blocks", String.valueOf(blocks)),
                    Placeholder.parsed("score", String.valueOf(blocks)));
            } else {
                messages.send(messages.getTimerDisplay(), player,
                    Placeholder.parsed("time", snapshot.formatted()),
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
