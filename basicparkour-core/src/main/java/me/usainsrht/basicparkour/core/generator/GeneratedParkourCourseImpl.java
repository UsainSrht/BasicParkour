package me.usainsrht.basicparkour.core.generator;

import me.usainsrht.basicparkour.api.course.Checkpoint;
import me.usainsrht.basicparkour.api.course.CourseRegion;
import me.usainsrht.basicparkour.api.generator.GeneratedParkourCourse;
import me.usainsrht.basicparkour.api.generator.GeneratorTemplate;
import me.usainsrht.basicparkour.core.course.CourseRegionImpl;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Map;

/**
 * Concrete implementation of {@link GeneratedParkourCourse}.
 *
 * <p>This object is created by {@link GeneratorManagerImpl} after blocks have been placed.
 * It implements the full {@link me.usainsrht.basicparkour.api.course.ParkourCourse} contract
 * so it can be handed directly to {@link me.usainsrht.basicparkour.core.session.SessionManager}.</p>
 *
 * <p>For infinite runs, the end region is set to an impossible location (Y = Integer.MIN_VALUE)
 * so it is never triggered by normal movement. Session completion is instead triggered
 * programmatically by {@link GeneratorManagerImpl} when the player reaches the last block
 * in a FIXED run, or never (for INFINITE).</p>
 */
public final class GeneratedParkourCourseImpl implements GeneratedParkourCourse {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final String id;
    private final Component displayName;
    private final GeneratorTemplate template;
    private final long seed;
    private final String worldName;
    private final CourseRegion startRegion;
    private final CourseRegion endRegion;
    private final List<Checkpoint> checkpoints;

    public GeneratedParkourCourseImpl(
        @NotNull String id,
        @NotNull GeneratorTemplate template,
        long seed,
        @NotNull String worldName,
        @NotNull CourseRegion startRegion,
        @NotNull CourseRegion endRegion,
        @NotNull List<Checkpoint> checkpoints
    ) {
        this.id           = id;
        this.displayName  = MINI.deserialize(template.displayName());
        this.template     = template;
        this.seed         = seed;
        this.worldName    = worldName;
        this.startRegion  = startRegion;
        this.endRegion    = endRegion;
        this.checkpoints  = List.copyOf(checkpoints);
    }

    // ── ParkourCourse ──────────────────────────────────────────────────────

    @Override @NotNull public String getId()              { return id; }
    @Override @NotNull public Component getDisplayName()  { return displayName; }
    @Override @NotNull public String getWorldName()       { return worldName; }
    @Override @NotNull public CourseRegion getStartRegion() { return startRegion; }
    @Override @NotNull public CourseRegion getEndRegion()   { return endRegion; }
    @Override @NotNull @Unmodifiable public List<Checkpoint> getCheckpoints() { return checkpoints; }

    @Override @NotNull @Unmodifiable
    public Map<Material, String> getModifierOverrides() {
        return template.modifierOverrides();
    }

    @Override @NotNull @Unmodifiable
    public List<String> getRewardCommands() {
        return List.of(); // generated runs don't have reward commands
    }

    // ── GeneratedParkourCourse ─────────────────────────────────────────────

    @Override public long getSeed()              { return seed; }
    @Override @NotNull public GeneratorTemplate getTemplate() { return template; }

    // ── Helpers ────────────────────────────────────────────────────────────

    /**
     * Adds a checkpoint to the live list (used by the sliding window generator
     * to append new platforms to an ongoing infinite run).
     * The list is re-copied to remain unmodifiable from the outside.
     *
     * <p><strong>Note:</strong> this is only called from the generator thread
     * which holds exclusive access to the checkpoint list while the window advances.</p>
     */
    public void appendCheckpoint(@NotNull Checkpoint cp) {
        // The checkpoints field is List.copyOf, so we need a mutable wrapper.
        // GeneratorManagerImpl holds the mutable list and passes a view here.
        throw new UnsupportedOperationException(
            "Use GeneratorManagerImpl.appendCheckpoint(instanceId, cp) instead.");
    }

    @Override
    public String toString() {
        return "GeneratedParkourCourse{id='%s', seed=%d, template='%s'}"
            .formatted(id, seed, template.id());
    }
}
