package me.usainsrht.basicparkour.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.usainsrht.basicparkour.api.BasicParkourAPI;
import me.usainsrht.basicparkour.api.course.ParkourCourse;
import me.usainsrht.basicparkour.api.generator.GeneratorTemplate;
import me.usainsrht.basicparkour.api.storage.LeaderboardEntry;
import me.usainsrht.basicparkour.core.BasicParkourPlugin;
import me.usainsrht.basicparkour.core.generator.GeneratorManagerImpl;
import me.usainsrht.basicparkour.core.listener.GeneratorEntryListener;
import me.usainsrht.basicparkour.core.session.SessionManager;
import me.usainsrht.basicparkour.core.util.TimerFormatter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Registers all {@code /basicparkour} (and configured aliases) sub-commands via
 * the Paper Brigadier command API.
 *
 * <h2>Sub-commands</h2>
 * <ul>
 *   <li>{@code join <courseId>} — manually join a premade course</li>
 *   <li>{@code leave} — exit active session</li>
 *   <li>{@code top <courseId> [limit]} — show leaderboard</li>
 *   <li>{@code pb [courseId]} — show personal best(s)</li>
 *   <li>{@code reload} — reload config and courses (admin)</li>
 *   <li>{@code resetpb <player>} — reset player records (admin)</li>
 *   <li>{@code generate <player> <templateId>} — start a generated run for a player (admin)</li>
 *   <li>{@code setentry <templateId> [portal|interact]} — register an entry point (admin)</li>
 *   <li>{@code removeentry <templateId>} — remove an entry point (admin)</li>
 * </ul>
 *
 * <p>All command literals, aliases, and permission nodes are loaded from
 * {@link CommandConfig} which mirrors the {@code commands:} section of
 * {@code config.yml}.</p>
 */
@SuppressWarnings({"UnstableApiUsage", "deprecation"})
public final class BasicParkourCommand {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final BasicParkourPlugin plugin;
    private final SessionManager sessionManager;
    private final CommandConfig cfg;
    private GeneratorManagerImpl generatorManager;
    private GeneratorEntryListener entryListener;

    public BasicParkourCommand(@NotNull BasicParkourPlugin plugin,
                                @NotNull SessionManager sessionManager,
                                @NotNull CommandConfig cfg) {
        this.plugin = plugin;
        this.sessionManager = sessionManager;
        this.cfg = cfg;
    }

    // -----------------------------------------------------------------------
    // Registration
    // -----------------------------------------------------------------------

    /**
     * Builds and registers the root command + all sub-commands with the given
     * Paper {@link Commands} registrar.
     *
     * @param registrar the Brigadier command registrar provided by
     *                  {@link io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents#COMMANDS}
     */
    public void register(@NotNull Commands registrar) {
        LiteralArgumentBuilder<CommandSourceStack> root =
                Commands.literal(cfg.getRoot())
                        .requires(src -> src.getSender().hasPermission(cfg.getPermUse()))
                        .executes(ctx -> {
                            sendHelp(ctx.getSource().getSender());
                            return Command.SINGLE_SUCCESS;
                        });

        // join <courseId>
        root.then(Commands.literal(cfg.getCmdJoin())
                .then(Commands.argument("courseId", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            BasicParkourAPI.get().getCourseRegistry().getCourses()
                                    .forEach(c -> builder.suggest(c.getId()));
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            String courseId = StringArgumentType.getString(ctx, "courseId");
                            cmdJoin(ctx.getSource().getSender(), courseId);
                            return Command.SINGLE_SUCCESS;
                        })));

        // leave
        root.then(Commands.literal(cfg.getCmdLeave())
                .executes(ctx -> {
                    cmdLeave(ctx.getSource().getSender());
                    return Command.SINGLE_SUCCESS;
                }));

        // top <courseId> [limit]
        root.then(Commands.literal(cfg.getCmdTop())
                .then(Commands.argument("courseId", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            BasicParkourAPI.get().getCourseRegistry().getCourses()
                                    .forEach(c -> builder.suggest(c.getId()));
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            String courseId = StringArgumentType.getString(ctx, "courseId");
                            cmdTop(ctx.getSource().getSender(), courseId, 10);
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("limit", IntegerArgumentType.integer(1, 50))
                                .executes(ctx -> {
                                    String courseId = StringArgumentType.getString(ctx, "courseId");
                                    int limit = IntegerArgumentType.getInteger(ctx, "limit");
                                    cmdTop(ctx.getSource().getSender(), courseId, limit);
                                    return Command.SINGLE_SUCCESS;
                                }))));

