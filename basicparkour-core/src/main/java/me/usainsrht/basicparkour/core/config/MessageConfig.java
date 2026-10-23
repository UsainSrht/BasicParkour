package me.usainsrht.basicparkour.core.config;

import me.usainsrht.yamlmessage.YamlMessage;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Message manager powered by YamlMessageAPI.
 *
 * <p>Every message can be configured in {@code messages.yml} as a simple string,
 * a list of strings, or a full object containing {@code chat}, {@code actionbar},
 * {@code title}, and {@code sound} (Adventure {@link net.kyori.adventure.sound.Sound}).</p>
 */
public final class MessageConfig {

    private final ConcurrentHashMap<String, YamlMessage> messages = new ConcurrentHashMap<>();

    private YamlMessage courseStart;
    private YamlMessage checkpointReach;
    private YamlMessage courseComplete;
    private YamlMessage sessionFail;
    private YamlMessage personalBest;
    private YamlMessage timerDisplay;
    private YamlMessage leaderboardHeader;
    private YamlMessage leaderboardEntry;
    private YamlMessage noCourse;
    private YamlMessage alreadyInSession;
    private YamlMessage sessionLeave;
    private YamlMessage notInSession;
    private YamlMessage timeLimitExceeded;
    private YamlMessage reloadSuccess;
    private YamlMessage resetPbSuccess;
    private YamlMessage wandGiven;
    private YamlMessage pos1Set;
    private YamlMessage pos2Set;
    private YamlMessage courseCreated;
    private YamlMessage startSet;
    private YamlMessage endSet;
    private YamlMessage portalSet;
    private YamlMessage checkpointAdded;
    private YamlMessage courseSaved;
    private YamlMessage ghostToggled;

    public MessageConfig(@NotNull ConfigurationSection cfg) {
        reload(cfg);
    }

    public void reload(@NotNull ConfigurationSection cfg) {
        messages.clear();
        ConfigurationSection msgSec = cfg.isConfigurationSection("messages")
                ? cfg.getConfigurationSection("messages")
                : cfg;

        if (msgSec != null) {
            for (String key : msgSec.getKeys(false)) {
                messages.put(key, YamlMessage.parse(msgSec.get(key)));
            }
        }

        courseStart = getMessage("course-start",
            "<gradient:green:aqua>▶ <bold>Basic Parkour</bold> started on <course>! Good luck!</gradient>");
        checkpointReach = getMessage("checkpoint-reach",
            "<green>✔ Checkpoint <white><checkpoint></white> reached! <dark_gray>[<white><time></white>]</dark_gray>");
        courseComplete = getMessage("course-complete",
            "<gold><bold>🏁 Course complete!</bold></gold> <yellow>Time: <white><time></white></yellow>");
        sessionFail = getMessage("session-fail",
            "<red>✗ Failed! <gray>Respawning at last checkpoint…</gray></red>");
        personalBest = getMessage("personal-best",
            "<aqua>🏆 <bold>New personal best!</bold> <white><time></white></aqua>");
        timerDisplay = getMessage("timer-display",
            "<gray>⏱ <white><time></white>  <dark_gray>CP: <white><checkpoint></white>/<white><total></white></dark_gray>");
        leaderboardHeader = getMessage("leaderboard-header",
            "<gold>━━━ <bold>Leaderboard — <course></bold> ━━━</gold>");
        leaderboardEntry = getMessage("leaderboard-entry",
            "  <gray><rank>. <white><player></white> — <yellow><time></yellow></gray>");
        noCourse = getMessage("no-course", "<red>No course found with that ID.</red>");
        alreadyInSession = getMessage("already-in-session",
            "<yellow>You are already running a course! Use <white>/basicparkour leave</white> to exit.</yellow>");
        sessionLeave = getMessage("session-leave", "<gray>You left the course.</gray>");
        notInSession = getMessage("not-in-session", "<yellow>You are not in an active parkour session.</yellow>");
        timeLimitExceeded = getMessage("time-limit-exceeded", "<red>Time limit exceeded!</red>");
        reloadSuccess = getMessage("reload-success", "<green>Basic Parkour configuration and courses reloaded.</green>");
        resetPbSuccess = getMessage("reset-pb-success", "<green>Personal best records reset for <player>.</green>");
        wandGiven = getMessage("wand-given", "<green>Parkour editor wand received. Left click: Pos 1, Right click: Pos 2.</green>");
        pos1Set = getMessage("pos1-set", "<aqua>Position 1 set to <white><x>, <y>, <z></white></aqua>");
        pos2Set = getMessage("pos2-set", "<aqua>Position 2 set to <white><x>, <y>, <z></white></aqua>");
        courseCreated = getMessage("course-created", "<green>Course <white><course></white> created! Next: set start, end, and checkpoints.</green>");
        startSet = getMessage("start-set", "<green>Start region and spawn location set for <white><course></white>.</green>");
        endSet = getMessage("end-set", "<green>End region set for <white><course></white>.</green>");
        portalSet = getMessage("portal-set", "<green>Portal region set for <white><course></white>.</green>");
        checkpointAdded = getMessage("checkpoint-added", "<green>Checkpoint #<index> added to <white><course></white>.</green>");
        courseSaved = getMessage("course-saved", "<green>Course <white><course></white> successfully saved and loaded!</green>");
        ghostToggled = getMessage("ghost-toggled", "<gray>Ghost playback is now: <state>.</gray>");
    }

