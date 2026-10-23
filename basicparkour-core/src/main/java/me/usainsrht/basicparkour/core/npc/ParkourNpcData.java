package me.usainsrht.basicparkour.core.npc;

import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Data model for a configured BasicParkour NPC link.
 */
public final class ParkourNpcData {

    private final String id; // Citizens NPC ID or FancyNpc name
    private final NpcPlatform platform;
    private final NpcAction action;
    private final String courseId;
    private int rank;

    public ParkourNpcData(@NotNull String id,
                          @NotNull NpcPlatform platform,
                          @NotNull NpcAction action,
                          @NotNull String courseId,
                          int rank) {
        this.id = id;
        this.platform = platform;
        this.action = action;
        this.courseId = courseId;
        this.rank = Math.max(1, rank);
    }

    public @NotNull String getId() {
        return id;
    }

    public @NotNull NpcPlatform getPlatform() {
        return platform;
    }

    public @NotNull NpcAction getAction() {
        return action;
    }

    public @NotNull String getCourseId() {
        return courseId;
    }

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = Math.max(1, rank);
    }

    public void serialize(@NotNull ConfigurationSection section) {
        section.set("platform", platform.name());
        section.set("action", action.name());
        section.set("course", courseId);
        section.set("rank", rank);
    }

    public static @Nullable ParkourNpcData deserialize(@NotNull String id, @NotNull ConfigurationSection section) {
        try {
            String platStr = section.getString("platform", "CITIZENS");
            NpcPlatform platform = NpcPlatform.valueOf(platStr.toUpperCase());
            String actStr = section.getString("action", "JOIN_COURSE");
            NpcAction action = NpcAction.valueOf(actStr.toUpperCase());
            String courseId = section.getString("course", "");
            int rank = section.getInt("rank", 1);

            return new ParkourNpcData(id, platform, action, courseId, rank);
        } catch (Exception e) {
            return null;
        }
    }
}
