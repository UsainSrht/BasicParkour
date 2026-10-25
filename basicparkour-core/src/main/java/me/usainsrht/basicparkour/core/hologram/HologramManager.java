package me.usainsrht.basicparkour.core.hologram;

import me.usainsrht.basicparkour.api.BasicParkourAPI;
import me.usainsrht.basicparkour.api.course.ParkourCourse;
import me.usainsrht.basicparkour.api.storage.LeaderboardEntry;
import me.usainsrht.basicparkour.core.BasicParkourPlugin;
import me.usainsrht.basicparkour.core.scheduling.FoliaScheduler;
import me.usainsrht.basicparkour.core.util.TimerFormatter;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages all BasicParkour holograms across FancyHolograms and DecentHolograms.
 */
public final class HologramManager {

    private final BasicParkourPlugin plugin;
    private final HologramConfig config;
    private final Map<String, ParkourHologramData> holograms = new ConcurrentHashMap<>();
    private final List<HologramProvider> providers = new ArrayList<>();
    private HologramProvider activeProvider;
    private FoliaScheduler.RepeatingTask refreshTask;
    private final File storageFile;

    public HologramManager(@NotNull BasicParkourPlugin plugin) {
        this.plugin = plugin;
        this.config = new HologramConfig(plugin.getConfig().getConfigurationSection("hologram"));
        this.storageFile = new File(plugin.getDataFolder(), "holograms.yml");

        // Register available providers
        providers.add(new FancyHologramsProvider());
        providers.add(new DecentHologramsProvider());

        selectProvider();
        load();
        startRefreshLoop();
    }

    public @NotNull HologramConfig getConfig() {
        return config;
    }

    public void selectProvider() {
        String preferred = config.getProvider().toLowerCase(Locale.ROOT);
        activeProvider = null;

        for (HologramProvider provider : providers) {
            if (!provider.isAvailable()) continue;
            if (preferred.equalsIgnoreCase(provider.getName()) || preferred.equals("auto")) {
                activeProvider = provider;
                break;
            }
        }

        if (activeProvider != null) {
            plugin.getLogger().info("[BasicParkour] Hologram provider selected: " + activeProvider.getName());
        } else {
            plugin.getLogger().info("[BasicParkour] No supported hologram plugin found (FancyHolograms / DecentHolograms).");
        }
    }

    public @Nullable HologramProvider getActiveProvider() {
        return activeProvider;
    }

    public boolean isEnabled() {
        return config.isEnabled() && activeProvider != null && activeProvider.isAvailable();
    }

    public void startRefreshLoop() {
        stopRefreshLoop();
        if (!isEnabled()) return;

        long intervalSeconds = config.getRefreshIntervalSeconds();
        if (intervalSeconds > 0) {
            long intervalTicks = intervalSeconds * 20L;
            refreshTask = plugin.getScheduler().runRepeatingGlobal(this::refreshAll, intervalTicks, intervalTicks);
        }
    }

    public void stopRefreshLoop() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    public void reload() {
        config.reload(plugin.getConfig().getConfigurationSection("hologram"));
        selectProvider();
        stopRefreshLoop();
        if (isEnabled()) {
            startRefreshLoop();
            load();
        } else {
            for (ParkourHologramData data : holograms.values()) {
                if (activeProvider != null) {
                    activeProvider.deleteHologram(data.getName());
                }
            }
        }
    }

    public void load() {
        holograms.clear();

        // 1. Load dynamic holograms from holograms.yml
        if (storageFile.exists()) {
            YamlConfiguration fileConfig = YamlConfiguration.loadConfiguration(storageFile);
            ConfigurationSection root = fileConfig.getConfigurationSection("holograms");
            if (root != null) {
                for (String key : root.getKeys(false)) {
                    ConfigurationSection sec = root.getConfigurationSection(key);
                    if (sec != null) {
                        ParkourHologramData data = ParkourHologramData.deserialize(key, sec, false);
                        if (data != null) {
                            holograms.put(key, data);
                        }
                    }
                }
            }
        }

        // 2. Load static definitions from config.yml if present
        ConfigurationSection defs = plugin.getConfig().getConfigurationSection("hologram.definitions");
        if (defs == null) {
            defs = plugin.getConfig().getConfigurationSection("hologram.list");
        }
        if (defs != null) {
            for (String key : defs.getKeys(false)) {
                ConfigurationSection sec = defs.getConfigurationSection(key);
                if (sec != null) {
                    ParkourHologramData data = ParkourHologramData.deserialize(key, sec, true);
                    if (data != null) {
                        holograms.put(key, data);
                    }
                }
            }
        }

        // Render loaded holograms
        if (isEnabled()) {
            holograms.keySet().forEach(this::refreshHologram);
        }
        plugin.getLogger().info("[BasicParkour] Loaded " + holograms.size() + " hologram(s).");
    }