    private YamlMessage getMessage(@NotNull String key, @NotNull String defaultMiniMessage) {
        return messages.computeIfAbsent(key, k -> YamlMessage.chat(defaultMiniMessage));
    }

    public @NotNull YamlMessage get(@NotNull String key) {
        return messages.getOrDefault(key, YamlMessage.empty());
    }

    public void send(@NotNull YamlMessage msg, @NotNull net.kyori.adventure.audience.Audience audience, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... resolvers) {
        msg.send(audience, me.usainsrht.basicparkour.core.miniplaceholders.MiniPlaceholdersHook.combine(audience, resolvers));
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public @NotNull YamlMessage getCourseStart() { return courseStart; }
    public @NotNull YamlMessage getCheckpointReach() { return checkpointReach; }
    public @NotNull YamlMessage getCourseComplete() { return courseComplete; }
    public @NotNull YamlMessage getSessionFail() { return sessionFail; }
    public @NotNull YamlMessage getPersonalBest() { return personalBest; }
    public @NotNull YamlMessage getTimerDisplay() { return timerDisplay; }
    public @NotNull YamlMessage getLeaderboardHeader() { return leaderboardHeader; }
    public @NotNull YamlMessage getLeaderboardEntry() { return leaderboardEntry; }
    public @NotNull YamlMessage getNoCourse() { return noCourse; }
    public @NotNull YamlMessage getAlreadyInSession() { return alreadyInSession; }
    public @NotNull YamlMessage getSessionLeave() { return sessionLeave; }
    public @NotNull YamlMessage getNotInSession() { return notInSession; }
    public @NotNull YamlMessage getTimeLimitExceeded() { return timeLimitExceeded; }
    public @NotNull YamlMessage getReloadSuccess() { return reloadSuccess; }
    public @NotNull YamlMessage getResetPbSuccess() { return resetPbSuccess; }
    public @NotNull YamlMessage getWandGiven() { return wandGiven; }
    public @NotNull YamlMessage getPos1Set() { return pos1Set; }
    public @NotNull YamlMessage getPos2Set() { return pos2Set; }
    public @NotNull YamlMessage getCourseCreated() { return courseCreated; }
    public @NotNull YamlMessage getStartSet() { return startSet; }
    public @NotNull YamlMessage getEndSet() { return endSet; }
    public @NotNull YamlMessage getPortalSet() { return portalSet; }
    public @NotNull YamlMessage getCheckpointAdded() { return checkpointAdded; }
    public @NotNull YamlMessage getCourseSaved() { return courseSaved; }
    public @NotNull YamlMessage getGhostToggled() { return ghostToggled; }
}
