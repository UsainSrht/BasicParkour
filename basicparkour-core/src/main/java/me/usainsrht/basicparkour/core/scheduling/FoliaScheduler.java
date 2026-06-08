package me.usainsrht.basicparkour.core.scheduling;

import me.usainsrht.basicparkour.core.BasicParkourPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

/**
 * Thread-safe scheduling utility that transparently supports both Folia and Paper.
 *
 * <h2>Folia vs Paper detection</h2>
 * On startup, {@link #isFolia()} is computed once by probing for
 * {@code io.papermc.paper.threadedregions.RegionizedServer}. All scheduling methods
 * delegate to the appropriate scheduler based on this flag.
 *
 * <h2>Usage rules (Folia)</h2>
 * <ul>
 *   <li>Entity-related tasks → {@link #runOnEntity(Entity, Runnable)}</li>
 *   <li>Block/chunk-related tasks → {@link #runAtLocation(Location, Runnable)}</li>
 *   <li>Server-wide state → {@link #runGlobal(Runnable)}</li>
 *   <li>I/O, DB queries → {@link #runAsync(Runnable)}</li>
 * </ul>
 */
public final class FoliaScheduler {

    /** Cached result of the Folia class probe. */
    private static final boolean FOLIA;

    static {
        boolean folia;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            folia = true;
        } catch (ClassNotFoundException e) {
            folia = false;
        }
        FOLIA = folia;
    }

    private final BasicParkourPlugin plugin;

    public FoliaScheduler(@NotNull BasicParkourPlugin plugin) {
        this.plugin = plugin;
    }

    /** @return {@code true} if running on Folia */
    public static boolean isFolia() {
        return FOLIA;
    }

    // -----------------------------------------------------------------------
    // One-shot tasks
    // -----------------------------------------------------------------------

    /**
     * Runs a task on the entity's owning region (Folia) or the main thread (Paper).
     * Safe for modifying entity state.
     *
     * @param entity   target entity
     * @param runnable the task to run
     */
    public void runOnEntity(@NotNull Entity entity, @NotNull Runnable runnable) {
        if (FOLIA) {
            entity.getScheduler().run(plugin, t -> runnable.run(), null);
        } else {
            Bukkit.getScheduler().runTask(plugin, runnable);
        }
    }

    /**
     * Runs a task at a specific world location (Folia RegionScheduler) or on the
     * main thread (Paper).
     *
     * @param location target location
     * @param runnable the task to run
     */
    public void runAtLocation(@NotNull Location location, @NotNull Runnable runnable) {
        if (FOLIA) {
            Bukkit.getRegionScheduler().run(plugin, location, t -> runnable.run());
        } else {
            Bukkit.getScheduler().runTask(plugin, runnable);
        }
    }

    /**
     * Runs a task on the global region thread (Folia) or main thread (Paper).
     * Use for server-wide operations not tied to a specific world location.
     *
     * @param runnable the task to run
     */
    public void runGlobal(@NotNull Runnable runnable) {
        if (FOLIA) {
            Bukkit.getGlobalRegionScheduler().run(plugin, t -> runnable.run());
        } else {
            Bukkit.getScheduler().runTask(plugin, runnable);
        }
    }

    /**
     * Runs a task asynchronously. Safe for I/O and database operations.
     * Do NOT access Bukkit API from inside this runnable on Folia.
     *
     * @param runnable the task to run
     */
    public void runAsync(@NotNull Runnable runnable) {
        if (FOLIA) {
            Bukkit.getAsyncScheduler().runNow(plugin, t -> runnable.run());
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable);
        }
    }

    // -----------------------------------------------------------------------
    // Delayed tasks
    // -----------------------------------------------------------------------

    /**
     * Schedules a task to run on the entity's owning region after a delay.
     *
     * @param entity     target entity
     * @param runnable   the task to run
     * @param delayTicks delay in ticks
     */
    public void runOnEntityLater(@NotNull Entity entity, @NotNull Runnable runnable, long delayTicks) {
        if (FOLIA) {
            entity.getScheduler().runDelayed(plugin, t -> runnable.run(), null, Math.max(1, delayTicks));
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, runnable, Math.max(1, delayTicks));
        }
    }

    /**
     * Schedules a task to run at a location after a delay.
     *
     * @param location   target location
     * @param runnable   the task to run
     * @param delayTicks delay in ticks
     */
    public void runAtLocationLater(@NotNull Location location, @NotNull Runnable runnable, long delayTicks) {
        if (FOLIA) {
            Bukkit.getRegionScheduler().runDelayed(plugin, location, t -> runnable.run(), Math.max(1, delayTicks));
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, runnable, Math.max(1, delayTicks));
        }
    }

    /**
     * Schedules a task asynchronously after a delay (ms-based on Folia, tick-based on Paper).
     *
     * @param runnable    the task to run
     * @param delayMs     delay in milliseconds (Folia) or ticks × 50ms (Paper approximation)
     */
    public void runAsyncLater(@NotNull Runnable runnable, long delayMs) {
        if (FOLIA) {
            Bukkit.getAsyncScheduler().runDelayed(plugin, t -> runnable.run(), delayMs, TimeUnit.MILLISECONDS);
        } else {
            long ticks = Math.max(1, delayMs / 50);
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, runnable, ticks);
        }
    }

    // -----------------------------------------------------------------------
    // Repeating tasks
    // -----------------------------------------------------------------------

    /**
     * Schedules a repeating task on the entity's owning region. Returns a handle
     * that must be cancelled when no longer needed to prevent memory leaks.
     *
     * @param entity      target entity
     * @param runnable    the periodic task
     * @param delayTicks  initial delay in ticks
     * @param periodTicks period in ticks
     * @return a cancellable handle
     */
    @NotNull
    public RepeatingTask runRepeatingOnEntity(@NotNull Entity entity,
                                              @NotNull Runnable runnable,
                                              long delayTicks,
                                              long periodTicks) {
        if (FOLIA) {
            var handle = entity.getScheduler().runAtFixedRate(
                plugin, t -> runnable.run(), null,
                Math.max(1, delayTicks), Math.max(1, periodTicks)
            );
            return () -> { if (handle != null) handle.cancel(); };
        } else {
            var task = Bukkit.getScheduler().runTaskTimer(plugin, runnable,
                Math.max(1, delayTicks), Math.max(1, periodTicks));
            return task::cancel;
        }
    }

    /**
     * Schedules a repeating task on the global region thread. Used for the timer
     * display broadcast that iterates over all active sessions.
     *
     * @param runnable    the periodic task
     * @param delayTicks  initial delay in ticks
     * @param periodTicks period in ticks
     * @return a cancellable handle
     */
    @NotNull
    public RepeatingTask runRepeatingGlobal(@NotNull Runnable runnable,
                                            long delayTicks,
                                            long periodTicks) {
        if (FOLIA) {
            var handle = Bukkit.getGlobalRegionScheduler().runAtFixedRate(
                plugin, t -> runnable.run(),
                Math.max(1, delayTicks), Math.max(1, periodTicks)
            );
            return handle::cancel;
        } else {
            var task = Bukkit.getScheduler().runTaskTimer(plugin, runnable,
                Math.max(1, delayTicks), Math.max(1, periodTicks));
            return task::cancel;
        }
    }

    /**
     * Schedules a repeating async task. Useful for the ghost recorder.
     *
     * @param runnable  the periodic task
     * @param delayMs   initial delay in milliseconds
     * @param periodMs  period in milliseconds
     * @return a cancellable handle
     */
    @NotNull
    public RepeatingTask runRepeatingAsync(@NotNull Runnable runnable,
                                           long delayMs,
                                           long periodMs) {
        if (FOLIA) {
            var handle = Bukkit.getAsyncScheduler().runAtFixedRate(
                plugin, t -> runnable.run(), delayMs, periodMs, TimeUnit.MILLISECONDS
            );
            return handle::cancel;
        } else {
            long delayTicks = Math.max(1, delayMs / 50);
            long periodTicks = Math.max(1, periodMs / 50);
            var task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, runnable, delayTicks, periodTicks);
            return task::cancel;
        }
    }

    // -----------------------------------------------------------------------
    // Nested: RepeatingTask handle
    // -----------------------------------------------------------------------

    /**
     * A simple functional interface for cancelling a repeating scheduled task.
     */
    @FunctionalInterface
    public interface RepeatingTask {
        /** Cancels this repeating task. Safe to call multiple times. */
        void cancel();
    }
}
