package me.usainsrht.basicparkour.core.config;

import me.usainsrht.basicparkour.api.course.Checkpoint;
import me.usainsrht.basicparkour.api.course.ParkourCourse;
import me.usainsrht.basicparkour.core.BasicParkourPlugin;
import me.usainsrht.basicparkour.core.course.CheckpointImpl;
import me.usainsrht.basicparkour.core.course.CourseRegionImpl;
import me.usainsrht.basicparkour.core.course.ParkourCourseImpl;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Loads {@link ParkourCourse} definitions from {@code plugins/BasicParkour/courses/*.yml}.
 *
 * <h2>File format</h2>
 * See the bundled {@code courses/example.yml} for the full schema. This loader is
 * deliberately strict — invalid or missing fields produce warnings, not crashes.
 */
public final class CourseLoader {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private final BasicParkourPlugin plugin;
    private final File coursesDir;

    public CourseLoader(@NotNull BasicParkourPlugin plugin) {
        this.plugin = plugin;
        this.coursesDir = new File(plugin.getDataFolder(), "courses");
    }

    /**
     * Loads all courses from the {@code courses/} directory.
     *
     * @return list of successfully loaded courses
     */
    @NotNull
    public List<ParkourCourse> loadAll() {
        if (!coursesDir.exists()) {
            coursesDir.mkdirs();
            // Save example course on first run
            plugin.saveResource("courses/example.yml", false);
        }

        List<ParkourCourse> courses = new ArrayList<>();
        File[] files = coursesDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            plugin.getLogger().warning("[CourseLoader] No course files found in /courses/");
            return courses;
        }

        for (File file : files) {
            try {
                ParkourCourse course = loadCourse(file);
                if (course != null) {
                    courses.add(course);
                    plugin.getLogger().info("[CourseLoader] Loaded course: " + course.getId());
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE,
                    "[CourseLoader] Failed to load course file: " + file.getName(), e);
            }
        }
        return courses;
    }

    // -----------------------------------------------------------------------
    // Private: parse a single course file
    // -----------------------------------------------------------------------

    @Nullable
    private ParkourCourse loadCourse(@NotNull File file) {
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        String id = cfg.getString("id");
        if (id == null || id.isBlank()) {
            warn(file, "Missing or empty 'id'");
            return null;
        }

        String displayNameRaw = cfg.getString("display-name", id);
        var displayName = MINI.deserialize(displayNameRaw);

        String worldName = cfg.getString("world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            warn(file, "World '%s' is not loaded".formatted(worldName));
            return null;
        }

        // Start / end regions
        CourseRegionImpl startRegion = parseRegion(cfg, "start-region", world, file);
        CourseRegionImpl endRegion = parseRegion(cfg, "end-region", world, file);
        if (startRegion == null || endRegion == null) return null;

        // Checkpoints
        List<Checkpoint> checkpoints = new ArrayList<>();
        if (cfg.isList("checkpoints")) {
            List<?> cpList = cfg.getList("checkpoints");
            if (cpList != null) {
                for (Object raw : cpList) {
                    if (!(raw instanceof Map<?, ?> cpMap)) continue;
                    Checkpoint cp = parseCheckpoint(cpMap, world, checkpoints.size(), file);
                    if (cp != null) checkpoints.add(cp);
                }
            }
        }

        // Modifier overrides
        Map<Material, String> modifiers = new HashMap<>();
        if (cfg.isList("modifiers")) {
            List<Map<?, ?>> modList = cfg.getMapList("modifiers");
            for (Map<?, ?> m : modList) {
                String blockName = getString(m, "block");
                String type = getString(m, "type");
                if (blockName == null || type == null) continue;
                Material mat = Material.matchMaterial(blockName);
                if (mat == null) {
                    warn(file, "Unknown material in modifiers: " + blockName);
                    continue;
                }
                modifiers.put(mat, "basicparkour:" + type.toLowerCase());
            }
        }

        // Reward commands
        List<String> rewards = cfg.getStringList("rewards");

        // Time limit
        long timeLimitMs = cfg.contains("time-limit-seconds")
            ? cfg.getLong("time-limit-seconds") * 1000L
            : -1L;

        return new ParkourCourseImpl(id, displayName, worldName,
            startRegion, endRegion, checkpoints, modifiers, rewards, timeLimitMs);
    }

    @Nullable
    private CourseRegionImpl parseRegion(@NotNull YamlConfiguration cfg,
                                          @NotNull String key,
                                          @NotNull World world,
                                          @NotNull File file) {
        ConfigurationSection sec = cfg.getConfigurationSection(key);
        if (sec == null) { warn(file, "Missing section: " + key); return null; }
        Location min = parseLocationSection(sec.getConfigurationSection("min"), world);
        Location max = parseLocationSection(sec.getConfigurationSection("max"), world);
        if (min == null || max == null) { warn(file, "Invalid region corners in: " + key); return null; }
        return new CourseRegionImpl(min, max);
    }

    @Nullable
    private Location parseLocationSection(@Nullable ConfigurationSection sec, @NotNull World world) {
        if (sec == null) return null;
        return new Location(world, sec.getDouble("x"), sec.getDouble("y"), sec.getDouble("z"));
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private Checkpoint parseCheckpoint(@NotNull Map<?, ?> map, @NotNull World world,
                                        int index, @NotNull File file) {
        // Spawn location
        Map<?, ?> locMap = (Map<?, ?>) map.get("location");
        if (locMap == null) { warn(file, "Checkpoint " + index + " missing 'location'"); return null; }
        double x = toDouble(locMap.get("x"));
        double y = toDouble(locMap.get("y"));
        double z = toDouble(locMap.get("z"));
        Object rawYaw   = locMap.get("yaw");
        Object rawPitch = locMap.get("pitch");
        float yaw   = (float) toDouble(rawYaw   != null ? rawYaw   : 0);
        float pitch = (float) toDouble(rawPitch != null ? rawPitch : 0);
        Location spawn = new Location(world, x, y, z, yaw, pitch);

        // Trigger region
        Map<?, ?> regionMap = (Map<?, ?>) map.get("region");
        if (regionMap == null) { warn(file, "Checkpoint " + index + " missing 'region'"); return null; }
        Map<?, ?> minMap = (Map<?, ?>) regionMap.get("min");
        Map<?, ?> maxMap = (Map<?, ?>) regionMap.get("max");
        if (minMap == null || maxMap == null) { warn(file, "Checkpoint " + index + " region missing min/max"); return null; }
        Location min = new Location(world, toDouble(minMap.get("x")), toDouble(minMap.get("y")), toDouble(minMap.get("z")));
        Location max = new Location(world, toDouble(maxMap.get("x")), toDouble(maxMap.get("y")), toDouble(maxMap.get("z")));

        return new CheckpointImpl(index, spawn, new CourseRegionImpl(min, max));
    }

    // -----------------------------------------------------------------------
    // Utility helpers
    // -----------------------------------------------------------------------

    private static double toDouble(@Nullable Object obj) {
        if (obj instanceof Number n) return n.doubleValue();
        return 0.0;
    }

    @Nullable
    private static String getString(@NotNull Map<?, ?> map, @NotNull String key) {
        Object val = map.get(key);
        return val instanceof String s ? s : null;
    }

    private void warn(@NotNull File file, @NotNull String msg) {
        plugin.getLogger().warning("[CourseLoader] %s in file '%s'".formatted(msg, file.getName()));
    }
}
