package me.usainsrht.basicparkour.core.miniplaceholders;

import io.github.miniplaceholders.api.Expansion;
import me.usainsrht.basicparkour.api.BasicParkourAPI;
import me.usainsrht.basicparkour.api.course.ParkourCourse;
import me.usainsrht.basicparkour.api.session.ParkourSession;
import me.usainsrht.basicparkour.api.storage.LeaderboardEntry;
import me.usainsrht.basicparkour.api.storage.PlayerRecord;
import me.usainsrht.basicparkour.core.BasicParkourPlugin;
import me.usainsrht.basicparkour.core.util.TimerFormatter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.Tag;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registers MiniPlaceholders expansion for BasicParkour with both audience and global placeholders.
 */
public final class BasicParkourMiniPlaceholdersExpansion {

    private final BasicParkourPlugin plugin;
    private Expansion expansion;

    private static final long CACHE_TTL_MS = 10_000L;
    private final Map<String, CacheEntry<String>> pbCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<String>> topPlayerCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<String>> topTimeCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<String>> topBlocksCache = new ConcurrentHashMap<>();

    public BasicParkourMiniPlaceholdersExpansion(@NotNull BasicParkourPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        Expansion.Builder builder = Expansion.builder("basicparkour")
                .author("UsainSrht")
                .version(plugin.getPluginMeta().getVersion());

        // ── Global Placeholders ───────────────────────────────────────────

        // <basicparkour_course_count>
        builder.globalPlaceholder("course_count", (queue, ctx) ->
                Tag.selfClosingInserting(Component.text(BasicParkourAPI.get().getCourseRegistry().getCourses().size())));

        // <basicparkour_active_sessions>
        builder.globalPlaceholder("active_sessions", (queue, ctx) ->
                Tag.selfClosingInserting(Component.text(plugin.getSessionManager().getActiveSessions().size())));

        // <basicparkour_course_display:<courseId>>
        builder.globalPlaceholder("course_display", (queue, ctx) -> {
            if (!queue.hasNext()) return Tag.selfClosingInserting(Component.text("None"));
            String courseId = queue.pop().value();
            Optional<ParkourCourse> opt = BasicParkourAPI.get().getCourseRegistry().getCourse(courseId);
            return opt.map(course -> Tag.selfClosingInserting(course.getDisplayName()))
                    .orElseGet(() -> Tag.selfClosingInserting(Component.text("None")));
        });

        // <basicparkour_course_checkpoints:<courseId>>
        builder.globalPlaceholder("course_checkpoints", (queue, ctx) -> {
            if (!queue.hasNext()) return Tag.selfClosingInserting(Component.text("0"));
            String courseId = queue.pop().value();
            Optional<ParkourCourse> opt = BasicParkourAPI.get().getCourseRegistry().getCourse(courseId);
            return opt.map(course -> Tag.selfClosingInserting(Component.text(course.getCheckpoints().size())))
                    .orElseGet(() -> Tag.selfClosingInserting(Component.text("0")));
        });

        // <basicparkour_top_player:<courseId>:<rank>>
        builder.globalPlaceholder("top_player", (queue, ctx) -> {
            if (!queue.hasNext()) return Tag.selfClosingInserting(Component.text("None"));
            String courseId = queue.pop().value();
            int rank = 1;
            if (queue.hasNext()) {
                try {
                    rank = Integer.parseInt(queue.pop().value());
                } catch (NumberFormatException ignored) {}
            }
            return Tag.selfClosingInserting(Component.text(getTopPlayer(courseId, rank)));
        });

        // <basicparkour_top_time:<courseId>:<rank>>
        builder.globalPlaceholder("top_time", (queue, ctx) -> {
            if (!queue.hasNext()) return Tag.selfClosingInserting(Component.text("None"));
            String courseId = queue.pop().value();
            int rank = 1;
            if (queue.hasNext()) {
                try {
                    rank = Integer.parseInt(queue.pop().value());
                } catch (NumberFormatException ignored) {}
            }
            return Tag.selfClosingInserting(Component.text(getTopTime(courseId, rank, false)));
        });

        // <basicparkour_top_time_raw:<courseId>:<rank>>
        builder.globalPlaceholder("top_time_raw", (queue, ctx) -> {
            if (!queue.hasNext()) return Tag.selfClosingInserting(Component.text("0"));
            String courseId = queue.pop().value();
            int rank = 1;
            if (queue.hasNext()) {
                try {
                    rank = Integer.parseInt(queue.pop().value());
                } catch (NumberFormatException ignored) {}
            }
            return Tag.selfClosingInserting(Component.text(getTopTime(courseId, rank, true)));
        });

        // <basicparkour_top_blocks:<courseId>:<rank>>
        builder.globalPlaceholder("top_blocks", (queue, ctx) -> {
            if (!queue.hasNext()) return Tag.selfClosingInserting(Component.text("0"));
            String courseId = queue.pop().value();
            int rank = 1;
            if (queue.hasNext()) {
                try {
                    rank = Integer.parseInt(queue.pop().value());
                } catch (NumberFormatException ignored) {}
            }
            return Tag.selfClosingInserting(Component.text(getTopBlocks(courseId, rank)));
        });

        // ── Audience Placeholders ─────────────────────────────────────────

        // <basicparkour_in_session>
        builder.audiencePlaceholder(Player.class, "in_session", (player, queue, ctx) -> {
            boolean inSession = plugin.getSessionManager().getSession(player).isPresent();
            return Tag.selfClosingInserting(Component.text(inSession));
        });

        // <basicparkour_current_course>
        builder.audiencePlaceholder(Player.class, "current_course", (player, queue, ctx) -> {
            String course = plugin.getSessionManager().getSession(player)
                    .map(s -> s.getCourse().getId())
                    .orElse("None");
            return Tag.selfClosingInserting(Component.text(course));
        });

        // <basicparkour_current_course_display>
        builder.audiencePlaceholder(Player.class, "current_course_display", (player, queue, ctx) -> {
            Optional<ParkourSession> session = plugin.getSessionManager().getSession(player);
            if (session.isPresent()) {
                return Tag.selfClosingInserting(session.get().getCourse().getDisplayName());
            }
            return Tag.selfClosingInserting(Component.text("None"));
        });

        // <basicparkour_current_time>
        builder.audiencePlaceholder(Player.class, "current_time", (player, queue, ctx) -> {
            String time = plugin.getSessionManager().getSession(player)
                    .map(s -> TimerFormatter.format(s.getElapsedMillis()))
                    .orElse("00:00.000");
            return Tag.selfClosingInserting(Component.text(time));
        });

        // <basicparkour_current_time_raw>
        builder.audiencePlaceholder(Player.class, "current_time_raw", (player, queue, ctx) -> {
            long time = plugin.getSessionManager().getSession(player)
                    .map(ParkourSession::getElapsedMillis)
                    .orElse(0L);
            return Tag.selfClosingInserting(Component.text(time));
        });

        // <basicparkour_current_checkpoint>
        builder.audiencePlaceholder(Player.class, "current_checkpoint", (player, queue, ctx) -> {
            int cp = plugin.getSessionManager().getSession(player)
                    .map(ParkourSession::getCurrentCheckpointIndex)
                    .orElse(0);
            return Tag.selfClosingInserting(Component.text(cp));
        });

        // <basicparkour_current_checkpoint_total>
        builder.audiencePlaceholder(Player.class, "current_checkpoint_total", (player, queue, ctx) -> {
            int total = plugin.getSessionManager().getSession(player)
                    .map(s -> s.getCourse().getCheckpoints().size())
                    .orElse(0);
            return Tag.selfClosingInserting(Component.text(total));
        });

        // <basicparkour_current_blocks>
        builder.audiencePlaceholder(Player.class, "current_blocks", (player, queue, ctx) -> {
            int blocks = plugin.getSessionManager().getSession(player)
                    .map(s -> Math.max(0, s.getCurrentCheckpointIndex()))
                    .orElse(0);
            return Tag.selfClosingInserting(Component.text(blocks));
        });

        // <basicparkour_is_ghost_enabled>
        builder.audiencePlaceholder(Player.class, "is_ghost_enabled", (player, queue, ctx) -> {
            boolean enabled = plugin.getGhostRecorder().isPlayerGhostEnabled(player.getUniqueId());
            return Tag.selfClosingInserting(Component.text(enabled));
        });

        // <basicparkour_pb:<courseId>>
        builder.audiencePlaceholder(Player.class, "pb", (player, queue, ctx) -> {
            if (!queue.hasNext()) return Tag.selfClosingInserting(Component.text("None"));
            String courseId = queue.pop().value();
            return Tag.selfClosingInserting(Component.text(getPlayerPb(player, courseId, false)));
        });

        // <basicparkour_pb_raw:<courseId>>
        builder.audiencePlaceholder(Player.class, "pb_raw", (player, queue, ctx) -> {
            if (!queue.hasNext()) return Tag.selfClosingInserting(Component.text("None"));
            String courseId = queue.pop().value();
            return Tag.selfClosingInserting(Component.text(getPlayerPb(player, courseId, true)));
        });

        // <basicparkour_pb_blocks:<courseId>>
        builder.audiencePlaceholder(Player.class, "pb_blocks", (player, queue, ctx) -> {
            if (!queue.hasNext()) return Tag.selfClosingInserting(Component.text("0"));
            String courseId = queue.pop().value();
            return Tag.selfClosingInserting(Component.text(getPlayerPbBlocks(player, courseId)));
        });

        this.expansion = builder.build();
        this.expansion.register();
    }

