package me.usainsrht.basicparkour.core.editor;

import me.usainsrht.basicparkour.api.BasicParkourAPI;
import me.usainsrht.basicparkour.api.course.Checkpoint;
import me.usainsrht.basicparkour.api.course.DieMode;
import me.usainsrht.basicparkour.api.course.ParkourCourse;
import me.usainsrht.basicparkour.core.BasicParkourPlugin;
import me.usainsrht.basicparkour.core.config.CourseLoader;
import me.usainsrht.itemapi.yamlitem.YamlItem;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages in-game course editing sessions, wand selections, and YAML course generation.
 */
public final class EditorManager {

    private final BasicParkourPlugin plugin;
    private final NamespacedKey wandKey;

    private final Map<UUID, Location> pos1Map = new ConcurrentHashMap<>();
    private final Map<UUID, Location> pos2Map = new ConcurrentHashMap<>();
    private final Map<String, DraftCourse> drafts = new ConcurrentHashMap<>();

    public EditorManager(@NotNull BasicParkourPlugin plugin) {
        this.plugin = plugin;
        this.wandKey = new NamespacedKey(plugin, "editor_wand");
    }

    public void setPos1(@NotNull Player player, @NotNull Location loc) {
        pos1Map.put(player.getUniqueId(), loc);
    }

    @Nullable
    public Location getPos1(@NotNull Player player) {
        return pos1Map.get(player.getUniqueId());
    }

    public void setPos2(@NotNull Player player, @NotNull Location loc) {
        pos2Map.put(player.getUniqueId(), loc);
    }

    @Nullable
    public Location getPos2(@NotNull Player player) {
        return pos2Map.get(player.getUniqueId());
    }

