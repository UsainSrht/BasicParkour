package me.usainsrht.basicparkour.core.generator.animation;

import me.usainsrht.basicparkour.core.generator.GeneratedCourseInstanceImpl;
import me.usainsrht.basicparkour.core.generator.PlacedBlock;
import me.usainsrht.basicparkour.core.scheduling.FoliaScheduler;
import org.bukkit.Location;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * DEFAULT animation:
 * A BlockDisplay entity is spawned visually at the previous platform location and slides smoothly
 * (interpolating translation) to its final position, after which the display is removed and the real
 * solid block is placed.
 */
public final class DefaultSlideAnimationHandler implements BlockAnimationHandler {

    public static final DefaultSlideAnimationHandler INSTANCE = new DefaultSlideAnimationHandler();

    private static final int SLIDE_TICKS = 5;
    private static final float DISPLAY_SCALE = 0.9999f;
    private static final float DISPLAY_OFFSET = (1.0f - DISPLAY_SCALE) / 2.0f;

    private DefaultSlideAnimationHandler() {}

    @Override
    public void animateAndPlace(
        @NotNull GeneratedCourseInstanceImpl instance,
        @NotNull PlacedBlock pb,
        @Nullable Location previousPlatformLocation,
        @NotNull FoliaScheduler scheduler,
        @NotNull Runnable onComplete
    ) {
        if (!instance.isActive()) return;
        Location targetLoc = pb.location();

        // If no previous location or mismatched world, place immediately
        if (previousPlatformLocation == null || !previousPlatformLocation.getWorld().equals(targetLoc.getWorld())) {
            instance.placeBlock(pb);
            onComplete.run();
            return;
        }

        // Relative offset from target block to previous platform (inset by DISPLAY_OFFSET to eliminate Z-fighting)
        float dx = (float) (previousPlatformLocation.getX() - targetLoc.getX()) + DISPLAY_OFFSET;
        float dy = (float) (previousPlatformLocation.getY() - targetLoc.getY()) + DISPLAY_OFFSET;
        float dz = (float) (previousPlatformLocation.getZ() - targetLoc.getZ()) + DISPLAY_OFFSET;
        Vector3f initialTranslation = new Vector3f(dx, dy, dz);
        Vector3f blockScale = new Vector3f(DISPLAY_SCALE, DISPLAY_SCALE, DISPLAY_SCALE);

        try {
            BlockDisplay display = targetLoc.getWorld().spawn(targetLoc, BlockDisplay.class, d -> {
                d.setBlock(pb.desiredMaterial().createBlockData());
                d.setTransformation(new Transformation(
                    initialTranslation,
                    new AxisAngle4f(),
                    blockScale,
                    new AxisAngle4f()
                ));
            });

            instance.activeDisplays.add(display);

            // Tick 1: Trigger smooth interpolation towards (DISPLAY_OFFSET, DISPLAY_OFFSET, DISPLAY_OFFSET)
            scheduler.runAtLocationLater(targetLoc, () -> {
                if (!instance.isActive() || display.isDead()) {
                    instance.activeDisplays.remove(display);
                    try { display.remove(); } catch (Exception ignored) {}
                    return;
                }
                display.setInterpolationDelay(0);
                display.setInterpolationDuration(SLIDE_TICKS);
                display.setTransformation(new Transformation(
                    new Vector3f(DISPLAY_OFFSET, DISPLAY_OFFSET, DISPLAY_OFFSET),
                    new AxisAngle4f(),
                    blockScale,
                    new AxisAngle4f()
                ));

                // When slide finishes, place real block first, then remove display entity
                // 2 ticks later to eliminate the 1-frame air flicker while the client chunk mesher updates.
                scheduler.runAtLocationLater(targetLoc, () -> {
                    if (!instance.isActive()) {
                        instance.activeDisplays.remove(display);
                        try { display.remove(); } catch (Exception ignored) {}
                        return;
                    }
                    instance.placeBlock(pb);
                    onComplete.run();

                    scheduler.runAtLocationLater(targetLoc, () -> {
                        instance.activeDisplays.remove(display);
                        try { display.remove(); } catch (Exception ignored) {}
                    }, 2L);
                }, SLIDE_TICKS);
            }, 1L);
        } catch (Throwable t) {
            // Fallback to instant placement if entity spawn fails
            if (instance.isActive()) {
                instance.placeBlock(pb);
                onComplete.run();
            }
        }
    }
}
