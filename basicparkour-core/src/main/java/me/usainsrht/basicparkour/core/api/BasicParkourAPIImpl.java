package me.usainsrht.basicparkour.core.api;

import me.usainsrht.basicparkour.api.BasicParkourAPI;
import me.usainsrht.basicparkour.api.course.ParkourCourse;
import me.usainsrht.basicparkour.api.generator.GeneratorManager;
import me.usainsrht.basicparkour.api.modifier.ModifierRegistry;
import me.usainsrht.basicparkour.api.session.ParkourSession;
import me.usainsrht.basicparkour.api.storage.ParkourRepository;
import me.usainsrht.basicparkour.core.generator.GeneratorManagerImpl;
import me.usainsrht.basicparkour.core.modifier.ModifierRegistryImpl;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The concrete implementation of {@link BasicParkourAPI}, registered as the singleton
 * during plugin startup.
 */
public final class BasicParkourAPIImpl extends BasicParkourAPI {

    private final CourseRegistryImpl courseRegistry;
    private final SessionManager sessionManager;
    private final ModifierRegistryImpl modifierRegistry;
    private final ParkourRepository repository;
    private GeneratorManagerImpl generatorManager;

    public BasicParkourAPIImpl(
        @NotNull SessionManager sessionManager,
        @NotNull ModifierRegistryImpl modifierRegistry,
        @NotNull ParkourRepository repository
    ) {
        this.sessionManager = sessionManager;
        this.modifierRegistry = modifierRegistry;
        this.repository = repository;
        this.courseRegistry = new CourseRegistryImpl(sessionManager);
    }

    @Override @NotNull public CourseRegistryImpl getCourseRegistry() { return courseRegistry; }
    @Override @NotNull public SessionManager getSessionManager() { return sessionManager; }
    @Override @NotNull public ModifierRegistry getModifierRegistry() { return modifierRegistry; }
    @Override @NotNull public ParkourRepository getRepository() { return repository; }
    @Override @NotNull public GeneratorManager getGeneratorManager() {
        if (generatorManager == null)
            throw new IllegalStateException("GeneratorManager not yet initialised.");
        return generatorManager;
    }

    /** Called by BasicParkourPlugin after the generator manager is created. */
    public void setGeneratorManager(@NotNull GeneratorManagerImpl mgr) {
        this.generatorManager = mgr;
    }

    // -----------------------------------------------------------------------
    // Inner: CourseRegistry implementation
    // -----------------------------------------------------------------------

    public static final class CourseRegistryImpl implements BasicParkourAPI.CourseRegistry {

        private final ConcurrentHashMap<String, ParkourCourse> courses = new ConcurrentHashMap<>();
        private final SessionManager sessionManager;

        public CourseRegistryImpl(@NotNull SessionManager sessionManager) {
            this.sessionManager = sessionManager;
        }

        /** Bulk-load courses (called during plugin startup). */
        public void loadAll(@NotNull List<ParkourCourse> loaded) {
            loaded.forEach(c -> courses.put(c.getId().toLowerCase(), c));
        }

        @Override
        @NotNull
        public Collection<ParkourCourse> getCourses() {
            return Collections.unmodifiableCollection(courses.values());
        }

        @Override
        @NotNull
        public Optional<ParkourCourse> getCourse(@NotNull String id) {
            return Optional.ofNullable(courses.get(id.toLowerCase()));
        }

        @Override
        public void registerCourse(@NotNull ParkourCourse course) {
            String key = course.getId().toLowerCase();
            if (courses.containsKey(key)) {
                throw new IllegalArgumentException("Course already registered: " + key);
            }
            courses.put(key, course);
        }

        @Override
        public void unregisterCourse(@NotNull String id) {
            ParkourCourse removed = courses.remove(id.toLowerCase());
            if (removed != null) {
                // Fail all active sessions on this course
                for (ParkourSession session : sessionManager.getActiveSessions()) {
                    if (session.getCourse().getId().equalsIgnoreCase(id)) {
                        session.fail(ParkourSession.FailReason.API);
                    }
                }
            }
        }
    }
}
