package me.usainsrht.basicparkour.core.generator.animation;

import me.usainsrht.basicparkour.core.generator.GeneratedCourseInstanceImpl;
import me.usainsrht.basicparkour.core.generator.PlacedBlock;
import me.usainsrht.basicparkour.core.scheduling.FoliaScheduler;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Places the block directly without any display entity or delay.
 */
public final class NoneAnimationHandler implements BlockAnimationHandler {

    public static final NoneAnimationHandler INSTANCE = new NoneAnimationHandler();

    private NoneAnimationHandler() {}

    @Override
    public void animateAndPlace(
        @NotNull GeneratedCourseInstanceImpl instance,
        @NotNull PlacedBlock pb,
        @Nullable Location previousPlatformLocation,
        @NotNull FoliaScheduler scheduler,
        @NotNull Runnable onComplete
    ) {
        if (!instance.isActive()) return;
        instance.placeBlock(pb);
        onComplete.run();
    }
}
