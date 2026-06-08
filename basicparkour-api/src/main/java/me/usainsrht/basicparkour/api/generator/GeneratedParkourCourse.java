package me.usainsrht.basicparkour.api.generator;

import me.usainsrht.basicparkour.api.course.ParkourCourse;
import org.jetbrains.annotations.NotNull;

/**
 * A {@link ParkourCourse} that was created by the procedural generator.
 *
 * <p>In addition to the standard course contract, a generated course exposes its
 * seed (for replay / sharing) and the template that produced it.</p>
 */
public interface GeneratedParkourCourse extends ParkourCourse {

    /**
     * Returns the random seed that was used to produce this course layout.
     * Two runs with the same seed and the same {@link GeneratorTemplate} will
     * produce an identical block pattern.
     *
     * @return the seed
     */
    long getSeed();

    /**
     * Returns the generator template that created this course.
     *
     * @return the template
     */
    @NotNull
    GeneratorTemplate getTemplate();
}
