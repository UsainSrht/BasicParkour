package me.usainsrht.basicparkour.core.listener;

import me.usainsrht.basicparkour.api.generator.GeneratorTemplate;
import me.usainsrht.basicparkour.core.BasicParkourPlugin;
import me.usainsrht.basicparkour.core.generator.GeneratorManagerImpl;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Detects when players should be placed into a generated parkour run.
 *
 * <h2>Two entry mechanisms</h2>
 * <ol>
 *   <li><strong>AABB portal</strong> — The admin defines a rectangular bounding box in
 *       {@code config.yml} under {@code generator.entry-points.<id>}. Walking into it
 *       starts a generated run for that template.</li>
 *   <li><strong>Interactive objects</strong> (sign, button, lever) — The admin registers
 *       the block location via {@code /basicparkour setentry <templateId> interact}.
 *       Right-clicking the block starts the run.</li>
 * </ol>
 *
 * <p>Entry points are loaded from {@code config.yml} on startup and refreshed on reload.</p>
 */
public final class GeneratorEntryListener implements Listener {

    /**
     * {@code [BasicParkour]} marker expected on line 0 of a registered sign
     * (case-insensitive, square brackets stripped for comparison).
     */
    private static final String SIGN_MARKER = "[BasicParkour]";

    private final BasicParkourPlugin plugin;
    private final GeneratorManagerImpl generatorManager;

    /** Loaded portal entry points. */
    private final List<PortalEntry> portalEntries = new ArrayList<>();

    /** Loaded interact entry points (block location key → templateId). */
    private final Map<String, String> interactEntries = new HashMap<>();

    /** Per-player timestamp cooldown to avoid re-triggering immediately when teleported or moving. */
    private final Map<UUID, Long> entryCooldownUntil = new ConcurrentHashMap<>();

    public void setEntryCooldown(@NotNull UUID uuid, long durationMillis) {
        entryCooldownUntil.put(uuid, System.currentTimeMillis() + durationMillis);
    }

    public boolean isOnCooldown(@NotNull UUID uuid) {
        Long until = entryCooldownUntil.get(uuid);
        if (until == null) return false;
        if (System.currentTimeMillis() < until) return true;
        entryCooldownUntil.remove(uuid);
        return false;
    }

    public GeneratorEntryListener(
        @NotNull BasicParkourPlugin plugin,
        @NotNull GeneratorManagerImpl generatorManager
    ) {
        this.plugin           = plugin;
        this.generatorManager = generatorManager;
        reloadEntries();
    }

    // ── Config reload ──────────────────────────────────────────────────────

    /**
     * Re-reads all entry points from {@code config.yml}.
     * Call this after {@link BasicParkourPlugin#reloadPlugin()}.
     */
    public void reloadEntries() {
        portalEntries.clear();
        interactEntries.clear();

        ConfigurationSection root = plugin.getConfig()
            .getConfigurationSection("generator.entry-points");
        if (root == null) return;

        for (String templateId : root.getKeys(false)) {
            ConfigurationSection ep = root.getConfigurationSection(templateId);
            if (ep == null) continue;

            String type = ep.getString("type", "portal");
            String worldName = ep.getString("world");
            if (worldName == null) continue;

            if ("portal".equalsIgnoreCase(type)) {
                // Expect min/max coords
                if (!ep.contains("min.x") || !ep.contains("max.x")) continue;
                double minX = ep.getDouble("min.x"), minY = ep.getDouble("min.y"), minZ = ep.getDouble("min.z");
                double maxX = ep.getDouble("max.x"), maxY = ep.getDouble("max.y"), maxZ = ep.getDouble("max.z");
                double lowerX = Math.min(minX, maxX);
                double upperX = Math.max(minX, maxX);
                double lowerY = Math.min(minY, maxY);
                double upperY = Math.max(minY, maxY);
                double lowerZ = Math.min(minZ, maxZ);
                double upperZ = Math.max(minZ, maxZ);
                // If min equals max on any axis, expand to encompass the full 1-block span
                if (upperX == lowerX) upperX += 1.0;
                if (upperY == lowerY) upperY += 1.0;
                if (upperZ == lowerZ) upperZ += 1.0;
                portalEntries.add(new PortalEntry(templateId, worldName,
                    lowerX, lowerY, lowerZ,
                    upperX, upperY, upperZ));
            } else {
                // interact: single block location
                double x = ep.getDouble("x"), y = ep.getDouble("y"), z = ep.getDouble("z");
                String key = worldName + "," + (int) x + "," + (int) y + "," + (int) z;
                interactEntries.put(key, templateId);
            }
        }
    }