    public void save() {
        YamlConfiguration fileConfig = new YamlConfiguration();
        ConfigurationSection root = fileConfig.createSection("holograms");
        for (Map.Entry<String, ParkourHologramData> entry : holograms.entrySet()) {
            if (entry.getValue().isFromConfig()) continue;
            ConfigurationSection sec = root.createSection(entry.getKey());
            entry.getValue().serialize(sec);
        }
        try {
            fileConfig.save(storageFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "[BasicParkour] Failed to save holograms.yml", e);
        }
    }

    public boolean createHologram(@NotNull String name,
                                  @NotNull HologramType type,
                                  @NotNull String courseId,
                                  @NotNull Location location,
                                  int limit) {
        return createHologram(name, type, courseId, location, limit, null);
    }

    public boolean createHologram(@NotNull String name,
                                  @NotNull HologramType type,
                                  @NotNull String courseId,
                                  @NotNull Location location,
                                  int limit,
                                  @Nullable List<String> customLines) {
        if (!isEnabled()) return false;
        ParkourHologramData data = new ParkourHologramData(name, type, courseId, location, limit, customLines, false);
        holograms.put(name, data);
        save();
        refreshHologram(name);
        return true;
    }

    public boolean deleteHologram(@NotNull String name) {
        ParkourHologramData removed = holograms.remove(name);
        if (removed != null) {
            if (activeProvider != null) {
                activeProvider.deleteHologram(name);
            }
            if (!removed.isFromConfig()) {
                save();
            }
            return true;
        }
        return false;
    }

    public @Nullable ParkourHologramData getHologram(@NotNull String name) {
        return holograms.get(name);
    }

    public @NotNull Collection<ParkourHologramData> getHolograms() {
        return Collections.unmodifiableCollection(holograms.values());
    }

    public void refreshAll() {
        if (!isEnabled()) return;
        holograms.keySet().forEach(this::refreshHologram);
    }

    public void onCourseComplete(@NotNull String courseId, boolean isNewBest) {
        if (!isEnabled()) return;
        if ((isNewBest && config.isUpdateOnRecord()) || config.isUpdateOnCompletion()) {
            refreshCourseHolograms(courseId);
        }
    }

    public void refreshCourseHolograms(@NotNull String courseId) {
        if (!isEnabled()) return;
        for (ParkourHologramData data : holograms.values()) {
            if (data.getCourseId().equalsIgnoreCase(courseId)) {
                refreshHologram(data.getName());
            }
        }
    }

    public void refreshHologram(@NotNull String name) {
        if (!isEnabled()) return;
        ParkourHologramData data = holograms.get(name);
        if (data == null) return;

        Optional<ParkourCourse> courseOpt = BasicParkourAPI.get().getCourseRegistry().getCourse(data.getCourseId());
        String formattedCourseName = courseOpt
                .map(c -> net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().serialize(c.getDisplayName()))
                .orElseGet(() -> {
                    if (plugin.getGeneratorManager() != null) {
                        for (var tpl : plugin.getGeneratorManager().getTemplates()) {
                            if (tpl.leaderboardCourseId().equalsIgnoreCase(data.getCourseId()) || tpl.id().equalsIgnoreCase(data.getCourseId())) {
                                return tpl.displayName();
                            }
                        }
                    }
                    return data.getCourseId();
                });

        String plainCourseName = courseOpt
                .map(c -> PlainTextComponentSerializer.plainText().serialize(c.getDisplayName()))
                .orElseGet(() -> {
                    if (plugin.getGeneratorManager() != null) {
                        for (var tpl : plugin.getGeneratorManager().getTemplates()) {
                            if (tpl.leaderboardCourseId().equalsIgnoreCase(data.getCourseId()) || tpl.id().equalsIgnoreCase(data.getCourseId())) {
                                return PlainTextComponentSerializer.plainText().serialize(
                                    net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(tpl.displayName())
                                );
                            }
                        }
                    }
                    return data.getCourseId();
                });

        boolean isGen = plugin.getGeneratorManager() != null && plugin.getGeneratorManager().getTemplates().stream()
                .anyMatch(t -> t.leaderboardCourseId().equalsIgnoreCase(data.getCourseId()) || t.id().equalsIgnoreCase(data.getCourseId()));
        String templateId = isGen ? plugin.getGeneratorManager().getTemplates().stream()
                .filter(t -> t.leaderboardCourseId().equalsIgnoreCase(data.getCourseId()) || t.id().equalsIgnoreCase(data.getCourseId()))
                .map(me.usainsrht.basicparkour.api.generator.GeneratorTemplate::id)
                .findFirst()
                .orElse(data.getCourseId()) : data.getCourseId();

        String command = isGen ? "/basicparkour endless " + templateId : "/basicparkour join " + data.getCourseId();

        if (data.getType() == HologramType.LEADERBOARD) {
            plugin.getRepository().getLeaderboard(data.getCourseId(), data.getLimit())
                    .thenAccept(board -> {
                        List<String> lines = renderLeaderboardLines(data, formattedCourseName, plainCourseName, board, isGen, templateId, command);
                        applyPapiAndPublish(data, lines);
                    });
        } else if (data.getType() == HologramType.COURSE_INFO) {
            plugin.getRepository().getLeaderboard(data.getCourseId(), 1)
                    .thenAccept(board -> {
                        List<String> lines = renderCourseInfoLines(data, formattedCourseName, plainCourseName, courseOpt.orElse(null), board, isGen, templateId, command);
                        applyPapiAndPublish(data, lines);
                    });
        } else if (data.getType() == HologramType.CUSTOM) {
            List<String> rawLines = data.getCustomLines();
            List<String> lines = new ArrayList<>();
            if (rawLines.isEmpty()) {
                lines.add("<gold>━━━ <bold>" + formattedCourseName + "</bold> ━━━</gold>");
                lines.add("<gray>Custom hologram</gray>");
            } else {
                for (String raw : rawLines) {
                    lines.add(replaceCoursePlaceholders(raw, data, formattedCourseName, plainCourseName, isGen, templateId, command, 0));
                }
            }
            applyPapiAndPublish(data, lines);
        }
    }

