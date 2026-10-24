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
    private final String cmdMenu;
    private final String cmdGhost;
    private final String cmdReload;
    private final String cmdResetPb;
    private final String cmdGenerate;
    private final String cmdSetEntry;
    private final String cmdRemoveEntry;
    private final String cmdWand;
    private final String cmdCreate;
    private final String cmdSetStart;
    private final String cmdSetEnd;
    private final String cmdSetPortal;
    private final String cmdAddCp;
    private final String cmdSave;
    private final String cmdRestart;
    private final String cmdCheckpoint;
    private final String cmdPos1;
    private final String cmdPos2;
    private final String cmdEndless;

    public CommandConfig(@NotNull FileConfiguration cfg) {
        ConfigurationSection sec = cfg.getConfigurationSection("commands");

        ConfigurationSection earlySub = sec != null ? sec.getConfigurationSection("subcommands") : null;
        cmdPos1    = earlySub != null ? earlySub.getString("pos1", "pos1") : "pos1";
        cmdPos2    = earlySub != null ? earlySub.getString("pos2", "pos2") : "pos2";
        cmdEndless = earlySub != null ? earlySub.getString("endless", "endless") : "endless";

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
            cmdMenu        = "menu";
            cmdGhost       = "ghost";
            cmdReload      = "reload";
            cmdResetPb     = "resetpb";
            cmdGenerate    = "generate";
            cmdSetEntry    = "setentry";
            cmdRemoveEntry = "removeentry";
            cmdWand        = "wand";
            cmdCreate      = "create";
            cmdSetStart    = "setstart";
            cmdSetEnd      = "setend";
            cmdSetPortal   = "setportal";
            cmdAddCp       = "addcp";
            cmdSave        = "save";
            cmdRestart     = "restart";
            cmdCheckpoint  = "checkpoint";
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
            cmdMenu        = sub.getString("menu",         "menu");
            cmdGhost       = sub.getString("ghost",        "ghost");
            cmdReload      = sub.getString("reload",       "reload");
            cmdResetPb     = sub.getString("resetpb",      "resetpb");
            cmdGenerate    = sub.getString("generate",     "generate");
            cmdSetEntry    = sub.getString("setentry",     "setentry");
            cmdRemoveEntry = sub.getString("removeentry",  "removeentry");
            cmdWand        = sub.getString("wand",         "wand");
            cmdCreate      = sub.getString("create",       "create");
            cmdSetStart    = sub.getString("setstart",     "setstart");
            cmdSetEnd      = sub.getString("setend",       "setend");
            cmdSetPortal   = sub.getString("setportal",    "setportal");
            cmdAddCp       = sub.getString("addcp",        "addcp");
            cmdSave        = sub.getString("save",         "save");
            cmdRestart     = sub.getString("restart",      "restart");
            cmdCheckpoint  = sub.getString("checkpoint",   "checkpoint");
        } else {
            cmdJoin        = "join";
            cmdLeave       = "leave";
            cmdTop         = "top";
            cmdPb          = "pb";
            cmdMenu        = "menu";
            cmdGhost       = "ghost";
            cmdReload      = "reload";
            cmdResetPb     = "resetpb";
            cmdGenerate    = "generate";
            cmdSetEntry    = "setentry";
            cmdRemoveEntry = "removeentry";
            cmdWand        = "wand";
            cmdCreate      = "create";
            cmdSetStart    = "setstart";
            cmdSetEnd      = "setend";
            cmdSetPortal   = "setportal";
            cmdAddCp       = "addcp";
            cmdSave        = "save";
            cmdRestart     = "restart";
            cmdCheckpoint  = "checkpoint";
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
    public @NotNull String getCmdMenu()         { return cmdMenu; }
    public @NotNull String getCmdGhost()        { return cmdGhost; }
    public @NotNull String getCmdReload()       { return cmdReload; }
    public @NotNull String getCmdResetPb()      { return cmdResetPb; }
    public @NotNull String getCmdGenerate()     { return cmdGenerate; }
    public @NotNull String getCmdSetEntry()     { return cmdSetEntry; }
    public @NotNull String getCmdRemoveEntry()  { return cmdRemoveEntry; }
    public @NotNull String getCmdWand()         { return cmdWand; }
    public @NotNull String getCmdCreate()       { return cmdCreate; }
    public @NotNull String getCmdSetStart()     { return cmdSetStart; }
    public @NotNull String getCmdSetEnd()       { return cmdSetEnd; }
    public @NotNull String getCmdSetPortal()    { return cmdSetPortal; }
    public @NotNull String getCmdAddCp()        { return cmdAddCp; }
    public @NotNull String getCmdSave()         { return cmdSave; }
    public @NotNull String getCmdRestart()      { return cmdRestart; }
    public @NotNull String getCmdCheckpoint()   { return cmdCheckpoint; }
    public @NotNull String getCmdPos1()         { return cmdPos1; }
    public @NotNull String getCmdPos2()         { return cmdPos2; }
    public @NotNull String getCmdEndless()      { return cmdEndless; }
}
