package me.usainsrht.basicparkour.core.session;

import me.usainsrht.basicparkour.core.scheduling.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.NotNull;

/**
 * Manages a dedicated scoreboard {@link Team} used to suppress player-vs-player
 * collision during active parkour sessions.
 *
 * <p>Minecraft's team option {@code collisionRule = NEVER} is the server-authoritative
 * way to prevent two players from pushing each other off platforms.</p>
 * <p>On Folia, the main scoreboard is unsupported, so this gracefully falls back
 * to setting {@code player.setCollidable(false)}.</p>
 */
public final class ParkourTeamManager {

    /** Internal scoreboard team name. Must be ≤ 16 characters. */
    private static final String TEAM_NAME = "bp_nocollide";

    private final FoliaScheduler scheduler;
    private final boolean isFolia;
    
    private Scoreboard scoreboard;
    private Team team;

    public ParkourTeamManager(@NotNull FoliaScheduler scheduler) {
        this.scheduler = scheduler;
        this.isFolia = FoliaScheduler.isFolia();
        
        if (!isFolia) {
            // Paper/Spigot: safe to use main scoreboard
            this.scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
            Team existing = scoreboard.getTeam(TEAM_NAME);
            this.team = (existing != null) ? existing : scoreboard.registerNewTeam(TEAM_NAME);
            this.team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        } else {
            // Folia: Main scoreboard is unsupported. We will use fallback.
            this.team = null;
        }
    }

    /**
     * Adds {@code player} to the no-collision team, or applies the Folia fallback.
     *
     * @param player the player entering a parkour session
     */
    public void addPlayer(@NotNull Player player) {
        if (team != null) {
            team.addPlayer(player);
        } else if (isFolia) {
            scheduler.runOnEntity(player, () -> player.setCollidable(false));
        }
    }

    /**
     * Removes {@code player} from the no-collision team, restoring normal physics.
     *
     * @param player the player whose session ended
     */
    public void removePlayer(@NotNull Player player) {
        if (team != null) {
            team.removePlayer(player);
        } else if (isFolia) {
            scheduler.runOnEntity(player, () -> player.setCollidable(true));
        }
    }

    /**
     * Unregisters the team on plugin disable.
     */
    public void cleanup() {
        if (team != null) {
            team.unregister();
        }
    }
}
