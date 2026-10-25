package me.usainsrht.basicparkour.core.hologram;

import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Manages configuration for the Hologram system in config.yml.
 */
public final class HologramConfig {

    private boolean enabled;
    private String provider;
    private long refreshIntervalSeconds;
    private boolean updateOnRecord;
    private boolean updateOnCompletion;
    private int defaultLimit;

    // Leaderboard
    private List<String> leaderboardHeader;
    private String leaderboardEntryFormat;
    private String leaderboardEntryFormatBlocks;
    private String leaderboardEmpty;
    private List<String> leaderboardFooter;
    private List<String> leaderboardFooterEndless;
    private List<String> leaderboardLines;

    // Course Info
    private List<String> courseInfoHeader;
    private String courseInfoCheckpoints;
    private String courseInfoRecord;
    private String courseInfoRecordBlocks;
    private String courseInfoRecordNone;
    private List<String> courseInfoFooter;
    private List<String> courseInfoFooterEndless;
    private List<String> courseInfoLines;

    public HologramConfig(@Nullable ConfigurationSection section) {
        reload(section);
    }

    public void reload(@Nullable ConfigurationSection section) {
        if (section == null) {
            applyDefaults();
            return;
        }

        this.enabled = section.getBoolean("enabled", true);
        this.provider = section.getString("provider", "auto");

        ConfigurationSection updateSec = section.getConfigurationSection("update");
        if (updateSec != null) {
            this.refreshIntervalSeconds = updateSec.getLong("interval-seconds",
                    updateSec.getLong("interval_seconds", section.getLong("refresh-interval-seconds", 60L)));
            this.updateOnRecord = updateSec.getBoolean("on-record",
                    updateSec.getBoolean("on_record", updateSec.getBoolean("update-on-record", true)));
            this.updateOnCompletion = updateSec.getBoolean("on-completion",
                    updateSec.getBoolean("on_completion", updateSec.getBoolean("update-on-completion", false)));
        } else {
            this.refreshIntervalSeconds = section.getLong("refresh-interval-seconds",
                    section.getLong("refresh_interval_seconds", 60L));
            this.updateOnRecord = section.getBoolean("update-on-record",
                    section.getBoolean("update_on_record", true));
            this.updateOnCompletion = section.getBoolean("update-on-completion",
                    section.getBoolean("update_on_completion", false));
        }

        this.defaultLimit = Math.max(1, section.getInt("default-limit", section.getInt("default_limit", 10)));

        // Leaderboard
        ConfigurationSection lbSec = section.getConfigurationSection("leaderboard");
        if (lbSec != null) {
            this.leaderboardHeader = getStringList(lbSec, "header", List.of("<gold>━━━ <bold>{course}</bold> Top {limit} ━━━</gold>"));
            this.leaderboardEntryFormat = lbSec.getString("entry-format",
                    lbSec.getString("entry_format", lbSec.getString("entry",
                            "<yellow>#{rank} </yellow><white>{player}</white> <gray>—</gray> <aqua>{time}</aqua>")));
            this.leaderboardEntryFormatBlocks = lbSec.getString("entry-format-blocks",
                    lbSec.getString("entry_format_blocks", lbSec.getString("entry-blocks",
                            "<yellow>#{rank} </yellow><white>{player}</white> <gray>—</gray> <green>{blocks} blocks</green> <dark_gray>({time})</dark_gray>")));
            this.leaderboardEmpty = lbSec.getString("empty", "<gray>No records yet. Be the first!</gray>");
            this.leaderboardFooter = getStringList(lbSec, "footer", List.of("<gray>Race now: <white>/basicparkour join {course_id}</white></gray>"));
            this.leaderboardFooterEndless = getStringList(lbSec, "footer-endless",
                    getStringList(lbSec, "footer_endless", List.of("<gray>Play now: <white>/basicparkour endless {template_id}</white></gray>")));
            this.leaderboardLines = lbSec.isList("lines") ? lbSec.getStringList("lines") : null;
        } else {
            this.leaderboardHeader = List.of("<gold>━━━ <bold>{course}</bold> Top {limit} ━━━</gold>");
            this.leaderboardEntryFormat = "<yellow>#{rank} </yellow><white>{player}</white> <gray>—</gray> <aqua>{time}</aqua>";
            this.leaderboardEntryFormatBlocks = "<yellow>#{rank} </yellow><white>{player}</white> <gray>—</gray> <green>{blocks} blocks</green> <dark_gray>({time})</dark_gray>";
            this.leaderboardEmpty = "<gray>No records yet. Be the first!</gray>";
            this.leaderboardFooter = List.of("<gray>Race now: <white>/basicparkour join {course_id}</white></gray>");
            this.leaderboardFooterEndless = List.of("<gray>Play now: <white>/basicparkour endless {template_id}</white></gray>");
            this.leaderboardLines = null;
        }

        // Course Info
        ConfigurationSection ciSec = section.getConfigurationSection("course-info");
        if (ciSec == null) {
            ciSec = section.getConfigurationSection("course_info");
        }
        if (ciSec != null) {
            this.courseInfoHeader = getStringList(ciSec, "header", List.of("<gradient:#ff9a9e:#fecfef><bold>{course}</bold></gradient>"));
            this.courseInfoCheckpoints = ciSec.getString("checkpoints", "<gray>Checkpoints: <white>{checkpoints}</white></gray>");
            this.courseInfoRecord = ciSec.getString("record", "<gray>Record: <aqua>{time}</aqua> by <white>{player}</white></gray>");
            this.courseInfoRecordBlocks = ciSec.getString("record-blocks",
                    ciSec.getString("record_blocks", "<gray>Record: <green>{blocks} blocks</green> <dark_gray>({time})</dark_gray> by <white>{player}</white></gray>"));
            this.courseInfoRecordNone = ciSec.getString("record-none",
                    ciSec.getString("record_none", "<gray>Record: <white>None</white></gray>"));
            this.courseInfoFooter = getStringList(ciSec, "footer", List.of("<green>Type <white>/basicparkour join {course_id}</white> to play!</green>"));
            this.courseInfoFooterEndless = getStringList(ciSec, "footer-endless",
                    getStringList(ciSec, "footer_endless", List.of("<green>Type <white>/basicparkour endless {template_id}</white> to play!</green>")));
            this.courseInfoLines = ciSec.isList("lines") ? ciSec.getStringList("lines") : null;
        } else {
            this.courseInfoHeader = List.of("<gradient:#ff9a9e:#fecfef><bold>{course}</bold></gradient>");
            this.courseInfoCheckpoints = "<gray>Checkpoints: <white>{checkpoints}</white></gray>";
            this.courseInfoRecord = "<gray>Record: <aqua>{time}</aqua> by <white>{player}</white></gray>";
            this.courseInfoRecordBlocks = "<gray>Record: <green>{blocks} blocks</green> <dark_gray>({time})</dark_gray> by <white>{player}</white></gray>";
            this.courseInfoRecordNone = "<gray>Record: <white>None</white></gray>";
            this.courseInfoFooter = List.of("<green>Type <white>/basicparkour join {course_id}</white> to play!</green>");
            this.courseInfoFooterEndless = List.of("<green>Type <white>/basicparkour endless {template_id}</white> to play!</green>");
            this.courseInfoLines = null;
        }
    }

