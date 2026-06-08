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
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
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

    /** Called by the plugin during onDisable to stop tasks and clean up sessions. */
    public void shutdown() {
        if (timerTask != null) timerTask.cancel();
        // Invalidate all active sessions
        sessions.values().forEach(s -> {
            s.tryInvalidate();
            Bukkit.getPluginManager().callEvent(
                new SessionFailEvent(s.getPlayer(), s, FailReason.DISCONNECT)
            );
        });
        sessions.clear();
        teamManager.cleanup();
    }

    // -----------------------------------------------------------------------
    // BasicParkourAPI.SessionManager implementation
    // -----------------------------------------------------------------------

    @Override
    @Nullable
    public ParkourSession startSession(@NotNull Player player, @NotNull ParkourCourse course) {
        // Disallow starting if already in a session
        ParkourSessionImpl existing = sessions.get(player.getUniqueId());
        if (existing != null) return existing;

        // Fire cancellable event
        CourseStartEvent event = new CourseStartEvent(player, course);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return null;

        // Create session
        ParkourSessionImpl session = new ParkourSessionImpl(player, course, this);
        sessions.put(player.getUniqueId(), session);

        // Add to no-collision team so parkour players can't push each other
        teamManager.addPlayer(player);

        // Apply visibility (hide other parkour players from each other)
        applyVisibility(player);

        // Start ghost recording if enabled
        if (plugin.getConfig().getBoolean("ghost.enabled", true)) {
            plugin.getGhostRecorder().startRecording(player);
        }

        // Notify player
        player.sendActionBar(miniMessage.deserialize(messages.getCourseStart(),
            Placeholder.component("course", course.getDisplayName())));

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

        // Notify player
        String msg = messages.getCheckpointReach()
            .replace("{checkpoint}", String.valueOf(checkpoint.getIndex() + 1))
            .replace("{time}", snapshot.formatted());
        player.sendActionBar(miniMessage.deserialize(msg));

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

        // Remove from no-collision team
        teamManager.removePlayer(player);

        // Restore visibility
        restoreVisibility(player);

        // Stop ghost recording
        if (plugin.getConfig().getBoolean("ghost.enabled", true)) {
            plugin.getGhostRecorder()
                .saveRecording(player, session.getCourse().getId(), elapsed)
                .exceptionally(ex -> {
                    plugin.getLogger().log(Level.WARNING, "Failed to save ghost recording", ex);
                    return null;
                });
        }

        // Save record asynchronously, then fire event back on main thread
        PlayerRecord newRecord = PlayerRecord.of(
            player.getUniqueId(), player.getName(),
            session.getCourse().getId(), elapsed
        );

        plugin.getRepository().getPersonalBest(player.getUniqueId(), session.getCourse().getId())
            .thenCompose(previousBest -> plugin.getRepository().saveRecord(newRecord)
                .thenAccept(isNewBest -> {
                    // Schedule event fire on appropriate thread
                    scheduler.runOnEntity(player, () -> {
                        CourseCompleteEvent ev = new CourseCompleteEvent(
                            player, session, elapsed, isNewBest, previousBest.orElse(null)
                        );
                        Bukkit.getPluginManager().callEvent(ev);

                        // Display completion message
                        String msg = messages.getCourseComplete()
                            .replace("{time}", TimerFormatter.format(elapsed));
                        player.sendMessage(miniMessage.deserialize(msg));

                        if (isNewBest) {
                            player.sendMessage(miniMessage.deserialize(
                                messages.getPersonalBest()
                                    .replace("{time}", TimerFormatter.format(elapsed))
                            ));
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

        // Determine respawn location: last checkpoint or start center
        Location respawnLoc = session.getLastCheckpoint()
            .map(Checkpoint::getSpawnLocation)
            .orElseGet(() -> session.getCourse().getStartRegion().getCenter());

        // Reset session state to ACTIVE so the player can continue from checkpoint
        // We do this by creating a fresh session pinned to the last checkpoint index
        sessions.remove(player.getUniqueId());

        // Fire event
        Bukkit.getPluginManager().callEvent(new SessionFailEvent(player, session, reason));

        // Instant respawn via entity scheduler
        scheduler.runOnEntity(player, () -> {
            player.teleportAsync(respawnLoc).thenRun(() -> {
                // Show fail message
                player.sendActionBar(miniMessage.deserialize(messages.getSessionFail()));

                // Restart a fresh session on the same course, pre-seeded with checkpoint
                ParkourSessionImpl newSession = new ParkourSessionImpl(
                    player, session.getCourse(), this
                );
                // Fast-forward the checkpoint index to where they were
                int cpIdx = session.getCurrentCheckpointIndex();
                for (int i = 0; i <= cpIdx; i++) {
                    newSession.advanceCheckpoint();
                }
                sessions.put(player.getUniqueId(), newSession);
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
            teamManager.removePlayer(player);
            restoreVisibility(player);
            if (plugin.getConfig().getBoolean("ghost.enabled", true)) {
                plugin.getGhostRecorder().discardRecording(player);
            }
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
            String timerMsg = messages.getTimerDisplay()
                .replace("{time}", snapshot.formatted())
                .replace("{checkpoint}", String.valueOf(snapshot.checkpointIndex() + 1))
                .replace("{total}", String.valueOf(session.getCourse().getCheckpoints().size()));
            player.sendActionBar(miniMessage.deserialize(timerMsg));
        });
    }

    // -----------------------------------------------------------------------
    // Visibility management
    // -----------------------------------------------------------------------

    private void applyVisibility(@NotNull Player newPlayer) {
        String mode = plugin.getConfig().getString("visibility.default", "ALL");
        for (ParkourSessionImpl other : sessions.values()) {
            Player otherPlayer = other.getPlayer();
            if (otherPlayer.equals(newPlayer)) continue;
            switch (mode) {
                case "NONE" -> {
                    newPlayer.hidePlayer(plugin, otherPlayer);
                    otherPlayer.hidePlayer(plugin, newPlayer);
                }
                // FRIENDS would require FriendsProvider SPI — fallthrough to ALL
                default -> {
                    newPlayer.showPlayer(plugin, otherPlayer);
                    otherPlayer.showPlayer(plugin, newPlayer);
                }
            }
        }
    }

    private void restoreVisibility(@NotNull Player leavingPlayer) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.showPlayer(plugin, leavingPlayer);
            leavingPlayer.showPlayer(plugin, online);
        }
    }
}
