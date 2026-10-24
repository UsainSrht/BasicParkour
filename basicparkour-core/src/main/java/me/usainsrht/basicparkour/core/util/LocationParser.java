package me.usainsrht.basicparkour.core.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Utility for parsing {@link Location} instances from various YAML configuration representations:
 * <ul>
 *   <li>Comma-separated string: {@code "world,x,y,z"} or {@code "world,x,y,z,yaw,pitch"}</li>
 *   <li>{@link ConfigurationSection} with {@code world, x, y, z, yaw, pitch} keys</li>
 *   <li>{@link Map} with {@code world, x, y, z, yaw, pitch} keys</li>
 * </ul>
 */
public final class LocationParser {

    private LocationParser() {}

    @Nullable
    public static Location parse(@Nullable Object obj) {
        if (obj == null) return null;
        if (obj instanceof Location loc) return loc;

        if (obj instanceof ConfigurationSection sec) {
            String worldName = sec.getString("world");
            World world = resolveWorld(worldName);
            if (world == null) return null;
            double x = sec.getDouble("x");
            double y = sec.getDouble("y");
            double z = sec.getDouble("z");
            float yaw = (float) sec.getDouble("yaw", 0.0);
            float pitch = (float) sec.getDouble("pitch", 0.0);
            return new Location(world, x, y, z, yaw, pitch);
        }

        if (obj instanceof Map<?, ?> map) {
            Object rawWorld = map.get("world");
            World world = resolveWorld(rawWorld != null ? rawWorld.toString() : null);
            if (world == null) return null;
            double x = toDouble(map.get("x"));
            double y = toDouble(map.get("y"));
            double z = toDouble(map.get("z"));
            float yaw = (float) toDouble(map.get("yaw"));
            float pitch = (float) toDouble(map.get("pitch"));
            return new Location(world, x, y, z, yaw, pitch);
        }

        if (obj instanceof String str) {
            String trimmed = str.trim();
            if (trimmed.isEmpty() || trimmed.equalsIgnoreCase("null") || trimmed.equalsIgnoreCase("none")) {
                return null;
            }
            String[] parts = trimmed.split(",");
            if (parts.length >= 4) {
                World world = resolveWorld(parts[0].trim());
                if (world == null) return null;
                try {
                    double x = Double.parseDouble(parts[1].trim());
                    double y = Double.parseDouble(parts[2].trim());
                    double z = Double.parseDouble(parts[3].trim());
                    float yaw = parts.length > 4 ? Float.parseFloat(parts[4].trim()) : 0.0f;
                    float pitch = parts.length > 5 ? Float.parseFloat(parts[5].trim()) : 0.0f;
                    return new Location(world, x, y, z, yaw, pitch);
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }

        return null;
    }

    @Nullable
    private static World resolveWorld(@Nullable String name) {
        if (name == null || name.isBlank()) {
            return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        }
        return Bukkit.getWorld(name);
    }

    private static double toDouble(@Nullable Object obj) {
        if (obj instanceof Number num) return num.doubleValue();
        if (obj != null) {
            try {
                return Double.parseDouble(obj.toString());
            } catch (NumberFormatException ignored) {}
        }
        return 0.0;
    }
}