    private void applyDefaults() {
        this.enabled = true;
        this.provider = "auto";
        this.refreshIntervalSeconds = 60L;
        this.updateOnRecord = true;
        this.updateOnCompletion = false;
        this.defaultLimit = 10;

        this.leaderboardHeader = List.of("<gold>━━━ <bold>{course}</bold> Top {limit} ━━━</gold>");
        this.leaderboardEntryFormat = "<yellow>#{rank} </yellow><white>{player}</white> <gray>—</gray> <aqua>{time}</aqua>";
        this.leaderboardEntryFormatBlocks = "<yellow>#{rank} </yellow><white>{player}</white> <gray>—</gray> <green>{blocks} blocks</green> <dark_gray>({time})</dark_gray>";
        this.leaderboardEmpty = "<gray>No records yet. Be the first!</gray>";
        this.leaderboardFooter = List.of("<gray>Race now: <white>/basicparkour join {course_id}</white></gray>");
        this.leaderboardFooterEndless = List.of("<gray>Play now: <white>/basicparkour endless {template_id}</white></gray>");
        this.leaderboardLines = null;

        this.courseInfoHeader = List.of("<gradient:#ff9a9e:#fecfef><bold>{course}</bold></gradient>");
        this.courseInfoCheckpoints = "<gray>Checkpoints: <white>{checkpoints}</white></gray>";
        this.courseInfoRecord = "<gray>Record: <aqua>{time}</aqua> by <white>{player}</white></gray>";
        this.courseInfoRecordBlocks = "<gray>Record: <green>{blocks} blocks</green> <dark_gray>({time})</dark_gray> by <white>{player}</white></gray>";
        this.courseInfoRecordNone = "<gray>Record: <white>None</white></gray>";
        this.courseInfoFooter = List.of("<green>Type <white>/basicparkour join {course_id}</white> to play!</green>");
        this.courseInfoFooterEndless = List.of("<green>Type <white>/basicparkour endless {template_id}</white> to play!</green>");
        this.courseInfoLines = null;
    }

