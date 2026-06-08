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

    /** Per-player cooldown to avoid triggering twice on the same block change. */
    private final Set<UUID> portalCooldown = Collections.newSetFromMap(new WeakHashMap<>());

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
                portalEntries.add(new PortalEntry(templateId, worldName,
                    Math.min(minX, maxX), Math.min(minY, maxY), Math.min(minZ, maxZ),
                    Math.max(minX, maxX), Math.max(minY, maxY), Math.max(minZ, maxZ)));
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
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();

        // Only process block changes
        if (sameBlock(from, to)) return;

        // Cooldown: skip if just triggered
        if (portalCooldown.contains(player.getUniqueId())) {
            portalCooldown.remove(player.getUniqueId());
            return;
        }

        // Already in a session — skip
        if (plugin.getBasicParkourAPI().getSessionManager().getSession(player).isPresent()) return;
        if (generatorManager.getInstance(player).isPresent()) return;

        String worldName = to.getWorld().getName();

        for (PortalEntry entry : portalEntries) {
            if (!entry.worldName().equals(worldName)) continue;
            if (entry.contains(to)) {
                portalCooldown.add(player.getUniqueId());
                startGeneratedRun(player, entry.templateId());
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerInteract(@NotNull PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) return;
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;

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
        portalCooldown.remove(event.getPlayer().getUniqueId());
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private void startGeneratedRun(@NotNull Player player, @NotNull String templateId) {
        Optional<GeneratorTemplate> tplOpt = generatorManager.getTemplate(templateId);
        if (tplOpt.isEmpty()) {
            player.sendMessage(net.kyori.adventure.text.Component.text(
                "§cUnknown generator template: " + templateId));
            return;
        }
        generatorManager.generate(player, tplOpt.get())
            .thenAccept(session -> {
                if (session == null) {
                    plugin.getScheduler().runOnEntity(player, () ->
                        player.sendMessage(net.kyori.adventure.text.Component.text(
                            "§cCould not start generated run. Try again later.")));
                }
            });
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
        boolean contains(@NotNull Location loc) {
            return loc.getX() >= minX && loc.getX() <= maxX
                && loc.getY() >= minY && loc.getY() <= maxY
                && loc.getZ() >= minZ && loc.getZ() <= maxZ;
        }
    }
}
