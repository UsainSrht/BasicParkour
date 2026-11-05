package me.usainsrht.basicparkour.core.display;

import me.usainsrht.basicparkour.api.storage.LeaderboardEntry;
import me.usainsrht.basicparkour.core.BasicParkourPlugin;
import me.usainsrht.basicparkour.core.miniplaceholders.MiniPlaceholdersHook;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.Context;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns a player (online or offline) into a display-name {@link Component} using the MiniMessage
 * format configured at {@code player-display.format} in config.yml.
 *
 * <p>The format may use MiniMessage and any MiniPlaceholders tag, e.g.
 * {@code <red><luckperms_prefix><player>} or {@code <scchatuser_displayname>}. Available built-in tags:
 * {@code <player>} (plain name) and {@code <uuid>}.</p>
 *
 * <ul>
 *   <li><b>Online players:</b> MiniPlaceholders audience + global placeholders are resolved against the player
 *       (on the player's entity thread).</li>
 *   <li><b>Offline players:</b> there is no audience, so only global placeholders resolve. Audience-only tags are
 *       removed instead of being printed raw. {@code <scchatuser_displayname>} is special-cased: the display
 *       name is fetched asynchronously from SCChat (which loads the LuckPerms user) and inserted.</li>
 * </ul>
 * Results are cached for {@code player-display.cache-seconds}.
 */
public final class PlayerNameResolver {

    private static final String SCCHAT_TAG = "scchatuser_displayname";
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final BasicParkourPlugin plugin;
    private final Map<UUID, Cached> cache = new ConcurrentHashMap<>();
    private volatile String cachedFormat = "";


    private record Cached(Component component, long expiresAt) {}

    public PlayerNameResolver(@NotNull BasicParkourPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Public API ─────────────────────────────────────────────────────────

    public @NotNull String getFormat() {
        String f = plugin.getConfig().getString("player-display.format", "<player>");
        return f == null || f.isBlank() ? "<player>" : f;
    }

    /** Resolves one player's display name. Never fails: falls back to the plain name. */
    public @NotNull CompletableFuture<Component> resolve(@NotNull UUID uuid, @Nullable String fallbackName) {
        final String name = fallbackName != null ? fallbackName : "Unknown";
        final String format = getFormat();

        if (!format.equals(cachedFormat)) { // config reloaded with a different format
            cache.clear();
            cachedFormat = format;
        }
        long ttlMs = (long) (plugin.getConfig().getDouble("player-display.cache-seconds", 30.0) * 1000.0);
        long now = System.currentTimeMillis();
        Cached hit = cache.get(uuid);
        if (hit != null && ttlMs > 0 && hit.expiresAt() > now) {
            return CompletableFuture.completedFuture(hit.component());
        }

        CompletableFuture<Component> result;
        Player online = Bukkit.getPlayer(uuid);
        if (online != null && online.isOnline()) {
            result = resolveOnline(online, format, name);
        } else {
            result = resolveOffline(uuid, format, name);
        }

        return result
            .exceptionally(ex -> Component.text(name))
            .thenApply(component -> {
                if (ttlMs > 0) {
                    long t = System.currentTimeMillis();
                    cache.put(uuid, new Cached(component, t + ttlMs));
                    if (cache.size() > 256) cache.values().removeIf(c -> c.expiresAt() <= t); // keep it bounded
                }
                return component;
            });
    }

    /** Resolves every distinct player of a leaderboard. The map is keyed by player UUID. */
    public @NotNull CompletableFuture<Map<UUID, Component>> resolveAll(@NotNull Collection<LeaderboardEntry> entries) {
        Map<UUID, Component> out = new ConcurrentHashMap<>();
        List<CompletableFuture<Void>> futures = new java.util.ArrayList<>();
        for (LeaderboardEntry e : entries) {
            if (out.containsKey(e.playerUuid())) continue;
            out.put(e.playerUuid(), Component.text(e.playerName())); // placeholder until resolved
            futures.add(resolve(e.playerUuid(), e.playerName())
                .thenAccept(c -> out.put(e.playerUuid(), c)));
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenApply(v -> out);
    }

    /** Same as {@link #resolveAll} but as MiniMessage strings, for text-based hologram templates. */
    public @NotNull CompletableFuture<Map<UUID, String>> resolveAllAsMiniMessage(@NotNull Collection<LeaderboardEntry> entries) {
        return resolveAll(entries).thenApply(map -> {
            Map<UUID, String> strings = new HashMap<>();
            map.forEach((uuid, comp) -> strings.put(uuid, MINI.serialize(comp)));
            return strings;
        });
    }

    public void clearCache() {
        cache.clear();
    }

    // ── Online ─────────────────────────────────────────────────────────────

    private @NotNull CompletableFuture<Component> resolveOnline(@NotNull Player player, @NotNull String format, @NotNull String name) {
        CompletableFuture<Component> future = new CompletableFuture<>();
        // MiniPlaceholders audience placeholders read live player state: do it on the entity's thread
        plugin.getScheduler().runOnEntity(player, () -> {
            try {
                TagResolver base = TagResolver.resolver(
                    Placeholder.unparsed("player", name),
                    Placeholder.unparsed("uuid", player.getUniqueId().toString()));
                future.complete(MINI.deserialize(format, player,
                    base, MiniPlaceholdersHook.getAudienceResolver(player)));
            } catch (Throwable t) {
                future.complete(Component.text(name));
            }
        });
        return future;
    }

    // ── Offline ────────────────────────────────────────────────────────────

    private @NotNull CompletableFuture<Component> resolveOffline(@NotNull UUID uuid, @NotNull String format, @NotNull String name) {
        CompletableFuture<Component> scChat = format.contains(SCCHAT_TAG)
            ? fetchScChatDisplayName(uuid)
            : CompletableFuture.completedFuture(null);

        return scChat.handle((scName, err) -> {
            try {
                TagResolver base = TagResolver.resolver(
                    Placeholder.unparsed("player", name),
                    Placeholder.unparsed("uuid", uuid.toString()));
                Component scComponent = scName != null ? scName : Component.text(name);
                TagResolver scTag = TagResolver.resolver(SCCHAT_TAG,
                    (ArgumentQueue args, Context ctx) -> Tag.selfClosingInserting(scComponent));
                // No audience offline: global MiniPlaceholders only; leftover namespaced tags are stripped
                return MINI.deserialize(format, base, scTag,
                    MiniPlaceholdersHook.getGlobalResolver(), STRIP_UNKNOWN_PLACEHOLDERS);
            } catch (Throwable t) {
                return Component.text(name);
            }
        });
    }

    /**
     * Removes placeholder-looking tags (namespace_name) nobody resolved, so an offline player never shows a raw
     * {@code <luckperms_prefix>}. Real MiniMessage tags with underscores (named colours, lang_or) are excluded.
     */
    static final TagResolver STRIP_UNKNOWN_PLACEHOLDERS = new TagResolver() {
        @Override
        public @Nullable Tag resolve(@NotNull String name, @NotNull ArgumentQueue arguments, @NotNull Context ctx) {
            if (!has(name)) return null;
            while (arguments.hasNext()) arguments.pop();
            return Tag.selfClosingInserting(Component.empty());
        }

        @Override
        public boolean has(@NotNull String name) {
            return name.indexOf('_') > 0
                && NamedTextColor.NAMES.value(name) == null
                && !name.equals("lang_or");
        }
    };

    // ── SCChat (optional soft dependency) ──────────────────────────────────

    /**
     * Asks SCChat for the player's display name (it loads the LuckPerms user asynchronously for offline players).
     * Completes with {@code null} when SCChat is missing or the call fails.
     */
    private @NotNull CompletableFuture<Component> fetchScChatDisplayName(@NotNull UUID uuid) {
        if (!SCChatHook.isAvailable()) return CompletableFuture.completedFuture(null);
        try {
            return SCChatHook.displayName(uuid);
        } catch (Throwable t) { // e.g. NoClassDefFoundError / incompatible SCChat
            plugin.getDebugManager().log("PlayerDisplay", "SCChat display name lookup failed: " + t);
            return CompletableFuture.completedFuture(null);
        }
    }
}
