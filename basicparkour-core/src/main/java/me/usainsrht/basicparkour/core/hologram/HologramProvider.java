package me.usainsrht.basicparkour.core.hologram;

import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Common SPI interface for hologram plugins (FancyHolograms, DecentHolograms).
 */
public interface HologramProvider {

    /**
     * @return the provider name
     */
    @NotNull
    String getName();

    /**
     * @return true if the underlying plugin is available and ready
     */
    boolean isAvailable();

    /**
     * Creates a new hologram or updates lines and location if already exists.
     *
     * @param name     unique identifier
     * @param location world location
     * @param lines    list of text lines (MiniMessage / legacy format)
     */
    void createOrUpdateHologram(@NotNull String name, @NotNull Location location, @NotNull List<String> lines);

    /**
     * Removes and deletes the hologram.
     *
     * @param name unique identifier
     */
    void deleteHologram(@NotNull String name);

    /**
     * Clean up all plugin-spawned holograms on disable if necessary.
     */
    default void shutdown() {}
}
