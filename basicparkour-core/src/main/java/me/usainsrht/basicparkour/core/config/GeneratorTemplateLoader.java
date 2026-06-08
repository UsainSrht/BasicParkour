package me.usainsrht.basicparkour.core.config;

import me.usainsrht.basicparkour.api.generator.*;
import me.usainsrht.basicparkour.core.BasicParkourPlugin;
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
            plugin.saveResource("generators/example_generator.yml", false);
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

    // ── Private: parse a single file ──────────────────────────────────────

    @Nullable
    private GeneratorTemplate load(@NotNull File file) {
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
        int slidingWindowAhead = cfg.getInt("sliding-window-ahead", 15);
        int cleanupDelay = cfg.getInt("cleanup-delay-seconds",
            plugin.getConfig().getInt("generator.default-cleanup-delay-seconds", 30));

        // ── Entry mode ─────────────────────────────────────────────────────
        EntryMode entryMode;
        try {
            entryMode = EntryMode.valueOf(
                cfg.getString("entry-mode", "DEDICATED_WORLD").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            warn(file, "Invalid entry-mode: " + cfg.getString("entry-mode"));
            entryMode = EntryMode.DEDICATED_WORLD;
        }
        String dedicatedWorld = cfg.getString("dedicated-world");
        int dedicatedRadius = cfg.getInt("dedicated-world-radius", 100_000);

        // ── Misc ───────────────────────────────────────────────────────────
        boolean shared = cfg.getBoolean("shared", false);
        Long seed = cfg.contains("seed") ? cfg.getLong("seed") : null;
        String leaderboardId = cfg.getString("leaderboard-course-id",
            plugin.getConfig().getString("generator.leaderboard-id", "__generated__"));

        // ── Difficulty overrides ───────────────────────────────────────────
        ConfigurationSection overrides = cfg.getConfigurationSection("difficulty-overrides");
        int minGap          = overrides != null ? overrides.getInt("min-gap",          -1) : -1;
        int maxGap          = overrides != null ? overrides.getInt("max-gap",          -1) : -1;
        int minHeightDelta  = overrides != null ? overrides.getInt("min-height-delta", Integer.MIN_VALUE) : Integer.MIN_VALUE;
        int maxHeightDelta  = overrides != null ? overrides.getInt("max-height-delta", Integer.MAX_VALUE) : Integer.MAX_VALUE;
        int platMinSize     = overrides != null ? overrides.getInt("platform-min-size", -1) : -1;
        int platMaxSize     = overrides != null ? overrides.getInt("platform-max-size", -1) : -1;
        double obstChance   = overrides != null ? overrides.getDouble("obstacle-chance", -1) : -1;
        double modChance    = overrides != null ? overrides.getDouble("modifier-chance", -1) : -1;

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
                modifiers.put(mat, "basicparkour:" + type.toLowerCase(Locale.ROOT));
            }
        }

        return new GeneratorTemplate(
            id, displayName, difficulty, theme, lengthType, fixedLength,
            blockMode, slidingWindowAhead, cleanupDelay,
            entryMode, dedicatedWorld, dedicatedRadius, shared, seed,
            leaderboardId,
            minGap, maxGap, minHeightDelta, maxHeightDelta,
            platMinSize, platMaxSize, obstChance, modChance,
            modifiers
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

    private void warn(@NotNull File file, @NotNull String msg) {
        plugin.getLogger().warning("[GeneratorLoader] " + msg + " in '" + file.getName() + "'");
    }
}
