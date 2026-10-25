package me.usainsrht.basicparkour.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.usainsrht.basicparkour.api.BasicParkourAPI;
import me.usainsrht.basicparkour.api.course.ParkourCourse;
import me.usainsrht.basicparkour.api.generator.GeneratorTemplate;
import me.usainsrht.basicparkour.api.storage.LeaderboardEntry;
import me.usainsrht.basicparkour.api.storage.PlayerRecord;
import me.usainsrht.basicparkour.core.BasicParkourPlugin;
import me.usainsrht.basicparkour.core.config.CourseLoader;
import me.usainsrht.basicparkour.core.config.GeneratorTemplateLoader;
import me.usainsrht.basicparkour.core.editor.EditorManager;
import me.usainsrht.basicparkour.core.generator.GeneratorManagerImpl;
import me.usainsrht.basicparkour.core.gui.CourseSelectorGUI;
import me.usainsrht.basicparkour.core.listener.GeneratorEntryListener;
import me.usainsrht.basicparkour.core.session.SessionManager;
import me.usainsrht.basicparkour.core.util.TimerFormatter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Registers all {@code /basicparkour} (and configured aliases) sub-commands via
 * the Paper Brigadier command API.
 */
@SuppressWarnings({"UnstableApiUsage", "deprecation"})
public final class BasicParkourCommand {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final BasicParkourPlugin plugin;
    private final SessionManager sessionManager;
    private final CommandConfig cfg;
    private GeneratorManagerImpl generatorManager;
    private GeneratorEntryListener entryListener;
    private EditorManager editorManager;
    private me.usainsrht.basicparkour.core.hologram.HologramManager hologramManager;
    private me.usainsrht.basicparkour.core.npc.NpcManager npcManager;

    public BasicParkourCommand(@NotNull BasicParkourPlugin plugin,
                                @NotNull SessionManager sessionManager,
                                @NotNull CommandConfig cfg) {
        this.plugin = plugin;
        this.sessionManager = sessionManager;
        this.cfg = cfg;
    }

    public void setEditorManager(@NotNull EditorManager editorManager) {
        this.editorManager = editorManager;
    }

    public void setHologramManager(me.usainsrht.basicparkour.core.hologram.HologramManager hologramManager) {
        this.hologramManager = hologramManager;
    }

    public void setNpcManager(me.usainsrht.basicparkour.core.npc.NpcManager npcManager) {
        this.npcManager = npcManager;
    }

    // -----------------------------------------------------------------------
    // Registration
    // -----------------------------------------------------------------------

