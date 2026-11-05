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
     * Light level the (still empty) block position currently receives. Pinning a BlockDisplay to it keeps the
     * display looking the same after the real solid block is placed on that position, because an opaque
     * block cell has light 0 and an unpinned display inside it would flash dark for a moment.
     * Must be called on the region thread that owns {@code loc}.
     */
    @NotNull
    static org.bukkit.entity.Display.Brightness ambientBrightness(@NotNull Location loc) {
        try {
            org.bukkit.block.Block block = loc.getBlock();
            return new org.bukkit.entity.Display.Brightness(
                Math.max(0, Math.min(15, block.getLightFromBlocks())),
                Math.max(0, Math.min(15, block.getLightFromSky())));
        } catch (Throwable t) {
            return new org.bukkit.entity.Display.Brightness(15, 15);
        }
    }

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
