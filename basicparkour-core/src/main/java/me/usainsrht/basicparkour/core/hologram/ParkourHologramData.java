package me.usainsrht.basicparkour.core.hologram;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
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
    private List<String> customLines;
    private boolean fromConfig;

    public ParkourHologramData(@NotNull String name,
                               @NotNull HologramType type,
                               @NotNull String courseId,
                               @NotNull Location location,
                               int limit) {
        this(name, type, courseId, location, limit, null, false);
    }

    public ParkourHologramData(@NotNull String name,
                               @NotNull HologramType type,
                               @NotNull String courseId,
                               @NotNull Location location,
                               int limit,
                               @Nullable List<String> customLines) {
        this(name, type, courseId, location, limit, customLines, false);
    }

    public ParkourHologramData(@NotNull String name,
                               @NotNull HologramType type,
                               @NotNull String courseId,
                               @NotNull Location location,
                               int limit,
                               @Nullable List<String> customLines,
                               boolean fromConfig) {
        this.name = name;
        this.type = type;
        this.courseId = courseId;
        this.location = location;
        this.limit = Math.max(1, limit);
        this.customLines = customLines != null ? new java.util.ArrayList<>(customLines) : new java.util.ArrayList<>();
        this.fromConfig = fromConfig;
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

    public @NotNull List<String> getCustomLines() {
        return customLines != null ? customLines : java.util.Collections.emptyList();
    }

    public void setCustomLines(@Nullable List<String> customLines) {
        this.customLines = customLines != null ? new java.util.ArrayList<>(customLines) : new java.util.ArrayList<>();
    }

    public boolean isFromConfig() {
        return fromConfig;
    }

    public void setFromConfig(boolean fromConfig) {
        this.fromConfig = fromConfig;
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
        if (customLines != null && !customLines.isEmpty()) {
            section.set("lines", customLines);
        }
    }

    public static @Nullable ParkourHologramData deserialize(@NotNull String name, @NotNull ConfigurationSection section) {
        return deserialize(name, section, false);
    }

    public static @Nullable ParkourHologramData deserialize(@NotNull String name, @NotNull ConfigurationSection section, boolean fromConfig) {
        try {
            String typeStr = section.getString("type", "LEADERBOARD");
            HologramType type = HologramType.valueOf(typeStr.toUpperCase(java.util.Locale.ROOT));
            String courseId = section.getString("course", "");
            int limit = section.getInt("limit", 10);

            ConfigurationSection locSec = section.getConfigurationSection("location");
            ConfigurationSection sourceSec = locSec != null ? locSec : section;

            String worldName = sourceSec.getString("world");
            if (worldName == null) return null;
            World world = Bukkit.getWorld(worldName);
            if (world == null) return null;

            double x = sourceSec.getDouble("x");
            double y = sourceSec.getDouble("y");
            double z = sourceSec.getDouble("z");
            float yaw = (float) sourceSec.getDouble("yaw", 0.0);
            float pitch = (float) sourceSec.getDouble("pitch", 0.0);
            Location loc = new Location(world, x, y, z, yaw, pitch);

            List<String> lines = section.isList("lines") ? section.getStringList("lines") : null;

            return new ParkourHologramData(name, type, courseId, loc, limit, lines, fromConfig);
        } catch (Exception e) {
            return null;
        }
    }
}
