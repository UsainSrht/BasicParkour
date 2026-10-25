package me.usainsrht.basicparkour.api;

import me.usainsrht.basicparkour.api.event.CourseStartEvent;
import me.usainsrht.basicparkour.api.generator.GeneratorManager;
import me.usainsrht.basicparkour.api.modifier.BlockModifier;
import me.usainsrht.basicparkour.api.course.ParkourCourse;
import me.usainsrht.basicparkour.api.modifier.ModifierRegistry;
import me.usainsrht.basicparkour.api.session.ParkourSession;
import me.usainsrht.basicparkour.api.storage.ParkourRepository;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Optional;

/**
 * The primary entry-point for the Basic Parkour API.
 *
 * <p>Obtain the singleton via {@link #get()}. The instance is registered by the
 * core plugin during its {@code onEnable()} lifecycle phase.</p>
 *
 * <pre>{@code
 * BasicParkourAPI api = BasicParkourAPI.get();
 * api.getCourseRegistry().getCourse("example").ifPresent(course -> {
 *     api.getSessionManager().startSession(player, course);
 * });
 * }</pre>
 */
public abstract class BasicParkourAPI {

    private static BasicParkourAPI instance;

    /**
     * Returns the registered singleton instance of the Basic Parkour API.
     *
     * @return the API instance
     * @throws IllegalStateException if the Basic Parkour plugin has not been enabled yet
     */
    @NotNull
    public static BasicParkourAPI get() {
        if (instance == null) {
            throw new IllegalStateException("BasicParkourAPI has not been initialised. Is the Basic Parkour plugin enabled?");
        }
        return instance;
    }

    /**
     * Checks if the BasicParkourAPI singleton has been registered and is available.
     *
     * @return true if the API is ready for use
     */
    public static boolean isAvailable() {
        return instance != null;
    }

    /**
     * Safely returns the registered singleton instance if available, or empty if not initialised yet.
     *
     * @return optional API instance
     */
    @NotNull
    public static Optional<BasicParkourAPI> getOptional() {
        return Optional.ofNullable(instance);
    }

    /**
     * Registers the API singleton. Called exclusively by the Basic Parkour core plugin.
     *
     * @param api the implementation instance
     * @throws IllegalStateException if an instance is already registered
     */
    public static void register(@NotNull BasicParkourAPI api) {
        if (instance != null) {
            throw new IllegalStateException("BasicParkourAPI is already registered.");
        }
        instance = api;
    }

    /**
     * Unregisters the API singleton. Called during plugin shutdown.
     */
    public static void unregister() {
        instance = null;
    }

    // -------------------------------------------------------------------------
    // Abstract API surface
    // -------------------------------------------------------------------------

    /**
     * Returns the course registry, which manages all loaded {@link ParkourCourse} definitions.
     *
     * @return the course registry
     */
    @NotNull
    public abstract CourseRegistry getCourseRegistry();

    /**
     * Returns the session manager, which manages all active {@link ParkourSession} instances.
     *
     * @return the session manager
     */
    @NotNull
    public abstract SessionManager getSessionManager();

    /**
     * Returns the modifier registry, which manages all registered {@link BlockModifier}s.
     *
     * @return the modifier registry
     */
    @NotNull
    public abstract ModifierRegistry getModifierRegistry();

    /**
     * Returns the data repository used for persisting player records and leaderboards.
     *
     * @return the parkour repository
     */
    @NotNull
    public abstract ParkourRepository getRepository();

    /**
     * Returns the generator manager, which manages dynamic procedural parkour generation.
     *
     * @return the generator manager
     */
    @NotNull
    public abstract GeneratorManager getGeneratorManager();

    /**
     * Returns the ghost recorder, which manages recording and replaying player ghost runs.
     *
     * @return the ghost recorder
     */
    @NotNull
    public abstract me.usainsrht.basicparkour.api.ghost.GhostRecorder getGhostRecorder();

    // -------------------------------------------------------------------------
    // Nested interface: CourseRegistry
    // -------------------------------------------------------------------------

    /**
     * Manages the collection of loaded {@link ParkourCourse} definitions.
     */
    public interface CourseRegistry {

        /**
         * Returns all currently loaded courses.
         *
         * @return an unmodifiable collection of courses
         */
        @NotNull
        Collection<ParkourCourse> getCourses();

