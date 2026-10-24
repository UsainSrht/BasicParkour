package me.usainsrht.basicparkour.core.papi;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.usainsrht.basicparkour.api.BasicParkourAPI;
import me.usainsrht.basicparkour.api.course.ParkourCourse;
import me.usainsrht.basicparkour.api.session.ParkourSession;
import me.usainsrht.basicparkour.core.BasicParkourPlugin;
import me.usainsrht.basicparkour.core.util.TimerFormatter;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PlaceholderAPI expansion for BasicParkour.
 *
 * <p>Supported placeholders:
 * <ul>
 *   <li>{@code %basicparkour_course_count%}</li>
 *   <li>{@code %basicparkour_in_session%}</li>
 *   <li>{@code %basicparkour_current_course%}</li>
 *   <li>{@code %basicparkour_current_time%}</li>
 *   <li>{@code %basicparkour_current_checkpoint%}</li>
 *   <li>{@code %basicparkour_pb_<courseId>%}</li>
 *   <li>{@code %basicparkour_pb_raw_<courseId>%}</li>
 *   <li>{@code %basicparkour_top_player_<courseId>_<rank>%}</li>
 *   <li>{@code %basicparkour_top_time_<courseId>_<rank>%}</li>
 * </ul>
 * </p>
 */
public final class BasicParkourExpansion extends PlaceholderExpansion {

    private final BasicParkourPlugin plugin;

    // Short-lived async caches for DB queries (TTL = 10 seconds)
    private static final long CACHE_TTL_MS = 10_000L;
    private final Map<String, CacheEntry<String>> pbCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<String>> topPlayerCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<String>> topTimeCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<String>> topBlocksCache = new ConcurrentHashMap<>();

