package me.usainsrht.basicparkour.core.hologram;

import me.usainsrht.basicparkour.api.BasicParkourAPI;
import me.usainsrht.basicparkour.api.course.ParkourCourse;
import me.usainsrht.basicparkour.api.storage.LeaderboardEntry;
import me.usainsrht.basicparkour.core.BasicParkourPlugin;
import me.usainsrht.basicparkour.core.scheduling.FoliaScheduler;
import me.usainsrht.basicparkour.core.util.TimerFormatter;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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
    private final Map<String, ParkourHologramData> holograms = new ConcurrentHashMap<>();
    private final List<HologramProvider> providers = new ArrayList<>();
    private HologramProvider activeProvider;
    private FoliaScheduler.RepeatingTask refreshTask;
    private final File storageFile;

    public HologramManager(@NotNull BasicParkourPlugin plugin) {
        this.plugin = plugin;
        this.storageFile = new File(plugin.getDataFolder(), "holograms.yml");

        // Register available providers
        providers.add(new FancyHologramsProvider());
        providers.add(new DecentHologramsProvider());

        selectProvider();
        load();
        startRefreshLoop();
    }

    public void selectProvider() {
        String preferred = plugin.getConfig().getString("hologram.provider", "auto").toLowerCase(Locale.ROOT);
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
        return activeProvider != null && activeProvider.isAvailable();
    }

    private void startRefreshLoop() {
        long intervalTicks = plugin.getConfig().getLong("hologram.refresh-interval-seconds", 60L) * 20L;
        if (intervalTicks > 0) {
            refreshTask = plugin.getScheduler().runRepeatingGlobal(this::refreshAll, intervalTicks, intervalTicks);
        }
    }

    public void load() {
        holograms.clear();
        if (!storageFile.exists()) return;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(storageFile);
        ConfigurationSection root = config.getConfigurationSection("holograms");
        if (root == null) return;

        for (String key : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(key);
            if (sec != null) {
                ParkourHologramData data = ParkourHologramData.deserialize(key, sec);
                if (data != null) {
                    holograms.put(key, data);
                }
            }
        }

        // Render loaded holograms
        holograms.keySet().forEach(this::refreshHologram);
        plugin.getLogger().info("[BasicParkour] Loaded " + holograms.size() + " hologram(s).");
    }

    public void save() {
        YamlConfiguration config = new YamlConfiguration();
        ConfigurationSection root = config.createSection("holograms");
        for (Map.Entry<String, ParkourHologramData> entry : holograms.entrySet()) {
            ConfigurationSection sec = root.createSection(entry.getKey());
            entry.getValue().serialize(sec);
        }
        try {
            config.save(storageFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "[BasicParkour] Failed to save holograms.yml", e);
        }
    }

    public boolean createHologram(@NotNull String name,
                                  @NotNull HologramType type,
                                  @NotNull String courseId,
                                  @NotNull Location location,
                                  int limit) {
        if (!isEnabled()) return false;
        ParkourHologramData data = new ParkourHologramData(name, type, courseId, location, limit);
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
            save();
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
        String courseName = courseOpt.map(c -> PlainTextComponentSerializer.plainText().serialize(c.getDisplayName()))
                .orElse(data.getCourseId());

        if (data.getType() == HologramType.LEADERBOARD) {
            plugin.getRepository().getLeaderboard(data.getCourseId(), data.getLimit())
                    .thenAccept(board -> {
                        List<String> lines = new ArrayList<>();
                        lines.add("<gold>━━━ <bold>" + courseName + "</bold> Top " + data.getLimit() + " ━━━</gold>");

                        if (board.isEmpty()) {
                            lines.add("<gray>No records yet. Be the first!</gray>");
                        } else {
                            for (LeaderboardEntry entry : board) {
                                lines.add("<yellow>#" + entry.rank() + " </yellow><white>" + entry.playerName()
                                        + "</white> <gray>—</gray> <aqua>" + TimerFormatter.format(entry.timeMs()) + "</aqua>");
                            }
                        }
                        lines.add("<gray>Race now: <white>/basicparkour join " + data.getCourseId() + "</white></gray>");

                        plugin.getScheduler().runGlobal(() -> {
                            if (activeProvider != null) {
                                activeProvider.createOrUpdateHologram(data.getName(), data.getLocation(), lines);
                            }
                        });
                    });
        } else if (data.getType() == HologramType.COURSE_INFO) {
            plugin.getRepository().getLeaderboard(data.getCourseId(), 1)
                    .thenAccept(board -> {
                        List<String> lines = new ArrayList<>();
                        lines.add("<gradient:#ff9a9e:#fecfef><bold>" + courseName + "</bold></gradient>");
                        if (courseOpt.isPresent()) {
                            ParkourCourse course = courseOpt.get();
                            lines.add("<gray>Checkpoints: <white>" + course.getCheckpoints().size() + "</white></gray>");
                        }
                        if (!board.isEmpty()) {
                            LeaderboardEntry top = board.get(0);
                            lines.add("<gray>Record: <aqua>" + TimerFormatter.format(top.timeMs())
                                    + "</aqua> by <white>" + top.playerName() + "</white></gray>");
                        } else {
                            lines.add("<gray>Record: <white>None</white></gray>");
                        }
                        lines.add("<green>Type <white>/basicparkour join " + data.getCourseId() + "</white> to play!</green>");

                        plugin.getScheduler().runGlobal(() -> {
                            if (activeProvider != null) {
                                activeProvider.createOrUpdateHologram(data.getName(), data.getLocation(), lines);
                            }
                        });
                    });
        }
    }

    public void shutdown() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        for (ParkourHologramData data : holograms.values()) {
            if (activeProvider != null) {
                activeProvider.deleteHologram(data.getName());
            }
        }
        providers.forEach(HologramProvider::shutdown);
    }
}
