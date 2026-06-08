package me.usainsrht.basicparkour.api.generator;

import me.usainsrht.basicparkour.api.session.ParkourSession;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Central API for managing the dynamic parkour generator.
 *
 * <p>Obtain via {@link me.usainsrht.basicparkour.api.BasicParkourAPI#getGeneratorManager()}.</p>
 *
 * <pre>{@code
 * BasicParkourAPI.get().getGeneratorManager()
 *     .getTemplate("forest_medium")
 *     .ifPresent(tpl ->
 *         api.getGeneratorManager().generate(player, tpl)
 *             .thenAccept(session -> player.sendMessage("Course ready!"))
 *     );
 * }</pre>
 */
public interface GeneratorManager {

    // ── Template registry ──────────────────────────────────────────────────

    /**
     * Returns all loaded generator templates.
     *
     * @return unmodifiable collection of templates
     */
    @NotNull
    Collection<GeneratorTemplate> getTemplates();

    /**
     * Looks up a template by its ID (case-insensitive).
     *
     * @param id the template ID
     * @return the template, or empty if not found
     */
    @NotNull
    Optional<GeneratorTemplate> getTemplate(@NotNull String id);

    // ── Instance tracking ──────────────────────────────────────────────────

    /**
     * Returns all currently active generated course instances.
     *
     * @return unmodifiable collection
     */
    @NotNull
    Collection<GeneratedCourseInstance> getActiveInstances();

    /**
     * Returns the active generated course instance for the given player, if any.
     *
     * @param player the player to check
     * @return the instance, or empty
     */
    @NotNull
    Optional<GeneratedCourseInstance> getInstance(@NotNull Player player);

    // ── Generation ─────────────────────────────────────────────────────────

    /**
     * Generates a parkour run from the given template and starts a session for the player.
     *
     * <p>This method is asynchronous: blocks are placed in a background thread, then the
     * session is started on the main/entity thread. The returned future completes with the
     * started {@link ParkourSession}, or {@code null} if the run could not be started
     * (e.g., no slot available, player already in a session, event cancelled).</p>
     *
     * @param player   the player to place into the run
     * @param template the generator template to use
     * @return a future resolving to the started session, or {@code null}
     */
    @NotNull
    CompletableFuture<@Nullable ParkourSession> generate(@NotNull Player player,
                                                          @NotNull GeneratorTemplate template);

    /**
     * Convenience overload that uses a random seed.
     *
     * @param player     the player
     * @param template   the generator template
     * @param customSeed explicit seed to use (overrides template seed if set)
     * @return a future resolving to the started session, or {@code null}
     */
    @NotNull
    CompletableFuture<@Nullable ParkourSession> generate(@NotNull Player player,
                                                          @NotNull GeneratorTemplate template,
                                                          long customSeed);
}