    public void register(@NotNull Commands registrar) {
        LiteralArgumentBuilder<CommandSourceStack> root =
                Commands.literal(cfg.getRoot())
                        .requires(src -> src.getSender().hasPermission(cfg.getPermUse()))
                        .executes(ctx -> {
                            CommandSender sender = ctx.getSource().getSender();
                            if (sender instanceof Player player) {
                                new CourseSelectorGUI(plugin, player).open();
                            } else {
                                sendHelp(sender);
                            }
                            return Command.SINGLE_SUCCESS;
                        });

        // menu
        root.then(Commands.literal(cfg.getCmdMenu())
                .executes(ctx -> {
                    cmdMenu(ctx.getSource().getSender());
                    return Command.SINGLE_SUCCESS;
                }));

        // ghost
        root.then(Commands.literal(cfg.getCmdGhost())
                .executes(ctx -> {
                    cmdGhost(ctx.getSource().getSender());
                    return Command.SINGLE_SUCCESS;
                }));

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

        // restart
        root.then(Commands.literal(cfg.getCmdRestart())
                .executes(ctx -> {
                    cmdRestart(ctx.getSource().getSender());
                    return Command.SINGLE_SUCCESS;
                }));

        // checkpoint (and alias cp)
        root.then(Commands.literal(cfg.getCmdCheckpoint())
                .executes(ctx -> {
                    cmdCheckpoint(ctx.getSource().getSender());
                    return Command.SINGLE_SUCCESS;
                }));
        root.then(Commands.literal("cp")
                .executes(ctx -> {
                    cmdCheckpoint(ctx.getSource().getSender());
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

        // reload (admin)
        root.then(Commands.literal(cfg.getCmdReload())
                .requires(src -> src.getSender().hasPermission(cfg.getPermAdmin()))
                .executes(ctx -> {
                    cmdReload(ctx.getSource().getSender());
                    return Command.SINGLE_SUCCESS;
                }));

        // resetpb <player> (admin)
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

        // generate <player> <templateId> (admin)
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

        // setentry <templateId> [portal|interact] [yaw] (admin)
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
                            cmdSetEntry(ctx.getSource().getSender(), templateId, "portal", null);
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
                                    cmdSetEntry(ctx.getSource().getSender(), templateId, type, null);
                                    return Command.SINGLE_SUCCESS;
                                })
                                .then(Commands.argument("yaw", DoubleArgumentType.doubleArg())
                                        .executes(ctx -> {
                                            String templateId = StringArgumentType.getString(ctx, "templateId");
                                            String type = StringArgumentType.getString(ctx, "type");
                                            double yaw = DoubleArgumentType.getDouble(ctx, "yaw");
                                            cmdSetEntry(ctx.getSource().getSender(), templateId, type, yaw);
                                            return Command.SINGLE_SUCCESS;
                                        })))));

        // removeentry <templateId> (admin)
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

        // setfailloc [templateId] (admin)
        root.then(Commands.literal("setfailloc")
                .requires(src -> src.getSender().hasPermission(cfg.getPermAdmin()))
                .executes(ctx -> {
                    cmdSetFailLoc(ctx.getSource().getSender(), null);
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("templateId", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            BasicParkourAPI.get().getGeneratorManager().getTemplates()
                                    .forEach(t -> builder.suggest(t.id()));
                            builder.suggest("global");
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            String templateId = StringArgumentType.getString(ctx, "templateId");
                            cmdSetFailLoc(ctx.getSource().getSender(), templateId);
                            return Command.SINGLE_SUCCESS;
                        })));

        // setcompleteloc [templateId] (admin)
        root.then(Commands.literal("setcompleteloc")
                .requires(src -> src.getSender().hasPermission(cfg.getPermAdmin()))
                .executes(ctx -> {
                    cmdSetCompleteLoc(ctx.getSource().getSender(), null);
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("templateId", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            BasicParkourAPI.get().getGeneratorManager().getTemplates()
                                    .forEach(t -> builder.suggest(t.id()));
                            builder.suggest("global");
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            String templateId = StringArgumentType.getString(ctx, "templateId");
                            cmdSetCompleteLoc(ctx.getSource().getSender(), templateId);
                            return Command.SINGLE_SUCCESS;
                        })));

        // wand (admin)
        root.then(Commands.literal(cfg.getCmdWand())
                .requires(src -> src.getSender().hasPermission(cfg.getPermAdmin()))
                .executes(ctx -> {
                    cmdWand(ctx.getSource().getSender());
                    return Command.SINGLE_SUCCESS;
                }));

        // pos1 / pos2 (admin) — set selection corners without the wand
        root.then(Commands.literal(cfg.getCmdPos1())
                .requires(src -> src.getSender().hasPermission(cfg.getPermAdmin()))
                .executes(ctx -> {
                    cmdPos(ctx.getSource().getSender(), 1);
                    return Command.SINGLE_SUCCESS;
                }));
        root.then(Commands.literal(cfg.getCmdPos2())
                .requires(src -> src.getSender().hasPermission(cfg.getPermAdmin()))
                .executes(ctx -> {
                    cmdPos(ctx.getSource().getSender(), 2);
                    return Command.SINGLE_SUCCESS;
                }));

        // endless [templateId] — player-facing generative parkour entry
        root.then(Commands.literal(cfg.getCmdEndless())
                .executes(ctx -> {
                    cmdEndless(ctx.getSource().getSender(), null);
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("templateId", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            BasicParkourAPI.get().getGeneratorManager().getTemplates()
                                    .forEach(t -> builder.suggest(t.id()));
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            cmdEndless(ctx.getSource().getSender(),
                                    StringArgumentType.getString(ctx, "templateId"));
                            return Command.SINGLE_SUCCESS;
                        })));

        // generate <player> <templateId> (admin)
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
                                    cmdGenerate(ctx.getSource().getSender(),
                                            StringArgumentType.getString(ctx, "player"),
                                            StringArgumentType.getString(ctx, "templateId"));
                                    return Command.SINGLE_SUCCESS;
                                }))));



        // create <id> <name> (admin)
        root.then(Commands.literal(cfg.getCmdCreate())
                .requires(src -> src.getSender().hasPermission(cfg.getPermAdmin()))
                .then(Commands.argument("id", StringArgumentType.word())
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    String id = StringArgumentType.getString(ctx, "id");
                                    String name = StringArgumentType.getString(ctx, "name");
                                    cmdCreate(ctx.getSource().getSender(), id, name);
                                    return Command.SINGLE_SUCCESS;
                                }))));

        // setstart <id> (admin)
        root.then(Commands.literal(cfg.getCmdSetStart())
                .requires(src -> src.getSender().hasPermission(cfg.getPermAdmin()))
                .then(Commands.argument("id", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            BasicParkourAPI.get().getCourseRegistry().getCourses().forEach(c -> builder.suggest(c.getId()));
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            String id = StringArgumentType.getString(ctx, "id");
                            cmdSetStart(ctx.getSource().getSender(), id);
                            return Command.SINGLE_SUCCESS;
                        })));

        // setend <id> (admin)
        root.then(Commands.literal(cfg.getCmdSetEnd())
                .requires(src -> src.getSender().hasPermission(cfg.getPermAdmin()))
                .then(Commands.argument("id", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            BasicParkourAPI.get().getCourseRegistry().getCourses().forEach(c -> builder.suggest(c.getId()));
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            String id = StringArgumentType.getString(ctx, "id");
                            cmdSetEnd(ctx.getSource().getSender(), id);
                            return Command.SINGLE_SUCCESS;
                        })));

        // setportal <id> (admin)
        root.then(Commands.literal(cfg.getCmdSetPortal())
                .requires(src -> src.getSender().hasPermission(cfg.getPermAdmin()))
                .then(Commands.argument("id", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            BasicParkourAPI.get().getCourseRegistry().getCourses().forEach(c -> builder.suggest(c.getId()));
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            String id = StringArgumentType.getString(ctx, "id");
                            cmdSetPortal(ctx.getSource().getSender(), id);
                            return Command.SINGLE_SUCCESS;
                        })));

        // addcp <id> (admin)
        root.then(Commands.literal(cfg.getCmdAddCp())
                .requires(src -> src.getSender().hasPermission(cfg.getPermAdmin()))
                .then(Commands.argument("id", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            BasicParkourAPI.get().getCourseRegistry().getCourses().forEach(c -> builder.suggest(c.getId()));
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            String id = StringArgumentType.getString(ctx, "id");
                            cmdAddCp(ctx.getSource().getSender(), id);
                            return Command.SINGLE_SUCCESS;
                        })));

        // save <id> (admin)
        root.then(Commands.literal(cfg.getCmdSave())
                .requires(src -> src.getSender().hasPermission(cfg.getPermAdmin()))
                .then(Commands.argument("id", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            BasicParkourAPI.get().getCourseRegistry().getCourses().forEach(c -> builder.suggest(c.getId()));
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            String id = StringArgumentType.getString(ctx, "id");
                            cmdSave(ctx.getSource().getSender(), id);
                            return Command.SINGLE_SUCCESS;
                        })));

        // hologram (admin)
        root.then(Commands.literal("hologram")
                .requires(src -> src.getSender().hasPermission(cfg.getPermAdmin()))
                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            builder.suggest("leaderboard");
                                            builder.suggest("course_info");
                                            return builder.buildFuture();
                                        })
                                        .then(Commands.argument("courseId", StringArgumentType.word())
                                                .suggests((ctx, builder) -> {
                                                    BasicParkourAPI.get().getCourseRegistry().getCourses().forEach(c -> builder.suggest(c.getId()));
                                                    BasicParkourAPI.get().getGeneratorManager().getTemplates().forEach(t -> builder.suggest(t.leaderboardCourseId()));
                                                    return builder.buildFuture();
                                                })
                                                .executes(ctx -> {
                                                    cmdHologramCreate(ctx.getSource().getSender(),
                                                            StringArgumentType.getString(ctx, "name"),
                                                            StringArgumentType.getString(ctx, "type"),
                                                            StringArgumentType.getString(ctx, "courseId"),
                                                            10);
                                                    return Command.SINGLE_SUCCESS;
                                                })
                                                .then(Commands.argument("limit", IntegerArgumentType.integer(1, 50))
                                                        .executes(ctx -> {
                                                            cmdHologramCreate(ctx.getSource().getSender(),
                                                                    StringArgumentType.getString(ctx, "name"),
                                                                    StringArgumentType.getString(ctx, "type"),
                                                                    StringArgumentType.getString(ctx, "courseId"),
                                                                    IntegerArgumentType.getInteger(ctx, "limit"));
                                                            return Command.SINGLE_SUCCESS;
                                                        }))))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    if (hologramManager != null) {
                                        hologramManager.getHolograms().forEach(h -> builder.suggest(h.getName()));
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    cmdHologramRemove(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "name"));
                                    return Command.SINGLE_SUCCESS;
                                })))
                .then(Commands.literal("refresh")
                        .executes(ctx -> {
                            cmdHologramRefresh(ctx.getSource().getSender(), null);
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    if (hologramManager != null) {
                                        hologramManager.getHolograms().forEach(h -> builder.suggest(h.getName()));
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    cmdHologramRefresh(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "name"));
                                    return Command.SINGLE_SUCCESS;
                                })))
                .then(Commands.literal("list")
                        .executes(ctx -> {
                            cmdHologramList(ctx.getSource().getSender());
                            return Command.SINGLE_SUCCESS;
                        })));

        // npc (admin)
        root.then(Commands.literal("npc")
                .requires(src -> src.getSender().hasPermission(cfg.getPermAdmin()))
                .then(Commands.literal("link")
                        .then(Commands.argument("platform", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    builder.suggest("citizens");
                                    builder.suggest("fancynpcs");
                                    return builder.buildFuture();
                                })
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .then(Commands.argument("action", StringArgumentType.word())
                                                .suggests((ctx, builder) -> {
                                                    for (var a : me.usainsrht.basicparkour.core.npc.NpcAction.values()) {
                                                        builder.suggest(a.name().toLowerCase(Locale.ROOT));
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .executes(ctx -> {
                                                    cmdNpcLink(ctx.getSource().getSender(),
                                                            StringArgumentType.getString(ctx, "platform"),
                                                            StringArgumentType.getString(ctx, "id"),
                                                            StringArgumentType.getString(ctx, "action"),
                                                            "", 1);
                                                    return Command.SINGLE_SUCCESS;
                                                })
                                                .then(Commands.argument("courseId", StringArgumentType.word())
                                                        .suggests((ctx, builder) -> {
                                                            BasicParkourAPI.get().getCourseRegistry().getCourses().forEach(c -> builder.suggest(c.getId()));
                                                            return builder.buildFuture();
                                                        })
                                                        .executes(ctx -> {
                                                            cmdNpcLink(ctx.getSource().getSender(),
                                                                    StringArgumentType.getString(ctx, "platform"),
                                                                    StringArgumentType.getString(ctx, "id"),
                                                                    StringArgumentType.getString(ctx, "action"),
                                                                    StringArgumentType.getString(ctx, "courseId"), 1);
                                                            return Command.SINGLE_SUCCESS;
                                                        })
                                                        .then(Commands.argument("rank", IntegerArgumentType.integer(1, 10))
                                                                .executes(ctx -> {
                                                                    cmdNpcLink(ctx.getSource().getSender(),
                                                                            StringArgumentType.getString(ctx, "platform"),
                                                                            StringArgumentType.getString(ctx, "id"),
                                                                            StringArgumentType.getString(ctx, "action"),
                                                                            StringArgumentType.getString(ctx, "courseId"),
                                                                            IntegerArgumentType.getInteger(ctx, "rank"));
                                                                    return Command.SINGLE_SUCCESS;
                                                                })))))))
                .then(Commands.literal("unlink")
                        .then(Commands.argument("platform", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    builder.suggest("citizens");
                                    builder.suggest("fancynpcs");
                                    return builder.buildFuture();
                                })
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .executes(ctx -> {
                                            cmdNpcUnlink(ctx.getSource().getSender(),
                                                    StringArgumentType.getString(ctx, "platform"),
                                                    StringArgumentType.getString(ctx, "id"));
                                            return Command.SINGLE_SUCCESS;
                                        }))))
                .then(Commands.literal("topholder")
                        .then(Commands.argument("platform", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    builder.suggest("citizens");
                                    builder.suggest("fancynpcs");
                                    return builder.buildFuture();
                                })
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .then(Commands.argument("courseId", StringArgumentType.word())
                                                .suggests((ctx, builder) -> {
                                                    BasicParkourAPI.get().getCourseRegistry().getCourses().forEach(c -> builder.suggest(c.getId()));
                                                    return builder.buildFuture();
                                                })
                                                .executes(ctx -> {
                                                    cmdNpcLink(ctx.getSource().getSender(),
                                                            StringArgumentType.getString(ctx, "platform"),
                                                            StringArgumentType.getString(ctx, "id"),
                                                            "TOP_HOLDER",
                                                            StringArgumentType.getString(ctx, "courseId"), 1);
                                                    return Command.SINGLE_SUCCESS;
                                                })))))
                .then(Commands.literal("list")
                        .executes(ctx -> {
                            cmdNpcList(ctx.getSource().getSender());
                            return Command.SINGLE_SUCCESS;
                        })));

        // Register root node
        registrar.register(
                root.build(),
                "Basic Parkour plugin commands",
                cfg.getAliases()
        );
    }

    // -----------------------------------------------------------------------
    // Sub-command implementations
    // -----------------------------------------------------------------------

    private void cmdMenu(@NotNull CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can open the menu."));
            return;
        }
        new CourseSelectorGUI(plugin, player).open();
    }

    private void cmdGhost(@NotNull CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can toggle ghost playback."));
            return;
        }
        boolean nowEnabled = plugin.getGhostRecorder().togglePlayerGhost(player.getUniqueId());
        String stateStr = nowEnabled ? "<green>Enabled</green>" : "<red>Disabled</red>";
        plugin.getMessages().getGhostToggled().send((net.kyori.adventure.audience.Audience) player, Placeholder.parsed("state", stateStr));
    }

    private void cmdJoin(@NotNull CommandSender sender, @NotNull String courseId) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can join a course."));
            return;
        }
        Optional<ParkourCourse> courseOpt = BasicParkourAPI.get()
                .getCourseRegistry().getCourse(courseId);
        if (courseOpt.isEmpty()) {
            plugin.getMessages().getNoCourse().send((net.kyori.adventure.audience.Audience) player);
            return;
        }
        if (sessionManager.getSession(player).isPresent()) {
            plugin.getMessages().getAlreadyInSession().send((net.kyori.adventure.audience.Audience) player);
            return;
        }
        sessionManager.startSession(player, courseOpt.get());
    }

    private void cmdLeave(@NotNull CommandSender sender) {
        if (!(sender instanceof Player player)) return;
        sessionManager.leaveSession(player);
    }

    private void cmdRestart(@NotNull CommandSender sender) {
        if (!(sender instanceof Player player)) return;
        sessionManager.restartSession(player);
    }

    private void cmdCheckpoint(@NotNull CommandSender sender) {
        if (!(sender instanceof Player player)) return;
        sessionManager.teleportToCheckpoint(player);
    }

    private void cmdTop(@NotNull CommandSender sender, @NotNull String courseId, int limit) {
        plugin.getMessages().getLeaderboardHeader().send(sender, Placeholder.parsed("course", courseId));

        plugin.getRepository().getLeaderboard(courseId, Math.min(limit, 50))
                .thenAccept(board -> {
                    if (board.isEmpty()) {
                        plugin.getScheduler().runGlobal(() ->
                                send(sender, "<gray>No records yet for this course.</gray>"));
                        return;
                    }
                    plugin.getScheduler().runGlobal(() -> {
                        for (LeaderboardEntry entry : board) {
                            if (entry.blocks() > 0) {
                                plugin.getMessages().getLeaderboardEntryEndless().send(sender,
                                        Placeholder.parsed("rank", String.valueOf(entry.rank())),
                                        Placeholder.parsed("player", entry.playerName()),
                                        Placeholder.parsed("blocks", String.valueOf(entry.blocks())),
                                        Placeholder.parsed("time", TimerFormatter.format(entry.timeMs())));
                            } else {
                                plugin.getMessages().getLeaderboardEntry().send(sender,
                                        Placeholder.parsed("rank", String.valueOf(entry.rank())),
                                        Placeholder.parsed("player", entry.playerName()),
                                        Placeholder.parsed("time", TimerFormatter.format(entry.timeMs())));
                            }
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
                    records.forEach(r -> {
                        if (r.blocks() > 0) {
                            send(player, "  <gray>%s: <green>%d blocks</green> <dark_gray>(<white>%s</white>)</dark_gray></gray>"
                                    .formatted(r.courseId(), r.blocks(), TimerFormatter.format(r.timeMs())));
                        } else {
                            send(player, "  <gray>%s: <white>%s</white></gray>"
                                    .formatted(r.courseId(), TimerFormatter.format(r.timeMs())));
                        }
                    });
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
                        PlayerRecord rec = opt.get();
                        if (rec.blocks() > 0) {
                            send(player, "<aqua>Your PB on <white>" + courseId + "</white>: <green>"
                                    + rec.blocks() + " blocks</green> <dark_gray>(" + TimerFormatter.format(rec.timeMs()) + ")</dark_gray></aqua>");
                        } else {
                            send(player, "<aqua>Your PB on <white>" + courseId + "</white>: "
                                    + TimerFormatter.format(rec.timeMs()) + "</aqua>");
                        }
                    } else {
                        send(player, "<gray>No record on course: " + courseId + "</gray>");
                    }
                }));
    }

    private void cmdReload(@NotNull CommandSender sender) {
        send(sender, "<gray>Reloading Basic Parkour...</gray>");
        plugin.reloadPluginAsync().thenRun(() -> {
            plugin.getScheduler().runGlobal(() -> {
                plugin.getMessages().getReloadSuccess().send(sender);
            });
        }).exceptionally(ex -> {
            plugin.getScheduler().runGlobal(() -> {
                send(sender, "<red>An error occurred while reloading: " + ex.getMessage() + "</red>");
            });
            return null;
        });
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
                        plugin.getMessages().getResetPbSuccess().send(sender, Placeholder.parsed("player", playerName))));
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
        cmdSetEntry(sender, templateId, type, null);
    }

    private void cmdSetEntry(@NotNull CommandSender sender,
                              @NotNull String templateId,
                              @NotNull String type,
                              @org.jetbrains.annotations.Nullable Double yaw) {
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
            Location p1 = editorManager != null ? editorManager.getPos1(player) : null;
            Location p2 = editorManager != null ? editorManager.getPos2(player) : null;
            if (p1 != null && p2 != null && p1.getWorld().equals(p2.getWorld())) {
                cfg2.set("min.x", (double) Math.min(p1.getBlockX(), p2.getBlockX()));
                cfg2.set("min.y", (double) Math.min(p1.getBlockY(), p2.getBlockY()));
                cfg2.set("min.z", (double) Math.min(p1.getBlockZ(), p2.getBlockZ()));
                cfg2.set("max.x", (double) Math.max(p1.getBlockX(), p2.getBlockX()) + 1.0);
                cfg2.set("max.y", (double) Math.max(p1.getBlockY(), p2.getBlockY()) + 1.0);
                cfg2.set("max.z", (double) Math.max(p1.getBlockZ(), p2.getBlockZ()) + 1.0);
            } else {
                cfg2.set("min.x", (double) (loc.getBlockX() - 1));
                cfg2.set("min.y", (double) loc.getBlockY());
                cfg2.set("min.z", (double) (loc.getBlockZ() - 1));
                cfg2.set("max.x", (double) (loc.getBlockX() + 2));
                cfg2.set("max.y", (double) (loc.getBlockY() + 3));
                cfg2.set("max.z", (double) (loc.getBlockZ() + 2));
            }
        } else {
            Block targetBlock = player.getTargetBlockExact(5);
            Location blockLoc = targetBlock != null ? targetBlock.getLocation() : loc;
            cfg2.set("x", blockLoc.getBlockX());
            cfg2.set("y", blockLoc.getBlockY());
            cfg2.set("z", blockLoc.getBlockZ());
        }
        if (yaw != null) {
            cfg2.set("yaw", yaw);
        }
        plugin.saveConfig();

        if (generatorManager != null && entryListener != null) {
            entryListener.reloadEntries();
        }

        send(sender, "<green>Entry point (" + typeKey + ") registered for template '"
            + templateId + "'" + (yaw != null ? " with fixed direction (yaw: " + yaw + ")" : "") + ".</green>");
    }

    private void cmdRemoveEntry(@NotNull CommandSender sender, @NotNull String templateId) {
        plugin.getConfig().set("generator.entry-points." + templateId, null);
        plugin.saveConfig();
        if (generatorManager != null && entryListener != null) {
            entryListener.reloadEntries();
        }
        send(sender, "<green>Entry point for '" + templateId + "' removed.</green>");
    }

    private void cmdSetFailLoc(@NotNull CommandSender sender, @Nullable String templateId) {
        if (!(sender instanceof Player player)) {
            send(sender, "<red>Only players can set fail locations.</red>");
            return;
        }
        Location loc = player.getLocation();
        String locStr = loc.getWorld().getName() + ","
            + String.format(Locale.ROOT, "%.2f,%.2f,%.2f,%.1f,%.1f",
                loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());

        if (templateId == null || templateId.isBlank() || templateId.equalsIgnoreCase("global")) {
            plugin.getConfig().set("session.fail-location", locStr);
            plugin.saveConfig();
            send(sender, "<green>Global fail location set to: <white>" + locStr + "</white></green>");
            return;
        }

        File genFile = new File(plugin.getDataFolder(), "generators/" + templateId + ".yml");
        if (genFile.exists()) {
            try {
                org.bukkit.configuration.file.YamlConfiguration yml =
                    org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(genFile);
                yml.set("fail-location", locStr);
                yml.save(genFile);

                GeneratorTemplateLoader templateLoader = new GeneratorTemplateLoader(plugin);
                GeneratorTemplate reloaded = templateLoader.load(genFile);
                if (reloaded != null) {
                    plugin.getBasicParkourAPI().getGeneratorManager().registerTemplate(reloaded);
                }
                send(sender, "<green>Fail location for template '<yellow>" + templateId + "</yellow>' set to: <white>" + locStr + "</white></green>");
            } catch (Exception ex) {
                send(sender, "<red>Failed to save template file: " + ex.getMessage() + "</red>");
            }
            return;
        }

        File courseFile = new File(plugin.getDataFolder(), "courses/" + templateId + ".yml");
        if (courseFile.exists()) {
            try {
                org.bukkit.configuration.file.YamlConfiguration yml =
                    org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(courseFile);
                yml.set("fail-location", locStr);
                yml.save(courseFile);

                CourseLoader courseLoader = new CourseLoader(plugin);
                courseLoader.load(courseFile).ifPresent(c -> {
                    plugin.getBasicParkourAPI().getCourseRegistry().unregisterCourse(c.getId());
                    plugin.getBasicParkourAPI().getCourseRegistry().registerCourse(c);
                });
                send(sender, "<green>Fail location for course '<yellow>" + templateId + "</yellow>' set to: <white>" + locStr + "</white></green>");
            } catch (Exception ex) {
                send(sender, "<red>Failed to save course file: " + ex.getMessage() + "</red>");
            }
            return;
        }

        send(sender, "<red>Template or course file not found: " + templateId + "</red>");
    }

    private void cmdSetCompleteLoc(@NotNull CommandSender sender, @Nullable String templateId) {
        if (!(sender instanceof Player player)) {
            send(sender, "<red>Only players can set complete locations.</red>");
            return;
        }
        Location loc = player.getLocation();
        String locStr = loc.getWorld().getName() + ","
            + String.format(Locale.ROOT, "%.2f,%.2f,%.2f,%.1f,%.1f",
                loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());

        if (templateId == null || templateId.isBlank() || templateId.equalsIgnoreCase("global")) {
            plugin.getConfig().set("session.complete-location", locStr);
            plugin.saveConfig();
            send(sender, "<green>Global complete location set to: <white>" + locStr + "</white></green>");
            return;
        }

        File genFile = new File(plugin.getDataFolder(), "generators/" + templateId + ".yml");
        if (genFile.exists()) {
            try {
                org.bukkit.configuration.file.YamlConfiguration yml =
                    org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(genFile);
                yml.set("complete-location", locStr);
                yml.save(genFile);

                GeneratorTemplateLoader templateLoader = new GeneratorTemplateLoader(plugin);
                GeneratorTemplate reloaded = templateLoader.load(genFile);
                if (reloaded != null) {
                    plugin.getBasicParkourAPI().getGeneratorManager().registerTemplate(reloaded);
                }
                send(sender, "<green>Complete location for template '<yellow>" + templateId + "</yellow>' set to: <white>" + locStr + "</white></green>");
            } catch (Exception ex) {
                send(sender, "<red>Failed to save template file: " + ex.getMessage() + "</red>");
            }
            return;
        }

        File courseFile = new File(plugin.getDataFolder(), "courses/" + templateId + ".yml");
        if (courseFile.exists()) {
            try {
                org.bukkit.configuration.file.YamlConfiguration yml =
                    org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(courseFile);
                yml.set("complete-location", locStr);
                yml.save(courseFile);

                CourseLoader courseLoader = new CourseLoader(plugin);
                courseLoader.load(courseFile).ifPresent(c -> {
                    plugin.getBasicParkourAPI().getCourseRegistry().unregisterCourse(c.getId());
                    plugin.getBasicParkourAPI().getCourseRegistry().registerCourse(c);
                });
                send(sender, "<green>Complete location for course '<yellow>" + templateId + "</yellow>' set to: <white>" + locStr + "</white></green>");
            } catch (Exception ex) {
                send(sender, "<red>Failed to save course file: " + ex.getMessage() + "</red>");
            }
            return;
        }

        send(sender, "<red>Template or course file not found: " + templateId + "</red>");
    }

    // ── In-game Editor Commands ──────────────────────────────────────────────

    private void cmdWand(@NotNull CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can receive the wand."));
            return;
        }
        if (editorManager == null) {
            send(player, "<red>Editor is not ready.</red>");
            return;
        }
        player.getInventory().addItem(editorManager.createWandItem());
        plugin.getMessages().getWandGiven().send((net.kyori.adventure.audience.Audience) player);
    }

    private void cmdPos(@NotNull CommandSender sender, int which) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can set selection positions."));
            return;
        }
        if (editorManager == null) {
            send(player, "<red>Editor is not ready.</red>");
            return;
        }
        Location loc = player.getLocation().getBlock().getLocation();
        if (which == 1) editorManager.setPos1(player, loc); else editorManager.setPos2(player, loc);
        send(player, "<green>Position " + which + " set to <white>(" + loc.getBlockX() + ", "
                + loc.getBlockY() + ", " + loc.getBlockZ() + ")</white> in <aqua>"
                + loc.getWorld().getName() + "</aqua>.</green>");
    }

    private void cmdEndless(@NotNull CommandSender sender, @org.jetbrains.annotations.Nullable String templateId) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can start a generated run."));
            return;
        }
        if (sessionManager.getSession(player).isPresent()) {
            plugin.getMessages().getAlreadyInSession().send((net.kyori.adventure.audience.Audience) player);
            return;
        }
        var gm = BasicParkourAPI.get().getGeneratorManager();
        if (templateId == null) {
            var templates = gm.getTemplates();
            if (templates.isEmpty()) {
                send(player, "<red>No generator templates are available.</red>");
                return;
            }
            if (templates.size() > 1) {
                new me.usainsrht.basicparkour.core.gui.GeneratorSelectorGUI(plugin, player).open();
                return;
            }
            templateId = templates.iterator().next().id();
        }
        Optional<GeneratorTemplate> tplOpt = gm.getTemplate(templateId);
        if (tplOpt.isEmpty()) {
            send(player, "<red>Unknown generator template: " + templateId + "</red>");
            return;
        }
        gm.generate(player, tplOpt.get());
    }

    private void cmdCreate(@NotNull CommandSender sender, @NotNull String id, @NotNull String name) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can create courses."));
            return;
        }
        if (editorManager == null) return;
        EditorManager.DraftCourse draft = editorManager.getOrCreateDraft(id, player.getWorld());
        draft.name = name;
        plugin.getMessages().getCourseCreated().send((net.kyori.adventure.audience.Audience) player, Placeholder.parsed("course", id));
    }

    private void cmdSetStart(@NotNull CommandSender sender, @NotNull String id) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can edit courses."));
            return;
        }
        if (editorManager == null) return;
        BoundingBox box = editorManager.getSelectionBox(player);
        if (box == null) {
            send(player, "<red>Please make a wand selection (Pos 1 & Pos 2) first!</red>");
            return;
        }
        EditorManager.DraftCourse draft = editorManager.getOrCreateDraft(id, player.getWorld());
        draft.startRegion = box;
        draft.startLocations.clear();
        draft.startLocations.add(player.getLocation());
        plugin.getMessages().getStartSet().send((net.kyori.adventure.audience.Audience) player, Placeholder.parsed("course", id));
    }

    private void cmdSetEnd(@NotNull CommandSender sender, @NotNull String id) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can edit courses."));
            return;
        }
        if (editorManager == null) return;
        BoundingBox box = editorManager.getSelectionBox(player);
        if (box == null) {
            send(player, "<red>Please make a wand selection (Pos 1 & Pos 2) first!</red>");
            return;
        }
        EditorManager.DraftCourse draft = editorManager.getOrCreateDraft(id, player.getWorld());
        draft.endRegion = box;
        plugin.getMessages().getEndSet().send((net.kyori.adventure.audience.Audience) player, Placeholder.parsed("course", id));
    }

    private void cmdSetPortal(@NotNull CommandSender sender, @NotNull String id) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can edit courses."));
            return;
        }
        if (editorManager == null) return;
        BoundingBox box = editorManager.getSelectionBox(player);
        if (box == null) {
            send(player, "<red>Please make a wand selection (Pos 1 & Pos 2) first!</red>");
            return;
        }
        EditorManager.DraftCourse draft = editorManager.getOrCreateDraft(id, player.getWorld());
        draft.portalRegion = box;
        plugin.getMessages().getPortalSet().send((net.kyori.adventure.audience.Audience) player, Placeholder.parsed("course", id));
    }

    private void cmdAddCp(@NotNull CommandSender sender, @NotNull String id) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can edit courses."));
            return;
        }
        if (editorManager == null) return;
        BoundingBox box = editorManager.getSelectionBox(player);
        if (box == null) {
            send(player, "<red>Please make a wand selection (Pos 1 & Pos 2) first!</red>");
            return;
        }
        EditorManager.DraftCourse draft = editorManager.getOrCreateDraft(id, player.getWorld());
        draft.checkpoints.add(new EditorManager.DraftCheckpoint(box, player.getLocation()));
        plugin.getMessages().getCheckpointAdded().send((net.kyori.adventure.audience.Audience) player,
                Placeholder.parsed("index", String.valueOf(draft.checkpoints.size())),
                Placeholder.parsed("course", id));
    }

    private void cmdSave(@NotNull CommandSender sender, @NotNull String id) {
        if (editorManager == null) return;
        try {
            boolean success = editorManager.saveCourse(id);
            if (success) {
                plugin.getMessages().getCourseSaved().send(sender, Placeholder.parsed("course", id));
            } else {
                send(sender, "<red>Failed to save course '" + id + "'. Make sure start and end regions are defined.</red>");
            }
        } catch (Exception e) {
            send(sender, "<red>Error saving course: " + e.getMessage() + "</red>");
        }
    }

    public void setGeneratorReferences(
        @NotNull GeneratorManagerImpl generatorManager,
        @NotNull GeneratorEntryListener entryListener
    ) {
        this.generatorManager = generatorManager;
        this.entryListener    = entryListener;
    }

    // -----------------------------------------------------------------------
    // Hologram & NPC sub-commands
    // -----------------------------------------------------------------------

    private void cmdHologramCreate(@NotNull CommandSender sender,
                                   @NotNull String name,
                                   @NotNull String typeStr,
                                   @NotNull String courseId,
                                   int limit) {
        if (!(sender instanceof Player player)) {
            send(sender, "<red>Only in-game players can create holograms at their location.</red>");
            return;
        }
        if (hologramManager == null || !hologramManager.isEnabled()) {
            send(sender, "<red>No supported hologram plugin active! Please install FancyHolograms or DecentHolograms.</red>");
            return;
        }

        me.usainsrht.basicparkour.core.hologram.HologramType type;
        try {
            type = me.usainsrht.basicparkour.core.hologram.HologramType.valueOf(typeStr.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            send(sender, "<red>Invalid hologram type. Use 'leaderboard' or 'course_info'.</red>");
            return;
        }

        boolean ok = hologramManager.createHologram(name, type, courseId, player.getLocation(), limit);
        if (ok) {
            send(sender, "<green>Hologram '<white>" + name + "</white>' (" + type.name() + ") created for course '<white>" + courseId + "</white>'!</green>");
        } else {
            send(sender, "<red>Failed to create hologram.</red>");
        }
    }

    private void cmdHologramRemove(@NotNull CommandSender sender, @NotNull String name) {
        if (hologramManager == null) return;
        boolean ok = hologramManager.deleteHologram(name);
        if (ok) {
            send(sender, "<green>Hologram '<white>" + name + "</white>' removed successfully.</green>");
        } else {
            send(sender, "<red>Hologram '<white>" + name + "</white>' not found.</red>");
        }
    }

    private void cmdHologramRefresh(@NotNull CommandSender sender, String name) {
        if (hologramManager == null) return;
        if (name == null) {
            hologramManager.refreshAll();
            send(sender, "<green>All holograms refreshed.</green>");
        } else {
            hologramManager.refreshHologram(name);
            send(sender, "<green>Hologram '<white>" + name + "</white>' refreshed.</green>");
        }
    }

    private void cmdHologramList(@NotNull CommandSender sender) {
        if (hologramManager == null) return;
        var list = hologramManager.getHolograms();
        send(sender, "<gold>━━━ Registered Holograms (" + list.size() + ") ━━━</gold>");
        if (list.isEmpty()) {
            send(sender, "<gray>No holograms configured yet.</gray>");
            return;
        }
        for (var holo : list) {
            send(sender, "<gray>• <yellow>" + holo.getName() + "</yellow> <dark_gray>[" + holo.getType().name() + "]</dark_gray> <gray>Course: <white>" + holo.getCourseId() + "</white></gray>");
        }
    }

    private void cmdNpcLink(@NotNull CommandSender sender,
                            @NotNull String platformStr,
                            @NotNull String id,
                            @NotNull String actionStr,
                            @NotNull String courseId,
                            int rank) {
        if (npcManager == null) return;

        me.usainsrht.basicparkour.core.npc.NpcPlatform platform;
        try {
            platform = me.usainsrht.basicparkour.core.npc.NpcPlatform.valueOf(platformStr.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            send(sender, "<red>Invalid platform! Choose 'citizens' or 'fancynpcs'.</red>");
            return;
        }

        me.usainsrht.basicparkour.core.npc.NpcAction action;
        try {
            action = me.usainsrht.basicparkour.core.npc.NpcAction.valueOf(actionStr.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            send(sender, "<red>Invalid action! Choose join_course, open_menu, open_generator, leave, show_pb, show_top, or topholder.</red>");
            return;
        }

        npcManager.linkNpc(platform, id, action, courseId, rank);
        send(sender, "<green>Linked <yellow>" + platform.name() + "</yellow> NPC '<white>" + id + "</white>' with action <aqua>" + action.name() + "</aqua>" + (courseId.isEmpty() ? "" : " on course '<white>" + courseId + "</white>'") + "!</green>");
    }

    private void cmdNpcUnlink(@NotNull CommandSender sender,
                              @NotNull String platformStr,
                              @NotNull String id) {
        if (npcManager == null) return;
        try {
            var platform = me.usainsrht.basicparkour.core.npc.NpcPlatform.valueOf(platformStr.toUpperCase(Locale.ROOT));
            boolean ok = npcManager.unlinkNpc(platform, id);
            if (ok) {
                send(sender, "<green>Unlinked <yellow>" + platform.name() + "</yellow> NPC '<white>" + id + "</white>'.</green>");
            } else {
                send(sender, "<red>No linked NPC found for ID '<white>" + id + "</white>'.</red>");
            }
        } catch (IllegalArgumentException e) {
            send(sender, "<red>Invalid platform! Choose 'citizens' or 'fancynpcs'.</red>");
        }
    }

    private void cmdNpcList(@NotNull CommandSender sender) {
        if (npcManager == null) return;
        var list = npcManager.getNpcs();
        send(sender, "<gold>━━━ Linked NPCs (" + list.size() + ") ━━━</gold>");
        if (list.isEmpty()) {
            send(sender, "<gray>No NPCs linked yet.</gray>");
            return;
        }
        for (var npc : list) {
            send(sender, "<gray>• <yellow>" + npc.getPlatform().name() + "</yellow> ID: <white>" + npc.getId() + "</white> <gray>Action: <aqua>" + npc.getAction().name() + "</aqua> Course: <white>" + npc.getCourseId() + "</white></gray>");
        }
    }

    // -----------------------------------------------------------------------
    // Help
    // -----------------------------------------------------------------------

    private void sendHelp(@NotNull CommandSender sender) {
        String r = cfg.getRoot();
        send(sender, "<gold>━━━ <bold>Basic Parkour Commands</bold> ━━━</gold>");
        send(sender, "<gray>/" + r + " " + cfg.getCmdMenu() + "</gray> — open course selector menu");
        send(sender, "<gray>/" + r + " " + cfg.getCmdGhost() + "</gray> — toggle ghost run display");
        send(sender, "<gray>/" + r + " " + cfg.getCmdJoin() + " <courseId></gray> — join a course");
        send(sender, "<gray>/" + r + " " + cfg.getCmdLeave() + "</gray> — exit current session");
        send(sender, "<gray>/" + r + " " + cfg.getCmdRestart() + "</gray> — restart active course");
        send(sender, "<gray>/" + r + " " + cfg.getCmdCheckpoint() + "</gray> — teleport to last checkpoint");
        send(sender, "<gray>/" + r + " " + cfg.getCmdEndless() + " [templateId]</gray> — start a generated (endless) run");
        send(sender, "<gray>/" + r + " " + cfg.getCmdTop() + " <courseId></gray> — view leaderboard");
        send(sender, "<gray>/" + r + " " + cfg.getCmdPb() + " [courseId]</gray> — your personal bests");
        if (sender.hasPermission(cfg.getPermAdmin())) {
            send(sender, "<gold>━━━ Admin / Editor ━━━</gold>");
            send(sender, "<gray>/" + r + " " + cfg.getCmdWand() + "</gray> — get selection wand");
            send(sender, "<gray>/" + r + " " + cfg.getCmdPos1() + " / " + cfg.getCmdPos2() + "</gray> — set selection corners at your feet");
            send(sender, "<gray>/" + r + " " + cfg.getCmdCreate() + " <id> <name></gray> — create draft course");
            send(sender, "<gray>/" + r + " " + cfg.getCmdSetStart() + " <id></gray> — set start region & spawn");
            send(sender, "<gray>/" + r + " " + cfg.getCmdSetEnd() + " <id></gray> — set end region");
            send(sender, "<gray>/" + r + " " + cfg.getCmdSetPortal() + " <id></gray> — set portal region");
            send(sender, "<gray>/" + r + " " + cfg.getCmdAddCp() + " <id></gray> — add checkpoint");
            send(sender, "<gray>/" + r + " " + cfg.getCmdSave() + " <id></gray> — save course to file");
            send(sender, "<gray>/" + r + " " + cfg.getCmdReload() + "</gray> — reload plugin");
            send(sender, "<gray>/" + r + " " + cfg.getCmdResetPb() + " <player></gray> — reset player records");
            send(sender, "<gray>/" + r + " " + cfg.getCmdGenerate() + " <player> <templateId></gray> — start generated run");
            send(sender, "<gray>/" + r + " " + cfg.getCmdSetEntry() + " <templateId> [portal|interact]</gray> — register entry point");
            send(sender, "<gray>/" + r + " " + cfg.getCmdRemoveEntry() + " <templateId></gray> — remove entry point");
            send(sender, "<gray>/" + r + " setfailloc [templateId|global]</gray> — set fail/exit teleport location");
            send(sender, "<gray>/" + r + " setcompleteloc [templateId|global]</gray> — set complete teleport location");
            send(sender, "<gray>/" + r + " hologram <create|remove|refresh|list></gray> — manage holograms");
            send(sender, "<gray>/" + r + " npc <link|unlink|topholder|list></gray> — manage NPCs");
        }
    }

    // -----------------------------------------------------------------------
    // Utility
    // -----------------------------------------------------------------------

    private static void send(@NotNull CommandSender sender, @NotNull String miniMessage) {
        sender.sendMessage(MINI.deserialize(miniMessage));
    }
}
