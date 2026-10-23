package me.usainsrht.basicparkour.core.hologram;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Data model for a configured BasicParkour hologram.
 */
public final class ParkourHologramData {

    private final String name;
    private final HologramType type;
    private final String courseId;
    private Location location;
    private int limit;

    public ParkourHologramData(@NotNull String name,
                               @NotNull HologramType type,
                               @NotNull String courseId,
                               @NotNull Location location,
                               int limit) {
        this.name = name;
        this.type = type;
        this.courseId = courseId;
        this.location = location;
        this.limit = Math.max(1, limit);
    }

    public @NotNull String getName() {
        return name;
    }

    public @NotNull HologramType getType() {
        return type;
    }

    public @NotNull String getCourseId() {
        return courseId;
    }

    public @NotNull Location getLocation() {
        return location;
    }

    public void setLocation(@NotNull Location location) {
        this.location = location;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = Math.max(1, limit);
    }

    public void serialize(@NotNull ConfigurationSection section) {
        section.set("type", type.name());
        section.set("course", courseId);
        section.set("limit", limit);
        section.set("world", Objects.requireNonNull(location.getWorld()).getName());
        section.set("x", location.getX());
        section.set("y", location.getY());
        section.set("z", location.getZ());
        section.set("yaw", location.getYaw());
        section.set("pitch", location.getPitch());
    }

    public static @Nullable ParkourHologramData deserialize(@NotNull String name, @NotNull ConfigurationSection section) {
        try {
            String typeStr = section.getString("type", "LEADERBOARD");
            HologramType type = HologramType.valueOf(typeStr.toUpperCase());
            String courseId = section.getString("course", "");
            int limit = section.getInt("limit", 10);
            String worldName = section.getString("world");
            if (worldName == null) return null;
            World world = Bukkit.getWorld(worldName);
            if (world == null) return null;

            double x = section.getDouble("x");
            double y = section.getDouble("y");
            double z = section.getDouble("z");
            float yaw = (float) section.getDouble("yaw", 0.0);
            float pitch = (float) section.getDouble("pitch", 0.0);
            Location loc = new Location(world, x, y, z, yaw, pitch);

            return new ParkourHologramData(name, type, courseId, loc, limit);
        } catch (Exception e) {
            return null;
        }
    }
}
