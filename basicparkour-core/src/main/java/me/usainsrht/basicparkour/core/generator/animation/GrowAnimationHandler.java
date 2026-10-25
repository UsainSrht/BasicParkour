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
 * GROW animation:
 * A BlockDisplay entity spawns at its final location with near-zero scale, smoothly expands
 * into a full block, and then switches to a solid world block.
 */
public final class GrowAnimationHandler implements BlockAnimationHandler {

    public static final GrowAnimationHandler INSTANCE = new GrowAnimationHandler();

    private static final int GROW_TICKS = 5;

    private GrowAnimationHandler() {}

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

        // Initial state: 0.01 scale, centered in the block
        Vector3f initialScale = new Vector3f(0.01f, 0.01f, 0.01f);
        Vector3f initialTranslation = new Vector3f(0.495f, 0.495f, 0.495f);

        try {
            BlockDisplay display = targetLoc.getWorld().spawn(targetLoc, BlockDisplay.class, d -> {
                d.setBlock(pb.desiredMaterial().createBlockData());
                d.setTransformation(new Transformation(
                    initialTranslation,
                    new AxisAngle4f(),
                    initialScale,
                    new AxisAngle4f()
                ));
            });

            instance.activeDisplays.add(display);

            // Tick 1: Trigger smooth expansion to full 1.0 scale
            scheduler.runAtLocationLater(targetLoc, () -> {
                if (!instance.isActive() || display.isDead()) {
                    instance.activeDisplays.remove(display);
                    try { display.remove(); } catch (Exception ignored) {}
                    return;
                }
                display.setInterpolationDelay(0);
                display.setInterpolationDuration(GROW_TICKS);
                display.setTransformation(new Transformation(
                    new Vector3f(0.0f, 0.0f, 0.0f),
                    new AxisAngle4f(),
                    new Vector3f(1.0f, 1.0f, 1.0f),
                    new AxisAngle4f()
                ));

                // When grow finishes, place real block first, then remove display entity
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
                }, GROW_TICKS);
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