    public BasicParkourExpansion(@NotNull BasicParkourPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    @NotNull
    public String getIdentifier() {
        return "basicparkour";
    }

    @Override
    @NotNull
    public String getAuthor() {
        return "UsainSrht";
    }

    @Override
    @NotNull
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    @Nullable
    public String onRequest(@Nullable OfflinePlayer offlinePlayer, @NotNull String params) {
        Player player = offlinePlayer != null && offlinePlayer.isOnline() ? offlinePlayer.getPlayer() : null;

        if (params.equalsIgnoreCase("course_count")) {
            return String.valueOf(BasicParkourAPI.get().getCourseRegistry().getCourses().size());
        }

        if (params.equalsIgnoreCase("in_session")) {
            if (player == null) return "false";
            return String.valueOf(plugin.getSessionManager().getSession(player).isPresent());
        }

        if (params.equalsIgnoreCase("current_course")) {
            if (player == null) return "None";
            return plugin.getSessionManager().getSession(player)
                    .map(s -> s.getCourse().getId())
                    .orElse("None");
        }

        if (params.equalsIgnoreCase("current_time")) {
            if (player == null) return "00:00.000";
            return plugin.getSessionManager().getSession(player)
                    .map(s -> TimerFormatter.format(s.getElapsedMillis()))
                    .orElse("00:00.000");
        }

        if (params.equalsIgnoreCase("current_checkpoint")) {
            if (player == null) return "0";
            return plugin.getSessionManager().getSession(player)
                    .map(s -> String.valueOf(s.getCurrentCheckpointIndex()))
                    .orElse("0");
        }

        if (params.equalsIgnoreCase("current_blocks")) {
            if (player == null) return "0";
            return plugin.getSessionManager().getSession(player)
                    .map(s -> String.valueOf(s.getCurrentCheckpointIndex()))
                    .orElse("0");
        }

        // %basicparkour_pb_blocks_<courseId>%
        if (params.toLowerCase().startsWith("pb_blocks_")) {
            if (offlinePlayer == null) return "0";
            String courseId = params.substring(10);
            String cacheKey = offlinePlayer.getUniqueId() + ":" + courseId + ":blocks";

            CacheEntry<String> cached = pbCache.get(cacheKey);
            long now = System.currentTimeMillis();
            if (cached != null && now - cached.timestamp < CACHE_TTL_MS) {
                return cached.value;
            }

            plugin.getRepository().getPersonalBest(offlinePlayer.getUniqueId(), courseId)
                    .thenAccept(opt -> {
                        String val = opt.map(r -> String.valueOf(r.blocks())).orElse("0");
                        pbCache.put(cacheKey, new CacheEntry<>(val, System.currentTimeMillis()));
                    });

            return cached != null ? cached.value : "...";
        }

        // %basicparkour_pb_<courseId>%
        if (params.toLowerCase().startsWith("pb_")) {
            if (offlinePlayer == null) return "None";
            boolean raw = params.toLowerCase().startsWith("pb_raw_");
            String courseId = raw ? params.substring(7) : params.substring(3);
            String cacheKey = offlinePlayer.getUniqueId() + ":" + courseId + (raw ? ":raw" : "");

            CacheEntry<String> cached = pbCache.get(cacheKey);
            long now = System.currentTimeMillis();
            if (cached != null && now - cached.timestamp < CACHE_TTL_MS) {
                return cached.value;
            }

            // Async trigger cache fill
            plugin.getRepository().getPersonalBest(offlinePlayer.getUniqueId(), courseId)
                    .thenAccept(opt -> {
                        String val = opt.map(r -> raw ? String.valueOf(r.timeMs()) : TimerFormatter.format(r.timeMs())).orElse("None");
                        pbCache.put(cacheKey, new CacheEntry<>(val, System.currentTimeMillis()));
                    });

            return cached != null ? cached.value : "...";
        }

        // %basicparkour_top_player_<courseId>_<rank>%
        if (params.toLowerCase().startsWith("top_player_")) {
            String sub = params.substring(11);
            int lastUnderscore = sub.lastIndexOf('_');
            if (lastUnderscore == -1) return "None";

            String courseId = sub.substring(0, lastUnderscore);
            int rank;
            try {
                rank = Integer.parseInt(sub.substring(lastUnderscore + 1));
            } catch (NumberFormatException e) {
                return "None";
            }

            String cacheKey = courseId + ":" + rank;
            CacheEntry<String> cached = topPlayerCache.get(cacheKey);
            long now = System.currentTimeMillis();
            if (cached != null && now - cached.timestamp < CACHE_TTL_MS) {
                return cached.value;
            }

            plugin.getRepository().getLeaderboard(courseId, Math.max(10, rank))
                    .thenAccept(board -> {
                        for (var entry : board) {
                            topPlayerCache.put(courseId + ":" + entry.rank(),
                                    new CacheEntry<>(entry.playerName(), System.currentTimeMillis()));
                            topTimeCache.put(courseId + ":" + entry.rank(),
                                    new CacheEntry<>(TimerFormatter.format(entry.timeMs()), System.currentTimeMillis()));
                            topBlocksCache.put(courseId + ":" + entry.rank(),
                                    new CacheEntry<>(String.valueOf(entry.blocks()), System.currentTimeMillis()));
                        }
                    });

            return cached != null ? cached.value : "...";
        }

        // %basicparkour_top_time_<courseId>_<rank>%
        if (params.toLowerCase().startsWith("top_time_")) {
            String sub = params.substring(9);
            int lastUnderscore = sub.lastIndexOf('_');
            if (lastUnderscore == -1) return "None";

            String courseId = sub.substring(0, lastUnderscore);
            int rank;
            try {
                rank = Integer.parseInt(sub.substring(lastUnderscore + 1));
            } catch (NumberFormatException e) {
                return "None";
            }

            String cacheKey = courseId + ":" + rank;
            CacheEntry<String> cached = topTimeCache.get(cacheKey);
            long now = System.currentTimeMillis();
            if (cached != null && now - cached.timestamp < CACHE_TTL_MS) {
                return cached.value;
            }

            plugin.getRepository().getLeaderboard(courseId, Math.max(10, rank))
                    .thenAccept(board -> {
                        for (var entry : board) {
                            topPlayerCache.put(courseId + ":" + entry.rank(),
                                    new CacheEntry<>(entry.playerName(), System.currentTimeMillis()));
                            topTimeCache.put(courseId + ":" + entry.rank(),
                                    new CacheEntry<>(TimerFormatter.format(entry.timeMs()), System.currentTimeMillis()));
                            topBlocksCache.put(courseId + ":" + entry.rank(),
                                    new CacheEntry<>(String.valueOf(entry.blocks()), System.currentTimeMillis()));
                        }
                    });

            return cached != null ? cached.value : "...";
        }

        // %basicparkour_top_blocks_<courseId>_<rank>%
        if (params.toLowerCase().startsWith("top_blocks_")) {
            String sub = params.substring(11);
            int lastUnderscore = sub.lastIndexOf('_');
            if (lastUnderscore == -1) return "0";

            String courseId = sub.substring(0, lastUnderscore);
            int rank;
            try {
                rank = Integer.parseInt(sub.substring(lastUnderscore + 1));
            } catch (NumberFormatException e) {
                return "0";
            }

            String cacheKey = courseId + ":" + rank;
            CacheEntry<String> cached = topBlocksCache.get(cacheKey);
            long now = System.currentTimeMillis();
            if (cached != null && now - cached.timestamp < CACHE_TTL_MS) {
                return cached.value;
            }

            plugin.getRepository().getLeaderboard(courseId, Math.max(10, rank))
                    .thenAccept(board -> {
                        for (var entry : board) {
                            topPlayerCache.put(courseId + ":" + entry.rank(),
                                    new CacheEntry<>(entry.playerName(), System.currentTimeMillis()));
                            topTimeCache.put(courseId + ":" + entry.rank(),
                                    new CacheEntry<>(TimerFormatter.format(entry.timeMs()), System.currentTimeMillis()));
                            topBlocksCache.put(courseId + ":" + entry.rank(),
                                    new CacheEntry<>(String.valueOf(entry.blocks()), System.currentTimeMillis()));
                        }
                    });

            return cached != null ? cached.value : "...";
        }

        return null;
    }

    private record CacheEntry<T>(T value, long timestamp) {}
}
