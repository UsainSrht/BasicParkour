package me.usainsrht.basicparkour.core.npc;

import me.usainsrht.basicparkour.api.BasicParkourAPI;
import me.usainsrht.basicparkour.api.course.ParkourCourse;
import me.usainsrht.basicparkour.api.storage.LeaderboardEntry;
import me.usainsrht.basicparkour.core.BasicParkourPlugin;
import me.usainsrht.basicparkour.core.gui.CourseSelectorGUI;
import me.usainsrht.basicparkour.core.gui.GeneratorSelectorGUI;
import me.usainsrht.basicparkour.core.gui.LeaderboardGUI;
import me.usainsrht.basicparkour.core.util.TimerFormatter;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages Citizens and FancyNPCs links, actions, and dynamic top holder updates.
 */
public final class NpcManager {

    private final BasicParkourPlugin plugin;
    private final CitizensHook citizensHook;
    private final FancyNpcsHook fancyNpcsHook;
    private final Map<String, ParkourNpcData> npcs = new ConcurrentHashMap<>();
    private final File storageFile;

    public NpcManager(@NotNull BasicParkourPlugin plugin) {
        this.plugin = plugin;
        this.storageFile = new File(plugin.getDataFolder(), "npcs.yml");
        this.citizensHook = new CitizensHook(plugin);
        this.fancyNpcsHook = new FancyNpcsHook(plugin);

        load();
        refreshAllTopHolders();
    }

    public @NotNull CitizensHook getCitizensHook() {
        return citizensHook;
    }

    public @NotNull FancyNpcsHook getFancyNpcsHook() {
        return fancyNpcsHook;
    }

    public void load() {
        npcs.clear();
        if (!storageFile.exists()) return;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(storageFile);
        ConfigurationSection root = config.getConfigurationSection("npcs");
        if (root == null) return;

        for (String key : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(key);
            if (sec != null) {
                ParkourNpcData data = ParkourNpcData.deserialize(key, sec);
                if (data != null) {
                    npcs.put(makeKey(data.getPlatform(), data.getId()), data);
                }
            }
        }
        plugin.getLogger().info("[BasicParkour] Loaded " + npcs.size() + " NPC link(s).");
    }

    public void save() {
        YamlConfiguration config = new YamlConfiguration();
        ConfigurationSection root = config.createSection("npcs");
        for (ParkourNpcData data : npcs.values()) {
            ConfigurationSection sec = root.createSection(data.getId());
            data.serialize(sec);
        }
        try {
            config.save(storageFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "[BasicParkour] Failed to save npcs.yml", e);
        }
    }

    public void linkNpc(@NotNull NpcPlatform platform,
                        @NotNull String id,
                        @NotNull NpcAction action,
                        @NotNull String courseId,
                        int rank) {
        ParkourNpcData data = new ParkourNpcData(id, platform, action, courseId, rank);
        npcs.put(makeKey(platform, id), data);

        if (platform == NpcPlatform.CITIZENS) {
            try {
                citizensHook.linkNpc(Integer.parseInt(id), action, courseId);
            } catch (NumberFormatException ignored) {}
        }
        save();

        if (action == NpcAction.TOP_HOLDER) {
            updateTopHolders(courseId);
        }
    }

    public boolean unlinkNpc(@NotNull NpcPlatform platform, @NotNull String id) {
        ParkourNpcData removed = npcs.remove(makeKey(platform, id));
        if (removed != null) {
            save();
            return true;
        }
        return false;
    }

    public @Nullable ParkourNpcData getNpc(@NotNull NpcPlatform platform, @NotNull String id) {
        return npcs.get(makeKey(platform, id));
    }

    public @NotNull Collection<ParkourNpcData> getNpcs() {
        return Collections.unmodifiableCollection(npcs.values());
    }

    public void refreshAllTopHolders() {
        Set<String> courses = new HashSet<>();
        for (ParkourNpcData data : npcs.values()) {
            if (data.getAction() == NpcAction.TOP_HOLDER && !data.getCourseId().isEmpty()) {
                courses.add(data.getCourseId());
            }
        }
        courses.forEach(this::updateTopHolders);
    }