        // pb [courseId]
        root.then(Commands.literal(cfg.getCmdPb())
                .executes(ctx -> {
                    cmdPbAll(ctx.getSource().getSender());
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("courseId", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            BasicParkourAPI.get().getCourseRegistry().getCourses()
                                    .forEach(c -> builder.suggest(c.getId()));
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            String courseId = StringArgumentType.getString(ctx, "courseId");
                            cmdPbSingle(ctx.getSource().getSender(), courseId);
                            return Command.SINGLE_SUCCESS;
                        })));

        // reload  (admin)
        root.then(Commands.literal(cfg.getCmdReload())
                .requires(src -> src.getSender().hasPermission(cfg.getPermAdmin()))
                .executes(ctx -> {
                    cmdReload(ctx.getSource().getSender());
                    return Command.SINGLE_SUCCESS;
                }));

        // resetpb <player>  (admin)
        root.then(Commands.literal(cfg.getCmdResetPb())
                .requires(src -> src.getSender().hasPermission(cfg.getPermAdmin()))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            Bukkit.getOnlinePlayers().forEach(p -> builder.suggest(p.getName()));
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            String playerName = StringArgumentType.getString(ctx, "player");
                            cmdResetPb(ctx.getSource().getSender(), playerName);
                            return Command.SINGLE_SUCCESS;
                        })));

        // generate <player> <templateId>  (admin)
        root.then(Commands.literal(cfg.getCmdGenerate())
                .requires(src -> src.getSender().hasPermission(cfg.getPermAdmin()))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            Bukkit.getOnlinePlayers().forEach(p -> builder.suggest(p.getName()));
                            return builder.buildFuture();
                        })
                        .then(Commands.argument("templateId", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    BasicParkourAPI.get().getGeneratorManager().getTemplates()
                                            .forEach(t -> builder.suggest(t.id()));
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    String playerName  = StringArgumentType.getString(ctx, "player");
                                    String templateId  = StringArgumentType.getString(ctx, "templateId");
                                    cmdGenerate(ctx.getSource().getSender(), playerName, templateId);
                                    return Command.SINGLE_SUCCESS;
                                }))));

        // setentry <templateId> [portal|interact]  (admin)
        root.then(Commands.literal(cfg.getCmdSetEntry())
                .requires(src -> src.getSender().hasPermission(cfg.getPermAdmin()))
                .then(Commands.argument("templateId", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            BasicParkourAPI.get().getGeneratorManager().getTemplates()
                                    .forEach(t -> builder.suggest(t.id()));
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            String templateId = StringArgumentType.getString(ctx, "templateId");
                            cmdSetEntry(ctx.getSource().getSender(), templateId, "portal");
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    builder.suggest("portal");
                                    builder.suggest("interact");
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    String templateId = StringArgumentType.getString(ctx, "templateId");
                                    String type = StringArgumentType.getString(ctx, "type");
                                    cmdSetEntry(ctx.getSource().getSender(), templateId, type);
                                    return Command.SINGLE_SUCCESS;
                                }))));

        // removeentry <templateId>  (admin)
        root.then(Commands.literal(cfg.getCmdRemoveEntry())
                .requires(src -> src.getSender().hasPermission(cfg.getPermAdmin()))
                .then(Commands.argument("templateId", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            BasicParkourAPI.get().getGeneratorManager().getTemplates()
                                    .forEach(t -> builder.suggest(t.id()));
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            String templateId = StringArgumentType.getString(ctx, "templateId");
                            cmdRemoveEntry(ctx.getSource().getSender(), templateId);
                            return Command.SINGLE_SUCCESS;
                        })));

        // Register the root node with all configured aliases
        registrar.register(
                root.build(),
                "Basic Parkour plugin commands",
                cfg.getAliases()
        );
    }

    // -----------------------------------------------------------------------
    // Sub-command implementations
    // -----------------------------------------------------------------------

    private void cmdJoin(@NotNull CommandSender sender, @NotNull String courseId) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can join a course."));
            return;
        }
        Optional<ParkourCourse> courseOpt = BasicParkourAPI.get()
                .getCourseRegistry().getCourse(courseId);
        if (courseOpt.isEmpty()) {
            send(player, plugin.getMessages().getNoCourse());
            return;
        }
        if (sessionManager.getSession(player).isPresent()) {
            send(player, plugin.getMessages().getAlreadyInSession());
            return;
        }
        sessionManager.startSession(player, courseOpt.get());
    }

    private void cmdLeave(@NotNull CommandSender sender) {
        if (!(sender instanceof Player player)) return;
        if (sessionManager.getSession(player).isEmpty()) {
            send(player, "<yellow>You are not in a session.</yellow>");
            return;
        }
        sessionManager.removeSession(player);
        send(player, "<gray>You left the course.</gray>");
    }

    private void cmdTop(@NotNull CommandSender sender, @NotNull String courseId, int limit) {
        send(sender, plugin.getMessages().getLeaderboardHeader().replace("{course}", courseId));

        plugin.getRepository().getLeaderboard(courseId, Math.min(limit, 50))
                .thenAccept(board -> {
                    if (board.isEmpty()) {
                        plugin.getScheduler().runGlobal(() ->
                                send(sender, "<gray>No records yet for this course.</gray>"));
                        return;
                    }
                    plugin.getScheduler().runGlobal(() -> {
                        for (LeaderboardEntry entry : board) {
                            String msg = plugin.getMessages().getLeaderboardEntry()
                                    .replace("{rank}", String.valueOf(entry.rank()))
                                    .replace("{player}", entry.playerName())
                                    .replace("{time}", TimerFormatter.format(entry.timeMs()));
                            send(sender, msg);
                        }
                    });
                });
    }

    private void cmdPbAll(@NotNull CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command."));
            return;
        }
        plugin.getRepository().getAllRecords(player.getUniqueId())
                .thenAccept(records -> plugin.getScheduler().runOnEntity(player, () -> {
                    if (records.isEmpty()) {
                        send(player, "<gray>You have no records yet.</gray>");
                        return;
                    }
                    send(player, "<aqua>Your personal bests:</aqua>");
                    records.forEach(r -> send(player,
                            "  <gray>%s: <white>%s</white></gray>"
                                    .formatted(r.courseId(), TimerFormatter.format(r.timeMs()))));
                }));
    }

    private void cmdPbSingle(@NotNull CommandSender sender, @NotNull String courseId) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command."));
            return;
        }
        plugin.getRepository().getPersonalBest(player.getUniqueId(), courseId)
                .thenAccept(opt -> plugin.getScheduler().runOnEntity(player, () -> {
                    if (opt.isPresent()) {
                        send(player, "<aqua>Your PB on <white>" + courseId + "</white>: "
                                + TimerFormatter.format(opt.get().timeMs()) + "</aqua>");
                    } else {
                        send(player, "<gray>No record on course: " + courseId + "</gray>");
                    }
                }));
    }

    private void cmdReload(@NotNull CommandSender sender) {
        plugin.reloadPlugin();
        send(sender, "<green>Basic Parkour configuration reloaded.</green>");
    }

    private void cmdResetPb(@NotNull CommandSender sender, @NotNull String playerName) {
        Player target = Bukkit.getPlayer(playerName);
        UUID uuid = target != null ? target.getUniqueId() : null;
        if (uuid == null) {
            send(sender, "<red>Player not found or never played.</red>");
            return;
        }
        plugin.getRepository().deleteRecords(uuid).thenRun(() ->
                plugin.getScheduler().runGlobal(() ->
                        send(sender, "<green>Reset all records for " + playerName + ".</green>")));
    }

    private void cmdGenerate(@NotNull CommandSender sender,
                              @NotNull String playerName,
                              @NotNull String templateId) {
        Player target = Bukkit.getPlayer(playerName);
        if (target == null) {
            send(sender, "<red>Player '" + playerName + "' is not online.</red>");
            return;
        }
        Optional<GeneratorTemplate> tplOpt = BasicParkourAPI.get()
            .getGeneratorManager().getTemplate(templateId);
        if (tplOpt.isEmpty()) {
            send(sender, "<red>Unknown generator template: " + templateId + "</red>");
            return;
        }
        send(sender, "<gray>Generating run '" + templateId + "' for " + playerName + "…</gray>");
        BasicParkourAPI.get().getGeneratorManager().generate(target, tplOpt.get())
            .thenAccept(session -> plugin.getScheduler().runGlobal(() -> {
                if (session == null) {
                    send(sender, "<red>Failed to generate run for " + playerName + ". Check console for details.</red>");
                } else {
                    send(sender, "<green>Generated run started for " + playerName + ".</green>");
                }
            }));
    }

    private void cmdSetEntry(@NotNull CommandSender sender,
                              @NotNull String templateId,
                              @NotNull String type) {
        if (!(sender instanceof Player player)) {
            send(sender, "<red>Only players can set entry points (must be in-world).</red>");
            return;
        }
        if (BasicParkourAPI.get().getGeneratorManager().getTemplate(templateId).isEmpty()) {
            send(sender, "<red>Unknown generator template: " + templateId + "</red>");
            return;
        }

        Location loc = player.getLocation();
        String worldName = loc.getWorld().getName();
        String typeKey = type.toLowerCase(Locale.ROOT);

        org.bukkit.configuration.ConfigurationSection cfg2 =
            plugin.getConfig().createSection("generator.entry-points." + templateId);
        cfg2.set("type", typeKey);
        cfg2.set("world", worldName);

        if ("portal".equals(typeKey)) {
            // Store a 3×3×3 box centred on the player's feet as the portal region
            cfg2.set("min.x", loc.getBlockX() - 1);
            cfg2.set("min.y", loc.getBlockY());
            cfg2.set("min.z", loc.getBlockZ() - 1);
            cfg2.set("max.x", loc.getBlockX() + 1);
            cfg2.set("max.y", loc.getBlockY() + 2);
            cfg2.set("max.z", loc.getBlockZ() + 1);
        } else {
            // Interact: the block the player is looking at, or their feet block
            Block targetBlock = player.getTargetBlockExact(5);
            Location blockLoc = targetBlock != null ? targetBlock.getLocation() : loc;
            cfg2.set("x", blockLoc.getBlockX());
            cfg2.set("y", blockLoc.getBlockY());
            cfg2.set("z", blockLoc.getBlockZ());
        }
        plugin.saveConfig();

        // Reload entry points in the listener if available
        if (generatorManager != null && entryListener != null) {
            entryListener.reloadEntries();
        }

        send(sender, "<green>Entry point (" + typeKey + ") registered for template '"
            + templateId + "' at your location.</green>");
    }

    private void cmdRemoveEntry(@NotNull CommandSender sender, @NotNull String templateId) {
        plugin.getConfig().set("generator.entry-points." + templateId, null);
        plugin.saveConfig();
        if (generatorManager != null && entryListener != null) {
            entryListener.reloadEntries();
        }
        send(sender, "<green>Entry point for '" + templateId + "' removed.</green>");
    }

    /**
     * Injects the generator manager and entry listener after they are created by the plugin.
     * Called from {@link me.usainsrht.basicparkour.core.BasicParkourPlugin#onEnable()}.
     */
    public void setGeneratorReferences(
        @NotNull GeneratorManagerImpl generatorManager,
        @NotNull GeneratorEntryListener entryListener
    ) {
        this.generatorManager = generatorManager;
        this.entryListener    = entryListener;
    }

    // -----------------------------------------------------------------------
    // Help
    // -----------------------------------------------------------------------

    private void sendHelp(@NotNull CommandSender sender) {
        String r = cfg.getRoot();
        send(sender, "<gold>━━━ <bold>Basic Parkour Commands</bold> ━━━</gold>");
        send(sender, "<gray>/" + r + " " + cfg.getCmdJoin() + " <courseId></gray> — join a course");
        send(sender, "<gray>/" + r + " " + cfg.getCmdLeave() + "</gray> — exit current session");
        send(sender, "<gray>/" + r + " " + cfg.getCmdTop() + " <courseId></gray> — view leaderboard");
        send(sender, "<gray>/" + r + " " + cfg.getCmdPb() + " [courseId]</gray> — your personal bests");
        if (sender.hasPermission(cfg.getPermAdmin())) {
            send(sender, "<gold>━━━ Admin ━━━</gold>");
            send(sender, "<gray>/" + r + " " + cfg.getCmdReload() + "</gray> — reload plugin");
            send(sender, "<gray>/" + r + " " + cfg.getCmdResetPb() + " <player></gray> — reset player records");
            send(sender, "<gray>/" + r + " " + cfg.getCmdGenerate() + " <player> <templateId></gray> — start generated run");
            send(sender, "<gray>/" + r + " " + cfg.getCmdSetEntry() + " <templateId> [portal|interact]</gray> — register entry point");
            send(sender, "<gray>/" + r + " " + cfg.getCmdRemoveEntry() + " <templateId></gray> — remove entry point");
        }
    }

    // -----------------------------------------------------------------------
    // Utility
    // -----------------------------------------------------------------------

    private static void send(@NotNull CommandSender sender, @NotNull String miniMessage) {
        sender.sendMessage(MINI.deserialize(miniMessage));
    }
}