        /**
         * Finds a course by its unique identifier.
         *
         * @param id the course ID (case-insensitive)
         * @return an {@link Optional} containing the course, or empty if not found
         */
        @NotNull
        Optional<ParkourCourse> getCourse(@NotNull String id);

        /**
         * Registers a course definition at runtime. Useful for API consumers that
         * wish to define courses programmatically rather than through YAML files.
         *
         * @param course the course to register
         * @throws IllegalArgumentException if a course with the same ID is already registered
         */
        void registerCourse(@NotNull ParkourCourse course);

        /**
         * Unregisters a course. Any active sessions on this course are immediately failed.
         *
         * @param id the course ID to remove
         */
        void unregisterCourse(@NotNull String id);
    }

    // -------------------------------------------------------------------------
    // Nested interface: SessionManager
    // -------------------------------------------------------------------------

    /**
     * Manages all active {@link ParkourSession} instances.
     */
    public interface SessionManager {

        /**
         * Starts a new parkour session for the given player on the specified course.
         * Fires {@link CourseStartEvent}; if cancelled, the session is not created.
         *
         * @param player the player
         * @param course the course to run
         * @return the created session, or {@code null} if the event was cancelled
         */
        @Nullable
        ParkourSession startSession(@NotNull Player player, @NotNull ParkourCourse course);

        /**
         * Starts a new parkour session with configurable initial teleportation.
         *
         * @param player         the player
         * @param course         the course to run
         * @param shouldTeleport whether to teleport the player to the course start location
         * @return the created session, or null if cancelled
         */
        @Nullable
        default ParkourSession startSession(@NotNull Player player, @NotNull ParkourCourse course, boolean shouldTeleport) {
            return startSession(player, course);
        }

        /**
         * Returns the active session for the given player, or {@link Optional#empty()}.
         *
         * @param player the player
         * @return an optional session
         */
        @NotNull
        Optional<ParkourSession> getSession(@NotNull Player player);

        /**
         * Returns all currently active sessions.
         *
         * @return an unmodifiable collection of sessions
         */
        @NotNull
        Collection<ParkourSession> getActiveSessions();

        /**
         * Returns whether the given player is currently running a parkour course.
         *
         * @param player the player
         * @return true if player has an active session
         */
        default boolean isPlayerInSession(@NotNull Player player) {
            return getSession(player).isPresent();
        }

        /**
         * Restarts the player's active parkour session from the beginning.
         *
         * @param player the player
         */
        default void restartSession(@NotNull Player player) {}

        /**
         * Leaves the player's active parkour session cleanly and teleports them to their exit/join location.
         *
         * @param player the player
         */
        default void leaveSession(@NotNull Player player) {
            leaveSession(player, true);
        }

        /**
         * Leaves the player's active parkour session cleanly with configurable teleportation.
         *
         * @param player   the player
         * @param teleport true to teleport back to exit/join location
         */
        default void leaveSession(@NotNull Player player, boolean teleport) {}

        /**
         * Teleports the player back to their last reached checkpoint (or start location).
         *
         * @param player the player
         */
        default void teleportToCheckpoint(@NotNull Player player) {}

        /**
         * Pauses an active session for the given player.
         *
         * @param player the player
         * @return true if paused, false if player is not in a session or already paused
         */
        default boolean pauseSession(@NotNull Player player) {
            return false;
        }

        /**
         * Resumes a paused session for the given player.
         *
         * @param player the player
         * @return true if resumed, false if not paused or not in session
         */
        default boolean resumeSession(@NotNull Player player) {
            return false;
        }

        /**
         * Gets the player's current visibility mode (ALL, TRANSLUCENT, NONE).
         *
         * @param player the player
         * @return visibility mode name
         */
        @NotNull
        default String getVisibilityMode(@NotNull Player player) {
            return "ALL";
        }

        /**
         * Sets the player's parkour visibility mode.
         *
         * @param player the player
         * @param mode   the visibility mode (ALL, TRANSLUCENT, NONE)
         */
        default void setVisibilityMode(@NotNull Player player, @NotNull String mode) {}

        /**
         * Cycles the player's visibility mode (ALL -> TRANSLUCENT -> NONE -> ALL).
         *
         * @param player the player
         * @return the new visibility mode
         */
        @NotNull
        default String cycleVisibility(@NotNull Player player) {
            return "ALL";
        }
    }
}
