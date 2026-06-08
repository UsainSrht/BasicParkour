package me.usainsrht.basicparkour.core.command;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Typed view over the {@code commands:} section of {@code config.yml}.
 *
 * <p>All command literals and permission nodes are read from config so that
 * server operators can fully rename or restrict them without touching the JAR.
 * A server restart is required for changes to take effect (commands are
 * registered with the Brigadier API during plugin enable).</p>
 */
public final class CommandConfig {

    private final String root;
    private final List<String> aliases;
    private final String permUse;
    private final String permAdmin;

    // Sub-command literals
    private final String cmdJoin;
    private final String cmdLeave;
    private final String cmdTop;
    private final String cmdPb;
    private final String cmdReload;
    private final String cmdResetPb;
    private final String cmdGenerate;
    private final String cmdSetEntry;
    private final String cmdRemoveEntry;

    public CommandConfig(@NotNull FileConfiguration cfg) {
        ConfigurationSection sec = cfg.getConfigurationSection("commands");

        if (sec == null) {
            // Safe defaults — mirrors the values in config.yml
            root           = "basicparkour";
            aliases        = List.of("pk", "parkour");
            permUse        = "basicparkour.use";
            permAdmin      = "basicparkour.admin";
            cmdJoin        = "join";
            cmdLeave       = "leave";
            cmdTop         = "top";
            cmdPb          = "pb";
            cmdReload      = "reload";
            cmdResetPb     = "resetpb";
            cmdGenerate    = "generate";
            cmdSetEntry    = "setentry";
            cmdRemoveEntry = "removeentry";
            return;
        }

        root      = sec.getString("root", "basicparkour");
        aliases   = sec.getStringList("aliases");
        permUse   = sec.getString("permissions.use",   "basicparkour.use");
        permAdmin = sec.getString("permissions.admin", "basicparkour.admin");

        ConfigurationSection sub = sec.getConfigurationSection("subcommands");
        if (sub != null) {
            cmdJoin        = sub.getString("join",         "join");
            cmdLeave       = sub.getString("leave",        "leave");
            cmdTop         = sub.getString("top",          "top");
            cmdPb          = sub.getString("pb",           "pb");
            cmdReload      = sub.getString("reload",       "reload");
            cmdResetPb     = sub.getString("resetpb",      "resetpb");
            cmdGenerate    = sub.getString("generate",     "generate");
            cmdSetEntry    = sub.getString("setentry",     "setentry");
            cmdRemoveEntry = sub.getString("removeentry",  "removeentry");
        } else {
            cmdJoin        = "join";
            cmdLeave       = "leave";
            cmdTop         = "top";
            cmdPb          = "pb";
            cmdReload      = "reload";
            cmdResetPb     = "resetpb";
            cmdGenerate    = "generate";
            cmdSetEntry    = "setentry";
            cmdRemoveEntry = "removeentry";
        }
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    /** Root command literal (e.g. {@code basicparkour}). */
    public @NotNull String getRoot() { return root; }

    /** Aliases for the root command (e.g. {@code [pk, parkour]}). */
    public @NotNull List<String> getAliases() { return aliases; }

    /** Permission node required for player-facing sub-commands. */
    public @NotNull String getPermUse() { return permUse; }

    /** Permission node required for admin-only sub-commands. */
    public @NotNull String getPermAdmin() { return permAdmin; }

    public @NotNull String getCmdJoin()         { return cmdJoin; }
    public @NotNull String getCmdLeave()        { return cmdLeave; }
    public @NotNull String getCmdTop()          { return cmdTop; }
    public @NotNull String getCmdPb()           { return cmdPb; }
    public @NotNull String getCmdReload()       { return cmdReload; }
    public @NotNull String getCmdResetPb()      { return cmdResetPb; }
    public @NotNull String getCmdGenerate()     { return cmdGenerate; }
    public @NotNull String getCmdSetEntry()     { return cmdSetEntry; }
    public @NotNull String getCmdRemoveEntry()  { return cmdRemoveEntry; }
}