    private @NotNull List<String> getStringList(@NotNull ConfigurationSection sec, @NotNull String path, @NotNull List<String> fallback) {
        if (sec.isList(path)) {
            return sec.getStringList(path);
        } else if (sec.isString(path)) {
            String str = sec.getString(path);
            return str != null ? List.of(str) : fallback;
        }
        return fallback;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public @NotNull String getProvider() {
        return provider != null ? provider : "auto";
    }

    public long getRefreshIntervalSeconds() {
        return refreshIntervalSeconds;
    }

    public boolean isUpdateOnRecord() {
        return updateOnRecord;
    }

    public boolean isUpdateOnCompletion() {
        return updateOnCompletion;
    }

    public int getDefaultLimit() {
        return defaultLimit;
    }

    // Leaderboard
    public @NotNull List<String> getLeaderboardHeader() {
        return leaderboardHeader != null ? Collections.unmodifiableList(leaderboardHeader) : Collections.emptyList();
    }

    public @NotNull String getLeaderboardEntryFormat() {
        return leaderboardEntryFormat != null ? leaderboardEntryFormat : "";
    }

    public @NotNull String getLeaderboardEntryFormatBlocks() {
        return leaderboardEntryFormatBlocks != null ? leaderboardEntryFormatBlocks : "";
    }

    public @NotNull String getLeaderboardEmpty() {
        return leaderboardEmpty != null ? leaderboardEmpty : "";
    }

    public @NotNull List<String> getLeaderboardFooter() {
        return leaderboardFooter != null ? Collections.unmodifiableList(leaderboardFooter) : Collections.emptyList();
    }

    public @NotNull List<String> getLeaderboardFooterEndless() {
        return leaderboardFooterEndless != null ? Collections.unmodifiableList(leaderboardFooterEndless) : Collections.emptyList();
    }

    public @Nullable List<String> getLeaderboardLines() {
        return leaderboardLines != null ? Collections.unmodifiableList(leaderboardLines) : null;
    }

    // Course Info
    public @NotNull List<String> getCourseInfoHeader() {
        return courseInfoHeader != null ? Collections.unmodifiableList(courseInfoHeader) : Collections.emptyList();
    }

    public @NotNull String getCourseInfoCheckpoints() {
        return courseInfoCheckpoints != null ? courseInfoCheckpoints : "";
    }

    public @NotNull String getCourseInfoRecord() {
        return courseInfoRecord != null ? courseInfoRecord : "";
    }

    public @NotNull String getCourseInfoRecordBlocks() {
        return courseInfoRecordBlocks != null ? courseInfoRecordBlocks : "";
    }

    public @NotNull String getCourseInfoRecordNone() {
        return courseInfoRecordNone != null ? courseInfoRecordNone : "";
    }

    public @NotNull List<String> getCourseInfoFooter() {
        return courseInfoFooter != null ? Collections.unmodifiableList(courseInfoFooter) : Collections.emptyList();
    }

    public @NotNull List<String> getCourseInfoFooterEndless() {
        return courseInfoFooterEndless != null ? Collections.unmodifiableList(courseInfoFooterEndless) : Collections.emptyList();
    }

    public @Nullable List<String> getCourseInfoLines() {
        return courseInfoLines != null ? Collections.unmodifiableList(courseInfoLines) : null;
    }
}