    private @NotNull List<String> renderLeaderboardLines(@NotNull ParkourHologramData data,
                                                         @NotNull String formattedCourseName,
                                                         @NotNull String plainCourseName,
                                                         @NotNull List<LeaderboardEntry> board,
                                                         boolean isGen,
                                                         @NotNull String templateId,
                                                         @NotNull String command) {
        List<String> lines = new ArrayList<>();
        List<String> customTemplate = !data.getCustomLines().isEmpty() ? data.getCustomLines() : config.getLeaderboardLines();

        if (customTemplate != null && !customTemplate.isEmpty()) {
            for (String raw : customTemplate) {
                if (raw.contains("{entries}") || raw.contains("{leaderboard_entries}")) {
                    appendLeaderboardEntries(lines, data, formattedCourseName, plainCourseName, board, isGen, templateId, command);
                } else {
                    lines.add(replaceCoursePlaceholders(raw, data, formattedCourseName, plainCourseName, isGen, templateId, command, board.size()));
                }
            }
        } else {
            // Header
            for (String h : config.getLeaderboardHeader()) {
                lines.add(replaceCoursePlaceholders(h, data, formattedCourseName, plainCourseName, isGen, templateId, command, board.size()));
            }
            // Entries
            appendLeaderboardEntries(lines, data, formattedCourseName, plainCourseName, board, isGen, templateId, command);
            // Footer
            List<String> footers = isGen ? config.getLeaderboardFooterEndless() : config.getLeaderboardFooter();
            for (String f : footers) {
                lines.add(replaceCoursePlaceholders(f, data, formattedCourseName, plainCourseName, isGen, templateId, command, board.size()));
            }
        }
        return lines;
    }

    private void appendLeaderboardEntries(@NotNull List<String> lines,
                                          @NotNull ParkourHologramData data,
                                          @NotNull String formattedCourseName,
                                          @NotNull String plainCourseName,
                                          @NotNull List<LeaderboardEntry> board,
                                          boolean isGen,
                                          @NotNull String templateId,
                                          @NotNull String command) {
        if (board.isEmpty()) {
            String empty = config.getLeaderboardEmpty();
            if (empty != null && !empty.isBlank()) {
                lines.add(replaceCoursePlaceholders(empty, data, formattedCourseName, plainCourseName, isGen, templateId, command, 0));
            }
        } else {
            for (LeaderboardEntry entry : board) {
                String format = entry.blocks() > 0 ? config.getLeaderboardEntryFormatBlocks() : config.getLeaderboardEntryFormat();
                lines.add(replaceEntryPlaceholders(format, entry, data, formattedCourseName, plainCourseName));
            }
        }
    }