    @Nullable
    public BoundingBox getSelectionBox(@NotNull Player player) {
        Location p1 = getPos1(player);
        Location p2 = getPos2(player);
        if (p1 == null || p2 == null) return null;
        if (!Objects.equals(p1.getWorld(), p2.getWorld())) return null;

        double minX = Math.min(p1.getBlockX(), p2.getBlockX());
        double minY = Math.min(p1.getBlockY(), p2.getBlockY());
        double minZ = Math.min(p1.getBlockZ(), p2.getBlockZ());
        double maxX = Math.max(p1.getBlockX(), p2.getBlockX()) + 1.0;
        double maxY = Math.max(p1.getBlockY(), p2.getBlockY()) + 1.0;
        double maxZ = Math.max(p1.getBlockZ(), p2.getBlockZ()) + 1.0;

        return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    @NotNull
    public ItemStack createWandItem() {
        ConfigurationSection wandSec = plugin.getConfig().getConfigurationSection("editor.wand");
        ItemStack item;
        if (wandSec != null) {
            try {
                item = YamlItem.parse(wandSec);
            } catch (Exception e) {
                item = ItemStack.of(Material.BLAZE_ROD);
            }
        } else {
            item = ItemStack.of(Material.BLAZE_ROD);
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isWandItem(@Nullable ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE);
    }

    @NotNull
    public DraftCourse getOrCreateDraft(@NotNull String id, @NotNull World world) {
        return drafts.computeIfAbsent(id.toLowerCase(Locale.ROOT), k -> {
            // If already loaded in course registry, import it!
            Optional<ParkourCourse> existing = BasicParkourAPI.get().getCourseRegistry().getCourse(k);
            if (existing.isPresent()) {
                ParkourCourse c = existing.get();
                String displayName = MiniMessage.miniMessage().serialize(c.getDisplayName());
                DraftCourse draft = new DraftCourse(c.getId(), displayName, c.getWorldName());
                draft.startRegion = BoundingBox.of(c.getStartRegion().getMin(), c.getStartRegion().getMax());
                draft.startLocations.addAll(c.getStartLocations());
                if (c.getPortalRegion().isPresent()) {
                    draft.portalRegion = BoundingBox.of(c.getPortalRegion().get().getMin(), c.getPortalRegion().get().getMax());
                }
                draft.endRegion = BoundingBox.of(c.getEndRegion().getMin(), c.getEndRegion().getMax());
                draft.teleportFromPortal = c.isPortalTeleport();
                draft.minY = c.getMinY().orElse(-64);
                draft.dieMode = c.getDieMode();
                draft.rewards.addAll(c.getRewardCommands());
                for (Checkpoint cp : c.getCheckpoints()) {
                    draft.checkpoints.add(new DraftCheckpoint(
                            BoundingBox.of(cp.getTriggerRegion().getMin(), cp.getTriggerRegion().getMax()),
                            cp.getSpawnLocation()));
                }
                return draft;
            }
            return new DraftCourse(id, id, world.getName());
        });
    }

    @Nullable
    public DraftCourse getDraft(@NotNull String id) {
        return drafts.get(id.toLowerCase(Locale.ROOT));
    }

    @NotNull
    public Set<String> getDraftIds() {
        return Collections.unmodifiableSet(drafts.keySet());
    }

    /**
     * Saves the draft course to plugins/BasicParkour/courses/{id}.yml and registers it.
     */
    public boolean saveCourse(@NotNull String id) throws IOException {
        DraftCourse draft = getDraft(id);
        if (draft == null) return false;
        if (draft.startRegion == null || draft.endRegion == null) {
            return false;
        }

        File coursesDir = new File(plugin.getDataFolder(), "courses");
        if (!coursesDir.exists()) coursesDir.mkdirs();

        File file = new File(coursesDir, draft.id + ".yml");
        YamlConfiguration yaml = new YamlConfiguration();

        yaml.set("id", draft.id);
        yaml.set("display-name", draft.name);
        yaml.set("world", draft.world);

        // Start region
        yaml.set("start-region.min.x", draft.startRegion.getMinX());
        yaml.set("start-region.min.y", draft.startRegion.getMinY());
        yaml.set("start-region.min.z", draft.startRegion.getMinZ());
        yaml.set("start-region.max.x", draft.startRegion.getMaxX());
        yaml.set("start-region.max.y", draft.startRegion.getMaxY());
        yaml.set("start-region.max.z", draft.startRegion.getMaxZ());

        // Portal region
        if (draft.portalRegion != null) {
            yaml.set("portal-region.min.x", draft.portalRegion.getMinX());
            yaml.set("portal-region.min.y", draft.portalRegion.getMinY());
            yaml.set("portal-region.min.z", draft.portalRegion.getMinZ());
            yaml.set("portal-region.max.x", draft.portalRegion.getMaxX());
            yaml.set("portal-region.max.y", draft.portalRegion.getMaxY());
            yaml.set("portal-region.max.z", draft.portalRegion.getMaxZ());
        }
        yaml.set("teleport-from-portal", draft.teleportFromPortal);

        // Start locations
        List<Map<String, Object>> startLocs = new ArrayList<>();
        for (Location loc : draft.startLocations) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("x", loc.getX());
            map.put("y", loc.getY());
            map.put("z", loc.getZ());
            map.put("yaw", (double) loc.getYaw());
            map.put("pitch", (double) loc.getPitch());
            startLocs.add(map);
        }
        yaml.set("start-locations", startLocs);

        // End region
        yaml.set("end-region.min.x", draft.endRegion.getMinX());
        yaml.set("end-region.min.y", draft.endRegion.getMinY());
        yaml.set("end-region.min.z", draft.endRegion.getMinZ());
        yaml.set("end-region.max.x", draft.endRegion.getMaxX());
        yaml.set("end-region.max.y", draft.endRegion.getMaxY());
        yaml.set("end-region.max.z", draft.endRegion.getMaxZ());

        // Checkpoints
        List<Map<String, Object>> cpList = new ArrayList<>();
        for (DraftCheckpoint cp : draft.checkpoints) {
            Map<String, Object> cpMap = new LinkedHashMap<>();
            Map<String, Object> locMap = new LinkedHashMap<>();
            locMap.put("x", cp.respawnLoc.getX());
            locMap.put("y", cp.respawnLoc.getY());
            locMap.put("z", cp.respawnLoc.getZ());
            locMap.put("yaw", (double) cp.respawnLoc.getYaw());
            locMap.put("pitch", (double) cp.respawnLoc.getPitch());
            cpMap.put("location", locMap);

            Map<String, Object> regMap = new LinkedHashMap<>();
            Map<String, Object> minMap = new LinkedHashMap<>();
            minMap.put("x", cp.region.getMinX());
            minMap.put("y", cp.region.getMinY());
            minMap.put("z", cp.region.getMinZ());
            Map<String, Object> maxMap = new LinkedHashMap<>();
            maxMap.put("x", cp.region.getMaxX());
            maxMap.put("y", cp.region.getMaxY());
            maxMap.put("z", cp.region.getMaxZ());
            regMap.put("min", minMap);
            regMap.put("max", maxMap);
            cpMap.put("region", regMap);

            cpList.add(cpMap);
        }
        yaml.set("checkpoints", cpList);

        yaml.set("min-y", draft.minY);
        yaml.set("die-mode", draft.dieMode.name());
        yaml.set("rewards", draft.rewards);

        yaml.save(file);

        // Load into CourseRegistry immediately
        CourseLoader loader = new CourseLoader(plugin);
        Optional<ParkourCourse> loaded = loader.load(file);
        loaded.ifPresent(c -> {
            BasicParkourAPI.get().getCourseRegistry().unregisterCourse(c.getId());
            BasicParkourAPI.get().getCourseRegistry().registerCourse(c);
        });

        return true;
    }

    public static final class DraftCourse {
        public final String id;
        public String name;
        public String world;
        public BoundingBox startRegion;
        public final List<Location> startLocations = new ArrayList<>();
        public BoundingBox portalRegion;
        public boolean teleportFromPortal = true;
        public BoundingBox endRegion;
        public final List<DraftCheckpoint> checkpoints = new ArrayList<>();
        public double minY = -64.0;
        public DieMode dieMode = DieMode.RESTART_DIE;
        public final List<String> rewards = new ArrayList<>();

        public DraftCourse(@NotNull String id, @NotNull String name, @NotNull String world) {
            this.id = id;
            this.name = name;
            this.world = world;
        }
    }

    public static final class DraftCheckpoint {
        public final BoundingBox region;
        public final Location respawnLoc;

        public DraftCheckpoint(@NotNull BoundingBox region, @NotNull Location respawnLoc) {
            this.region = region;
            this.respawnLoc = respawnLoc;
        }
    }
}