    // ── Event handlers ─────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(@NotNull PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();

        // Only process position changes (skip pure camera rotation)
        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) return;

        Player player = event.getPlayer();

        // Cooldown: skip if just triggered or recently left/failed a session
        if (isOnCooldown(player.getUniqueId())) return;

        // Already in a session — skip
        if (plugin.getBasicParkourAPI().getSessionManager().getSession(player).isPresent()) return;
        if (generatorManager.getInstance(player).isPresent()) return;

        String worldName = to.getWorld().getName();

        for (PortalEntry entry : portalEntries) {
            if (!entry.worldName().equals(worldName)) continue;
            if (entry.matches(player, to)) {
                setEntryCooldown(player.getUniqueId(), 1000L);
                startGeneratedRun(player, entry.templateId());
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerInteract(@NotNull PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) return;
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK
            && event.getAction() != org.bukkit.event.block.Action.PHYSICAL) return;

        Player player = event.getPlayer();

        // Already in a session — skip
        if (plugin.getBasicParkourAPI().getSessionManager().getSession(player).isPresent()) return;
        if (generatorManager.getInstance(player).isPresent()) return;

        // Check registered interact entry points
        String key = block.getWorld().getName() + "," + block.getX() + "," + block.getY() + "," + block.getZ();
        String templateId = interactEntries.get(key);

        if (templateId == null) {
            // Check if it's a sign with [BasicParkour] on line 0
            templateId = readSignTemplateId(block);
        }

        if (templateId != null) {
            event.setCancelled(true);
            startGeneratedRun(player, templateId);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        generatorManager.onSessionEnd(event.getPlayer());
        entryCooldownUntil.remove(event.getPlayer().getUniqueId());
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private void startGeneratedRun(@NotNull Player player, @NotNull String templateId) {
        Optional<GeneratorTemplate> tplOpt = generatorManager.getTemplate(templateId);
        if (tplOpt.isPresent()) {
            generatorManager.generate(player, tplOpt.get())
                .thenAccept(session -> {
                    if (session == null) {
                        plugin.getScheduler().runOnEntity(player, () ->
                            player.sendMessage(net.kyori.adventure.text.Component.text(
                                "§cCould not start generated run. Try again later.")));
                    }
                });
            return;
        }

        // Support premade courses as well for walk-in entry!
        var courseOpt = plugin.getBasicParkourAPI().getCourseRegistry().getCourse(templateId);
        if (courseOpt.isPresent()) {
            plugin.getScheduler().runOnEntity(player, () ->
                plugin.getBasicParkourAPI().getSessionManager().startSession(player, courseOpt.get(), false));
            return;
        }

        player.sendMessage(net.kyori.adventure.text.Component.text(
            "§cUnknown generator template or course: " + templateId));
    }

    @Nullable
    private String readSignTemplateId(@NotNull Block block) {
        if (!(block.getState() instanceof Sign sign)) return null;
        // Line 0 = [BasicParkour], Line 1 = templateId
        String line0 = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
            .plainText().serialize(sign.line(0)).trim();
        if (!line0.equalsIgnoreCase(SIGN_MARKER) && !line0.equalsIgnoreCase("BasicParkour")) return null;
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
            .plainText().serialize(sign.line(1)).trim();
    }

    private static boolean sameBlock(@NotNull Location a, @NotNull Location b) {
        return a.getBlockX() == b.getBlockX()
            && a.getBlockY() == b.getBlockY()
            && a.getBlockZ() == b.getBlockZ();
    }

    // ── Portal entry record ────────────────────────────────────────────────

    private record PortalEntry(
        @NotNull String templateId,
        @NotNull String worldName,
        double minX, double minY, double minZ,
        double maxX, double maxY, double maxZ
    ) {
        boolean overlaps(@NotNull org.bukkit.util.BoundingBox box) {
            return box.getMinX() <= maxX && box.getMaxX() >= minX
                && box.getMinY() <= (maxY + 0.1) && box.getMaxY() >= (minY - 0.25)
                && box.getMinZ() <= maxZ && box.getMaxZ() >= minZ;
        }

        boolean contains(@NotNull Location loc) {
            double x = loc.getX();
            double y = loc.getY();
            double z = loc.getZ();
            return x >= minX && x <= maxX
                && y >= (minY - 0.25) && y <= (maxY + 0.1)
                && z >= minZ && z <= maxZ;
        }

        boolean matches(@NotNull Player player, @NotNull Location loc) {
            if (!player.getWorld().getName().equals(worldName)) return false;
            try {
                if (overlaps(player.getBoundingBox())) return true;
            } catch (Throwable ignored) {}
            return contains(loc);
        }
    }
}