    private @NotNull List<String> renderCourseInfoLines(@NotNull ParkourHologramData data,
                                                        @NotNull String formattedCourseName,
                                                        @NotNull String plainCourseName,
                                                        @Nullable ParkourCourse course,
                                                        @NotNull List<LeaderboardEntry> board,
                                                        boolean isGen,
                                                        @NotNull String templateId,
                                                        @NotNull String command) {
        int checkpoints = course != null ? course.getCheckpoints().size() : 0;
        LeaderboardEntry top = board.isEmpty() ? null : board.get(0);

        String recordLine;
        if (top != null) {
            String format = top.blocks() > 0 ? config.getCourseInfoRecordBlocks() : config.getCourseInfoRecord();
            recordLine = replaceEntryPlaceholders(format, top, data, formattedCourseName, plainCourseName);
        } else {
            recordLine = replaceCoursePlaceholders(config.getCourseInfoRecordNone(), data, formattedCourseName, plainCourseName, isGen, templateId, command, 0);
        }

        String cpLine = replaceCoursePlaceholders(config.getCourseInfoCheckpoints(), data, formattedCourseName, plainCourseName, isGen, templateId, command, board.size())
                .replace("{checkpoints}", String.valueOf(checkpoints));

        List<String> footers = isGen ? config.getCourseInfoFooterEndless() : config.getCourseInfoFooter();
        List<String> customTemplate = !data.getCustomLines().isEmpty() ? data.getCustomLines() : config.getCourseInfoLines();
        List<String> lines = new ArrayList<>();

        if (customTemplate != null && !customTemplate.isEmpty()) {
            for (String raw : customTemplate) {
                if (raw.contains("{record}")) {
                    lines.add(recordLine);
                } else if (raw.contains("{checkpoints}")) {
                    if (course != null) lines.add(cpLine);
                } else if (raw.contains("{footer}")) {
                    for (String f : footers) {
                        lines.add(replaceCoursePlaceholders(f, data, formattedCourseName, plainCourseName, isGen, templateId, command, board.size()));
                    }
                } else {
                    lines.add(replaceCoursePlaceholders(raw, data, formattedCourseName, plainCourseName, isGen, templateId, command, board.size()));
                }
            }
        } else {
            for (String h : config.getCourseInfoHeader()) {
                lines.add(replaceCoursePlaceholders(h, data, formattedCourseName, plainCourseName, isGen, templateId, command, board.size()));
            }
            if (course != null) {
                lines.add(cpLine);
            }
            if (recordLine != null && !recordLine.isBlank()) {
                lines.add(recordLine);
            }
            for (String f : footers) {
                lines.add(replaceCoursePlaceholders(f, data, formattedCourseName, plainCourseName, isGen, templateId, command, board.size()));
            }
        }
        return lines;
    }

    private @NotNull String replaceCoursePlaceholders(@NotNull String template,
                                                      @NotNull ParkourHologramData data,
                                                      @NotNull String formattedCourseName,
                                                      @NotNull String plainCourseName,
                                                      boolean isGen,
                                                      @NotNull String templateId,
                                                      @NotNull String command,
                                                      int count) {
        return template
                .replace("{course}", formattedCourseName)
                .replace("{course_name}", plainCourseName)
                .replace("{course_id}", data.getCourseId())
                .replace("{limit}", String.valueOf(data.getLimit()))
                .replace("{count}", String.valueOf(count))
                .replace("{template_id}", templateId)
                .replace("{command}", command);
    }

    private @NotNull String replaceEntryPlaceholders(@NotNull String template,
                                                     @NotNull LeaderboardEntry entry,
                                                     @NotNull ParkourHologramData data,
                                                     @NotNull String formattedCourseName,
                                                     @NotNull String plainCourseName) {
        return template
                .replace("{rank}", String.valueOf(entry.rank()))
                .replace("{player}", entry.playerName())
                .replace("{time}", TimerFormatter.format(entry.timeMs()))
                .replace("{time_ms}", String.valueOf(entry.timeMs()))
                .replace("{time_raw}", String.valueOf(entry.timeMs()))
                .replace("{blocks}", String.valueOf(entry.blocks()))
                .replace("{uuid}", entry.playerUuid().toString())
                .replace("{course}", formattedCourseName)
                .replace("{course_name}", plainCourseName)
                .replace("{course_id}", data.getCourseId())
                .replace("{limit}", String.valueOf(data.getLimit()));
    }

    private void applyPapiAndPublish(@NotNull ParkourHologramData data, @NotNull List<String> lines) {
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            for (int i = 0; i < lines.size(); i++) {
                try {
                    lines.set(i, me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(null, lines.get(i)));
                } catch (Throwable ignored) {}
            }
        }
        plugin.getScheduler().runGlobal(() -> {
            if (activeProvider != null) {
                activeProvider.createOrUpdateHologram(data.getName(), data.getLocation(), lines);
            }
        });
    }

    public void shutdown() {
        stopRefreshLoop();
        for (ParkourHologramData data : holograms.values()) {
            if (activeProvider != null) {
                activeProvider.deleteHologram(data.getName());
            }
        }
        providers.forEach(HologramProvider::shutdown);
    }
}
