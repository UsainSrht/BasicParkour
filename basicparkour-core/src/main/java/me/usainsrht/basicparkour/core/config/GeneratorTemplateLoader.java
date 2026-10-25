package me.usainsrht.basicparkour.core.config;

import me.usainsrht.basicparkour.api.course.DieMode;
import me.usainsrht.basicparkour.api.generator.*;
import me.usainsrht.basicparkour.core.BasicParkourPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;
import java.util.logging.Level;

/**
 * Loads {@link GeneratorTemplate} definitions from
 * {@code plugins/BasicParkour/generators/*.yml}.
 *
 * <p>Ships a bundled {@code generators/example_generator.yml} on first run.
 * Invalid or missing fields produce warnings rather than crashes — the template
 * is skipped if a fatal field (id, difficulty, theme) is unparseable.</p>
 */
public final class GeneratorTemplateLoader {

    private final BasicParkourPlugin plugin;
    private final File generatorsDir;

    public GeneratorTemplateLoader(@NotNull BasicParkourPlugin plugin) {
        this.plugin = plugin;
        this.generatorsDir = new File(plugin.getDataFolder(), "generators");
    }

    /**
     * Loads all generator templates from the {@code generators/} directory.
     *
     * @return list of successfully loaded templates
     */
    @NotNull
    public List<GeneratorTemplate> loadAll() {
        if (!generatorsDir.exists()) {
            generatorsDir.mkdirs();
        }
        String[] defaultTemplates = {
            "example_generator.yml",
            "single_block.yml",
            "single_block_easy.yml",
            "single_block_hard.yml",
            "single_block_expert.yml"
        };
        for (String tplName : defaultTemplates) {
            File tplFile = new File(generatorsDir, tplName);
            if (!tplFile.exists()) {
                try {
                    plugin.saveResource("generators/" + tplName, false);
                } catch (Exception ignored) {
                }
            }
        }

        List<GeneratorTemplate> result = new ArrayList<>();
        File[] files = generatorsDir.listFiles((d, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            plugin.getLogger().warning("[GeneratorLoader] No generator files found in /generators/");
            return result;
        }

        for (File file : files) {
            try {
                GeneratorTemplate tpl = load(file);
                if (tpl != null) {
                    result.add(tpl);
                    plugin.getLogger().info("[GeneratorLoader] Loaded template: " + tpl.id());
                }
            } catch (Exception ex) {
                plugin.getLogger().log(Level.SEVERE,
                    "[GeneratorLoader] Failed to load: " + file.getName(), ex);
            }
        }
        return result;
    }

    // ── Parse a single file ────────────────────────────────────────────────
    @Nullable
    public GeneratorTemplate load(@NotNull File file) {
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        // ── Required fields ────────────────────────────────────────────────
        String id = cfg.getString("id");
        if (id == null || id.isBlank()) {
            warn(file, "Missing 'id'");
            return null;
        }

        String displayName = cfg.getString("display-name", "<gold>" + id + "</gold>");

        GeneratorDifficulty difficulty;
        try {
            difficulty = GeneratorDifficulty.valueOf(
                cfg.getString("difficulty", "MEDIUM").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            warn(file, "Invalid difficulty: " + cfg.getString("difficulty"));
            return null;
        }

        // ── Theme ──────────────────────────────────────────────────────────
        ConfigurationSection themeSection = cfg.getConfigurationSection("theme");
        GeneratorTheme theme = parseTheme(themeSection, id, file);
        if (theme == null) return null;

        // ── Length ─────────────────────────────────────────────────────────
        LengthType lengthType;
        try {
            lengthType = LengthType.valueOf(
                cfg.getString("length-type", "FIXED").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            warn(file, "Invalid length-type: " + cfg.getString("length-type"));
            lengthType = LengthType.FIXED;
        }
        int fixedLength = cfg.getInt("fixed-length", 30);

        // ── Block mode ─────────────────────────────────────────────────────
        BlockMode blockMode;
        try {
            blockMode = BlockMode.valueOf(
                cfg.getString("block-mode", "FULL_UPFRONT").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            warn(file, "Invalid block-mode: " + cfg.getString("block-mode"));
            blockMode = BlockMode.FULL_UPFRONT;
        }
        ConfigurationSection globalDiff = plugin.getConfig().getConfigurationSection("generator.difficulties." + difficulty.name());

        int slidingWindowAhead;
        if (cfg.contains("sliding-window-ahead")) {
            slidingWindowAhead = cfg.getInt("sliding-window-ahead");
        } else if (globalDiff != null && globalDiff.contains("sliding-window-ahead")) {
            slidingWindowAhead = globalDiff.getInt("sliding-window-ahead");
        } else {
            slidingWindowAhead = difficulty.getDefaultSlidingWindowAhead();
        }
        int cleanupDelay = cfg.getInt("cleanup-delay-seconds",
            plugin.getConfig().getInt("generator.default-cleanup-delay-seconds", 30));

        // ── Entry mode ─────────────────────────────────────────────────────
        String entryModeRaw = cfg.getString("entry-mode", "DEDICATED_WORLD").trim().toUpperCase(Locale.ROOT);
        EntryMode entryMode;
        if (entryModeRaw.equals("WALK_IN") || entryModeRaw.equals("SPAWN") || entryModeRaw.equals("AREA")) {
            entryMode = EntryMode.WALK_IN;
        } else {
            try {
                entryMode = EntryMode.valueOf(entryModeRaw);
            } catch (IllegalArgumentException ex) {
                warn(file, "Invalid entry-mode: " + cfg.getString("entry-mode"));
                entryMode = EntryMode.DEDICATED_WORLD;
            }
        }
        String dedicatedWorld = cfg.getString("dedicated-world");
        int dedicatedRadius = cfg.getInt("dedicated-world-radius", 100_000);

        // ── Misc ───────────────────────────────────────────────────────────
        boolean shared = cfg.getBoolean("shared", false);
        Long seed = cfg.contains("seed") ? cfg.getLong("seed") : null;
        String leaderboardId = cfg.getString("leaderboard-course-id",
            plugin.getConfig().getString("generator.leaderboard-id", "__generated__"));

        Float fixedYaw = null;
        if (cfg.contains("walk-in-yaw")) {
            fixedYaw = (float) cfg.getDouble("walk-in-yaw");
        } else if (cfg.contains("fixed-yaw")) {
            fixedYaw = (float) cfg.getDouble("fixed-yaw");
        } else if (cfg.contains("yaw")) {
            fixedYaw = (float) cfg.getDouble("yaw");
        }

        // ── Platform shapes ────────────────────────────────────────────────
        List<String> platformShapes = new ArrayList<>();
        if (cfg.isList("platform-shapes")) {
            for (String shape : cfg.getStringList("platform-shapes")) {
                if (shape != null && !shape.isBlank()) {
                    platformShapes.add(shape.trim().toUpperCase(Locale.ROOT));
                }
            }
        } else if (cfg.isList("platform-sizes")) {
            for (int size : cfg.getIntegerList("platform-sizes")) {
                platformShapes.add(size + "x" + size);
            }
        }

        // ── Difficulty overrides ───────────────────────────────────────────
        ConfigurationSection overrides = cfg.getConfigurationSection("difficulty-overrides");
        int minGap          = getDifficultyInt(overrides, globalDiff, "min-gap", -1);
        int maxGap          = getDifficultyInt(overrides, globalDiff, "max-gap", -1);
        int minHeightDelta  = getDifficultyInt(overrides, globalDiff, "min-height-delta", Integer.MIN_VALUE);
        int maxHeightDelta  = getDifficultyInt(overrides, globalDiff, "max-height-delta", Integer.MAX_VALUE);
        int platMinSize     = getDifficultyInt(overrides, globalDiff, "platform-min-size", -1);
        int platMaxSize     = getDifficultyInt(overrides, globalDiff, "platform-max-size", -1);
        double obstChance   = getDifficultyDouble(overrides, globalDiff, "obstacle-chance", -1.0);
        double modChance    = getDifficultyDouble(overrides, globalDiff, "modifier-chance", -1.0);

        // ── Modifier overrides ─────────────────────────────────────────────
        Map<Material, String> modifiers = new HashMap<>();
        if (cfg.isList("modifiers")) {
            for (Map<?, ?> entry : cfg.getMapList("modifiers")) {
                String blockName = strOf(entry, "block");
                String type = strOf(entry, "type");
                if (blockName == null || type == null) continue;
                Material mat = Material.matchMaterial(blockName);
                if (mat == null) {
                    warn(file, "Unknown material in modifiers: " + blockName);
                    continue;
                }
            }
        }

        // ── Endless / session options ─────────────────────────────────────────
        me.usainsrht.basicparkour.api.course.DieMode dieMode;
        if (cfg.contains("die-mode")) {
            String raw = cfg.getString("die-mode", "FAIL_DIE").trim().toUpperCase(Locale.ROOT);
            if (raw.equals("FAIL") || raw.equals("FAIL_DIE")) {
                dieMode = DieMode.FAIL_DIE;
            } else if (raw.equals("RESTART") || raw.equals("RESTART_DIE") || raw.equals("CHECKPOINT")) {
                dieMode = DieMode.RESTART_DIE;
            } else {
                dieMode = lengthType == LengthType.INFINITE ? DieMode.FAIL_DIE : DieMode.RESTART_DIE;
            }
        } else {
            dieMode = lengthType == LengthType.INFINITE ? DieMode.FAIL_DIE : DieMode.RESTART_DIE;
        }

        BlockAppearAnimation appearAnimation;
        String animRaw = cfg.getString("appear-animation", cfg.getString("animation", "DEFAULT")).trim().toUpperCase(Locale.ROOT);
        try {
            appearAnimation = BlockAppearAnimation.valueOf(animRaw);
        } catch (IllegalArgumentException ex) {
            warn(file, "Unknown appear-animation: " + animRaw);
            appearAnimation = BlockAppearAnimation.DEFAULT;
        }

        double slidingWindowTime;
        if (cfg.contains("sliding-window-time-seconds")) {
            slidingWindowTime = cfg.getDouble("sliding-window-time-seconds");
        } else if (cfg.contains("sliding-window-time")) {
            slidingWindowTime = cfg.getDouble("sliding-window-time");
        } else if (globalDiff != null && globalDiff.contains("sliding-window-time-seconds")) {
            slidingWindowTime = globalDiff.getDouble("sliding-window-time-seconds");
        } else {
            slidingWindowTime = difficulty.getDefaultSlidingWindowTimeSeconds();
        }

        SlidingWindowMode slidingMode;
        if (cfg.contains("sliding-window-mode")) {
            try {
                slidingMode = SlidingWindowMode.valueOf(cfg.getString("sliding-window-mode").trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                warn(file, "Unknown sliding-window-mode: " + cfg.getString("sliding-window-mode"));
                slidingMode = slidingWindowTime > 0 ? SlidingWindowMode.DUAL : SlidingWindowMode.BLOCKS;
            }
        } else {
            slidingMode = slidingWindowTime > 0 ? SlidingWindowMode.DUAL : SlidingWindowMode.BLOCKS;
        }

        Location failLocation = me.usainsrht.basicparkour.core.util.LocationParser.parse(cfg.get("fail-location"));
        if (failLocation == null) {
            failLocation = me.usainsrht.basicparkour.core.util.LocationParser.parse(cfg.get("exit-location"));
        }
        Location completeLocation = me.usainsrht.basicparkour.core.util.LocationParser.parse(cfg.get("complete-location"));

        // ── Direction & Cone Constraint ───────────────────────────────────────
        double defaultCone = lengthType == LengthType.INFINITE ? 90.0 : 360.0;
        double directionCone = cfg.getDouble("direction-cone",
            cfg.getDouble("direction-range",
            cfg.getDouble("cone-angle", defaultCone)));
        if (directionCone < 0.0) directionCone = 0.0;
        if (directionCone > 360.0) directionCone = 360.0;

        Float directionYaw = null;
        if (cfg.contains("direction-yaw")) {
            directionYaw = (float) cfg.getDouble("direction-yaw");
        }

        int corridorWidth = cfg.getInt("corridor-width", cfg.getInt("max-corridor-width", 0));
        int maxHeightDeviation = cfg.getInt("max-height-deviation", cfg.getInt("height-range", 15));

        // ── Protection overrides ──────────────────────────────────────────────
        Boolean preventDamage = cfg.contains("prevent-damage") ? cfg.getBoolean("prevent-damage") : null;
        Boolean fallDamageFails = cfg.contains("fall-damage-fails-session")
            ? cfg.getBoolean("fall-damage-fails-session")
            : (cfg.contains("fall-damage-fails") ? cfg.getBoolean("fall-damage-fails") : null);

        return new GeneratorTemplate(
            id, displayName, difficulty, theme, lengthType, fixedLength,
            blockMode, slidingWindowAhead, cleanupDelay,
            entryMode, dedicatedWorld, dedicatedRadius, shared, seed,
            leaderboardId, fixedYaw, platformShapes,
            minGap, maxGap, minHeightDelta, maxHeightDelta,
            platMinSize, platMaxSize, obstChance, modChance,
            modifiers,
            dieMode, appearAnimation, slidingMode, slidingWindowTime,
            failLocation, completeLocation,
            directionCone, directionYaw, corridorWidth, maxHeightDeviation,
            preventDamage, fallDamageFails
        );
    }

    @Nullable
    private GeneratorTheme parseTheme(
        @Nullable ConfigurationSection sec, @NotNull String templateId, @NotNull File file
    ) {
        String themeName = sec != null ? sec.getString("name", templateId) : templateId;
        List<Material> platform = parseMaterials(sec, "platform-materials", file, Material.OAK_PLANKS);
        List<Material> accent   = parseMaterials(sec, "accent-materials",   file, null);
        List<Material> obstacle = parseMaterials(sec, "obstacle-materials", file, null);
        return new GeneratorTheme(themeName, platform, accent, obstacle);
    }

    @NotNull
    private List<Material> parseMaterials(
        @Nullable ConfigurationSection sec, @NotNull String key,
        @NotNull File file, @Nullable Material fallback
    ) {
        if (sec == null || !sec.isList(key)) {
            return fallback != null ? List.of(fallback) : List.of();
        }
        List<Material> result = new ArrayList<>();
        for (String name : sec.getStringList(key)) {
            Material m = Material.matchMaterial(name);
            if (m == null) {
                warn(file, "Unknown material '" + name + "' in theme." + key);
            } else {
                result.add(m);
            }
        }
        if (result.isEmpty() && fallback != null) result.add(fallback);
        return result;
    }

    @Nullable
    private static String strOf(@NotNull Map<?, ?> map, @NotNull String key) {
        Object v = map.get(key);
        return v instanceof String s ? s : null;
    }

    private int getDifficultyInt(@Nullable ConfigurationSection overrides, @Nullable ConfigurationSection globalSec, @NotNull String key, int fallback) {
        if (overrides != null && overrides.contains(key)) {
            return overrides.getInt(key);
        }
        if (globalSec != null && globalSec.contains(key)) {
            return globalSec.getInt(key);
        }
        return fallback;
    }

    private double getDifficultyDouble(@Nullable ConfigurationSection overrides, @Nullable ConfigurationSection globalSec, @NotNull String key, double fallback) {
        if (overrides != null && overrides.contains(key)) {
            return overrides.getDouble(key);
        }
        if (globalSec != null && globalSec.contains(key)) {
            return globalSec.getDouble(key);
        }
        return fallback;
    }

    private void warn(@NotNull File file, @NotNull String msg) {
        plugin.getLogger().warning("[GeneratorLoader] " + msg + " in '" + file.getName() + "'");
    }
}