    public void unregister() {
        if (expansion != null) {
            expansion.unregister();
            expansion = null;
        }
    }

    private String getPlayerPb(Player player, String courseId, boolean raw) {
        String cacheKey = player.getUniqueId() + ":" + courseId + (raw ? ":raw" : "");
        CacheEntry<String> cached = pbCache.get(cacheKey);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.timestamp < CACHE_TTL_MS) {
            return cached.value;
        }

        plugin.getRepository().getPersonalBest(player.getUniqueId(), courseId)
                .thenAccept(opt -> {
                    String val = opt.map(r -> raw ? String.valueOf(r.timeMs()) : TimerFormatter.format(r.timeMs()))
                            .orElse("None");
                    pbCache.put(cacheKey, new CacheEntry<>(val, System.currentTimeMillis()));
                });

        return cached != null ? cached.value : "...";
    }

    private String getPlayerPbBlocks(Player player, String courseId) {
        String cacheKey = player.getUniqueId() + ":" + courseId + ":blocks";
        CacheEntry<String> cached = pbCache.get(cacheKey);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.timestamp < CACHE_TTL_MS) {
            return cached.value;
        }

        plugin.getRepository().getPersonalBest(player.getUniqueId(), courseId)
                .thenAccept(opt -> {
                    String val = opt.map(r -> String.valueOf(r.blocks())).orElse("0");
                    pbCache.put(cacheKey, new CacheEntry<>(val, System.currentTimeMillis()));
                });

        return cached != null ? cached.value : "...";
    }

    private String getTopPlayer(String courseId, int rank) {
        String cacheKey = courseId + ":" + rank;
        CacheEntry<String> cached = topPlayerCache.get(cacheKey);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.timestamp < CACHE_TTL_MS) {
            return cached.value;
        }

        fetchLeaderboard(courseId, rank);
        return cached != null ? cached.value : "...";
    }

    private String getTopTime(String courseId, int rank, boolean raw) {
        String cacheKey = courseId + ":" + rank + (raw ? ":raw" : "");
        CacheEntry<String> cached = topTimeCache.get(cacheKey);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.timestamp < CACHE_TTL_MS) {
            return cached.value;
        }

        fetchLeaderboard(courseId, rank);
        return cached != null ? cached.value : "...";
    }

    private String getTopBlocks(String courseId, int rank) {
        String cacheKey = courseId + ":" + rank;
        CacheEntry<String> cached = topBlocksCache.get(cacheKey);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.timestamp < CACHE_TTL_MS) {
            return cached.value;
        }

        fetchLeaderboard(courseId, rank);
        return cached != null ? cached.value : "...";
    }

    private void fetchLeaderboard(String courseId, int rank) {
        plugin.getRepository().getLeaderboard(courseId, Math.max(10, rank))
                .thenAccept(board -> {
                    long now = System.currentTimeMillis();
                    for (LeaderboardEntry entry : board) {
                        topPlayerCache.put(courseId + ":" + entry.rank(),
                                new CacheEntry<>(entry.playerName(), now));
                        topTimeCache.put(courseId + ":" + entry.rank(),
                                new CacheEntry<>(TimerFormatter.format(entry.timeMs()), now));
                        topTimeCache.put(courseId + ":" + entry.rank() + ":raw",
                                new CacheEntry<>(String.valueOf(entry.timeMs()), now));
                        topBlocksCache.put(courseId + ":" + entry.rank(),
                                new CacheEntry<>(String.valueOf(entry.blocks()), now));
                    }
                });
    }

    private record CacheEntry<T>(T value, long timestamp) {}
}
