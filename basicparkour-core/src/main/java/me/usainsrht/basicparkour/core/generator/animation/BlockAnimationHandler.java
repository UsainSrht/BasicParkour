package me.usainsrht.basicparkour.core.generator.animation;

import me.usainsrht.basicparkour.api.generator.BlockAppearAnimation;
import me.usainsrht.basicparkour.core.generator.GeneratedCourseInstanceImpl;
import me.usainsrht.basicparkour.core.generator.PlacedBlock;
import me.usainsrht.basicparkour.core.scheduling.FoliaScheduler;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Strategy interface for animating newly generated parkour blocks in endless/sliding runs.
 *
 * <p>Implementations must execute on the region thread owning the target block's chunk.</p>
 */
public interface BlockAnimationHandler {

    /**
     * Executes the block appear animation, then places the real block and invokes {@code onComplete}.
     *
     * @param instance                 the generated course instance
     * @param pb                       the block to be placed
     * @param previousPlatformLocation the location of the previous platform center, if known
     * @param scheduler                the Folia scheduler handle
     * @param onComplete               runnable to execute when the block placement is finished
     */
    void animateAndPlace(
        @NotNull GeneratedCourseInstanceImpl instance,
        @NotNull PlacedBlock pb,
        @Nullable Location previousPlatformLocation,
        @NotNull FoliaScheduler scheduler,
        @NotNull Runnable onComplete
    );

    /**
     * Resolves the handler for the given animation type.
     */
    @NotNull
    static BlockAnimationHandler of(@NotNull BlockAppearAnimation animation) {
        return switch (animation) {
            case NONE -> NoneAnimationHandler.INSTANCE;
            case DEFAULT -> DefaultSlideAnimationHandler.INSTANCE;
            case GROW -> GrowAnimationHandler.INSTANCE;
        };
    }
}