    public void updateTopHolders(@NotNull String courseId) {
        plugin.getRepository().getLeaderboard(courseId, 1).thenAccept(board -> {
            if (board.isEmpty()) return;
            LeaderboardEntry top = board.get(0);

            for (ParkourNpcData data : npcs.values()) {
                if (data.getAction() == NpcAction.TOP_HOLDER && data.getCourseId().equalsIgnoreCase(courseId)) {
                    if (data.getPlatform() == NpcPlatform.CITIZENS) {
                        try {
                            citizensHook.updateTopHolder(Integer.parseInt(data.getId()), top.playerName());
                        } catch (NumberFormatException ignored) {}
                    } else if (data.getPlatform() == NpcPlatform.FANCYNPCS) {
                        fancyNpcsHook.updateTopHolder(data.getId(), top.playerName());
                    }
                }
            }
        });
    }

    public void executeAction(@NotNull Player player, @NotNull NpcAction action, @NotNull String courseId) {
        plugin.getScheduler().runOnEntity(player, () -> {
            switch (action) {
                case JOIN_COURSE -> {
                    if (courseId.isEmpty()) {
                        new CourseSelectorGUI(plugin, player).open();
                        return;
                    }
                    Optional<ParkourCourse> opt = BasicParkourAPI.get().getCourseRegistry().getCourse(courseId);
                    if (opt.isPresent()) {
                        plugin.getSessionManager().startSession(player, opt.get());
                    } else {
                        plugin.getMessages().getNoCourse().send((Audience) player);
                    }
                }
                case OPEN_MENU -> new CourseSelectorGUI(plugin, player).open();
                case OPEN_GENERATOR -> new GeneratorSelectorGUI(plugin, player).open();
                case LEAVE -> {
                    if (plugin.getSessionManager().getSession(player).isPresent()) {
                        plugin.getSessionManager().removeSession(player);
                        plugin.getMessages().getSessionLeave().send((Audience) player);
                    } else {
                        plugin.getMessages().getNotInSession().send((Audience) player);
                    }
                }
                case SHOW_PB -> {
                    if (courseId.isEmpty()) return;
                    plugin.getRepository().getPersonalBest(player.getUniqueId(), courseId).thenAccept(opt ->
                            plugin.getScheduler().runOnEntity(player, () -> {
                                Optional<ParkourCourse> c = BasicParkourAPI.get().getCourseRegistry().getCourse(courseId);
                                String id = c.map(ParkourCourse::getId).orElse(courseId);
                                if (opt.isPresent()) {
                                    me.usainsrht.basicparkour.api.storage.PlayerRecord rec = opt.get();
                                    if (rec.blocks() > 0) {
                                        player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                                                "<aqua>Your PB on <yellow>" + id + "</yellow>: <green>"
                                                        + rec.blocks() + " blocks</green> <dark_gray>(" + TimerFormatter.format(rec.timeMs()) + ")</dark_gray></aqua>"
                                        ));
                                    } else {
                                        player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                                                "<aqua>Your PB on <yellow>" + id + "</yellow>: <white>" + TimerFormatter.format(rec.timeMs()) + "</white></aqua>"
                                        ));
                                    }
                                } else {
                                    player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                                            "<aqua>Your PB on <yellow>" + id + "</yellow>: <white>None</white></aqua>"
                                    ));
                                }
                            }));
                }
                case SHOW_TOP -> {
                    if (courseId.isEmpty()) {
                        new CourseSelectorGUI(plugin, player).open();
                        return;
                    }
                    Optional<ParkourCourse> c = BasicParkourAPI.get().getCourseRegistry().getCourse(courseId);
                    if (c.isPresent()) {
                        new LeaderboardGUI(plugin, player, c.get(), 0).open();
                    }
                }
                case TOP_HOLDER -> {
                    if (!courseId.isEmpty()) {
                        Optional<ParkourCourse> opt = BasicParkourAPI.get().getCourseRegistry().getCourse(courseId);
                        opt.ifPresent(course -> plugin.getSessionManager().startSession(player, course));
                    } else {
                        new CourseSelectorGUI(plugin, player).open();
                    }
                }
            }
        });
    }

    private static String makeKey(NpcPlatform platform, String id) {
        return platform.name() + ":" + id;
    }
}
