package me.usainsrht.basicparkour.core;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.usainsrht.basicparkour.api.BasicParkourAPI;
import me.usainsrht.basicparkour.api.ghost.GhostRecorder;
import me.usainsrht.basicparkour.api.storage.ParkourRepository;
import me.usainsrht.basicparkour.core.api.BasicParkourAPIImpl;
import me.usainsrht.basicparkour.core.command.BasicParkourCommand;
import me.usainsrht.basicparkour.core.command.CommandConfig;
import me.usainsrht.basicparkour.core.config.CourseLoader;
import me.usainsrht.basicparkour.core.config.GeneratorTemplateLoader;
import me.usainsrht.basicparkour.core.config.MessageConfig;
import me.usainsrht.basicparkour.core.editor.EditorManager;
import me.usainsrht.basicparkour.core.gui.MenuConfig;
import me.usainsrht.basicparkour.core.generator.GeneratorManagerImpl;
import me.usainsrht.basicparkour.core.ghost.GhostRecorderImpl;
import me.usainsrht.basicparkour.core.listener.GeneratorEntryListener;
import me.usainsrht.basicparkour.core.listener.PlayerMoveListener;
import me.usainsrht.basicparkour.core.listener.PlayerQuitListener;
import me.usainsrht.basicparkour.core.modifier.BoostBlockModifier;
import me.usainsrht.basicparkour.core.modifier.JumpPadModifier;
import me.usainsrht.basicparkour.core.modifier.KillBlockModifier;
import me.usainsrht.basicparkour.core.modifier.ModifierRegistryImpl;
import me.usainsrht.basicparkour.core.scheduling.FoliaScheduler;
import me.usainsrht.basicparkour.core.session.SessionManager;
import me.usainsrht.basicparkour.core.storage.RepositoryFactory;
import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Level;

/**
 * Main plugin class for Basic Parkour.
 *
 * <h2>Boot order</h2>
 * <ol>
 * <li>Load configuration ({@code config.yml}) and save defaults.</li>
 * <li>Initialise {@link FoliaScheduler} (detects Folia at class-load
 * time).</li>
 * <li>Create and initialise {@link ParkourRepository} from config.</li>
 * <li>Build {@link ModifierRegistryImpl} and register built-in modifiers.</li>
 * <li>Build {@link SessionManager} and start its timer loop.</li>
 * <li>Register the API singleton.</li>
 * <li>Load course definitions from {@code courses/}.</li>
 * <li>Register event listeners and commands (Brigadier via LifecycleEvents).</li>
 * </ol>
 *
 * <h2>Shutdown order</h2>
 * <ol>
 * <li>Shut down {@link SessionManager} (invalidates all sessions).</li>
 * <li>Close the repository (flushes connection pools).</li>
 * <li>Unregister the API singleton.</li>
 * </ol>
 */
@SuppressWarnings("UnstableApiUsage")
public final class BasicParkourPlugin extends JavaPlugin {

    // ── Component references ───────────────────────────────────────────────
    private FoliaScheduler scheduler;
    private ParkourRepository repository;
    private ModifierRegistryImpl modifierRegistry;
    private SessionManager sessionManager;
    private GhostRecorderImpl ghostRecorder;
    private MessageConfig messages;
    private MenuConfig menuConfig;
    private EditorManager editorManager;
    private BasicParkourAPIImpl basicParkourAPI;
    private CommandConfig commandConfig;
    private GeneratorManagerImpl generatorManager;
    private me.usainsrht.basicparkour.core.hotbar.HotbarManager hotbarManager;
    private me.usainsrht.basicparkour.core.tab.TabHook tabHook;
    private me.usainsrht.basicparkour.core.hologram.HologramManager hologramManager;
    private me.usainsrht.basicparkour.core.npc.NpcManager npcManager;
    private me.usainsrht.basicparkour.core.listener.GeneratorEntryListener entryListener;

    // ────────────────────────────────────────────────────────────────────────────

    @Override
    public void onEnable() {
        // ── 1. Configuration ─────────────────────────────────────────────────
        saveDefaultConfig();
        java.io.File messagesFile = new java.io.File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            saveResource("messages.yml", false);
        }
        org.bukkit.configuration.file.YamlConfiguration messagesConfig =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(messagesFile);
        messages = new MessageConfig(messagesConfig);

        java.io.File menusFile = new java.io.File(getDataFolder(), "menus.yml");
        if (!menusFile.exists()) {
            saveResource("menus.yml", false);
        }
        org.bukkit.configuration.file.YamlConfiguration menusConfig =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(menusFile);
        menuConfig = new MenuConfig(menusConfig);

        editorManager = new EditorManager(this);
        commandConfig = new CommandConfig(getConfig());

        // ── 2. Scheduler ─────────────────────────────────────────────────────
        scheduler = new FoliaScheduler(this);
        getLogger().info("[BasicParkour] Running on " + (FoliaScheduler.isFolia() ? "Folia" : "Paper"));

        // ── 3. Storage ───────────────────────────────────────────────────────
        try {
            repository = RepositoryFactory.create(this);
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "[BasicParkour] Failed to initialise storage — disabling plugin!", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // ── 4. Modifier registry ─────────────────────────────────────────────
        modifierRegistry = new ModifierRegistryImpl();
        registerBuiltInModifiers();

        // ── 4b. TAB Hook ─────────────────────────────────────────────────────
        tabHook = new me.usainsrht.basicparkour.core.tab.TabHook(this);

        // ── 5. Session manager & Hotbar ─────────────────────────────────────
        sessionManager = new SessionManager(this, scheduler, messages);
        hotbarManager = new me.usainsrht.basicparkour.core.hotbar.HotbarManager(this, sessionManager);
        sessionManager.setHotbarManager(hotbarManager);

        // ── 6. Ghost recorder ────────────────────────────────────────────────
        ghostRecorder = new GhostRecorderImpl(this, scheduler);

        // ── 7. API singleton ─────────────────────────────────────────────────
        basicParkourAPI = new BasicParkourAPIImpl(sessionManager, modifierRegistry, repository, ghostRecorder);
        BasicParkourAPI.register(basicParkourAPI);
        getServer().getServicesManager().register(BasicParkourAPI.class, basicParkourAPI, this, org.bukkit.plugin.ServicePriority.Normal);

        // ── 8. Load courses ──────────────────────────────────────────────────
        CourseLoader loader = new CourseLoader(this);
        basicParkourAPI.getCourseRegistry().loadAll(loader.loadAll());
        getLogger().info("[BasicParkour] Loaded %d course(s).".formatted(
                basicParkourAPI.getCourseRegistry().getCourses().size()));

        // ── 8b. Load generator templates ─────────────────────────────────────
        generatorManager = new GeneratorManagerImpl(this, scheduler, sessionManager);
        GeneratorTemplateLoader templateLoader = new GeneratorTemplateLoader(this);
        generatorManager.loadTemplates(templateLoader.loadAll());
        basicParkourAPI.setGeneratorManager(generatorManager);

        // ── 9. Start timer loop ──────────────────────────────────────────────
        sessionManager.start();

        // ── 10. Listeners ────────────────────────────────────────────────────
        var pm = getServer().getPluginManager();
        pm.registerEvents(new PlayerMoveListener(this, sessionManager), this);
        pm.registerEvents(new PlayerQuitListener(this, sessionManager), this);
        pm.registerEvents(new me.usainsrht.basicparkour.core.listener.ParkourProtectionListener(this, sessionManager), this);
        pm.registerEvents(new me.usainsrht.basicparkour.core.listener.ParkourRestrictionListener(this, sessionManager), this);
        pm.registerEvents(hotbarManager, this);
        pm.registerEvents(new me.usainsrht.basicparkour.core.gui.GUIListener(), this);
        pm.registerEvents(new me.usainsrht.basicparkour.core.editor.WandListener(this, editorManager), this);
        // Collision suppression is handled via ParkourTeamManager inside SessionManager
        // (no event needed)

        // ── 10b. Generator entry listener ────────────────────────────────────
        entryListener = new GeneratorEntryListener(this, generatorManager);
        pm.registerEvents(entryListener, this);

        // ── 10c. Hologram & NPC Managers ─────────────────────────────────────
        hologramManager = new me.usainsrht.basicparkour.core.hologram.HologramManager(this);
        npcManager = new me.usainsrht.basicparkour.core.npc.NpcManager(this);

        // ── 11. Commands (Paper Brigadier) ──────────────────────────────────────
        BasicParkourCommand cmdHandler =
                new BasicParkourCommand(this, sessionManager, commandConfig);
        cmdHandler.setGeneratorReferences(generatorManager, entryListener);
        cmdHandler.setEditorManager(editorManager);
        cmdHandler.setHologramManager(hologramManager);
        cmdHandler.setNpcManager(npcManager);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                event -> cmdHandler.register(event.registrar()));

        // ── 11b. PlaceholderAPI ───────────────────────────────────────────────
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new me.usainsrht.basicparkour.core.papi.BasicParkourExpansion(this).register();
            getLogger().info("[BasicParkour] PlaceholderAPI expansion registered.");
        }

        // ── 11c. MiniPlaceholders ─────────────────────────────────────────────
        me.usainsrht.basicparkour.core.miniplaceholders.MiniPlaceholdersHook.init(this);

        // ── 12. bStats Metrics ────────────────────────────────────────────────
        new Metrics(this, 31868);

        getLogger().info("[BasicParkour] Plugin enabled successfully! "
                + "Courses loaded: " + basicParkourAPI.getCourseRegistry().getCourses().size());
    }

    @Override
    public void onDisable() {
        if (hologramManager != null)
            hologramManager.shutdown();
        if (tabHook != null)
            tabHook.cleanup();
        me.usainsrht.basicparkour.core.miniplaceholders.MiniPlaceholdersHook.shutdown();
        if (generatorManager != null)
            generatorManager.shutdown();
        if (sessionManager != null)
            sessionManager.shutdown();
        if (hotbarManager != null)
            hotbarManager.restoreAll();
        if (repository != null)
            repository.close();
        if (basicParkourAPI != null) {
            getServer().getServicesManager().unregister(BasicParkourAPI.class, basicParkourAPI);
        }
        BasicParkourAPI.unregister();
        getLogger().info("[BasicParkour] Plugin disabled.");
    }

    /**
     * Convenience static method to retrieve the registered {@link BasicParkourAPI} singleton.
     *
     * @return the API singleton
     * @throws IllegalStateException if the plugin is not enabled
     */
    @NotNull
    public static BasicParkourAPI getApi() {
        return BasicParkourAPI.get();
    }

    // -----------------------------------------------------------------------
    // Reload
    // -----------------------------------------------------------------------

    /**
     * Safely and asynchronously reloads the plugin:
     * 1. Safely stops every active parkour session, making players leave cleanly and restoring their states.
     * 2. Shuts down active generator instances and restores placed blocks.
     * 3. Dispatches asynchronous loading of configs, courses, generator templates, and database.
     * 4. Dispatches back to the server/global thread to apply the reloaded courses, templates, holograms, NPCs, and menus.
     *
     * @return CompletableFuture completing when reload has finished.
     */
    public java.util.concurrent.CompletableFuture<Void> reloadPluginAsync() {
        java.util.concurrent.CompletableFuture<Void> future = new java.util.concurrent.CompletableFuture<>();

        // Step 1: Safely stop all sessions and make players leave on their region/entity threads
        scheduler.runGlobal(() -> {
            try {
                if (sessionManager != null) {
                    sessionManager.stopAllSessions();
                }
                if (generatorManager != null) {
                    generatorManager.shutdown();
                }

                // Step 2: Async file I/O & parsing
                scheduler.runAsync(() -> {
                    try {
                        reloadConfig();

                        java.io.File messagesFile = new java.io.File(getDataFolder(), "messages.yml");
                        org.bukkit.configuration.file.YamlConfiguration newMessages = messagesFile.exists()
                                ? org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(messagesFile)
                                : null;

                        java.io.File menusFile = new java.io.File(getDataFolder(), "menus.yml");
                        org.bukkit.configuration.file.YamlConfiguration newMenus = menusFile.exists()
                                ? org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(menusFile)
                                : null;

                        CourseLoader loader = new CourseLoader(this);
                        var newCourses = loader.loadAll();

                        GeneratorTemplateLoader templateLoader = new GeneratorTemplateLoader(this);
                        var newTemplates = templateLoader.loadAll();

                        // Step 3: Apply reloaded state on global/main thread
                        scheduler.runGlobal(() -> {
                            try {
                                if (newMessages != null) {
                                    messages.reload(newMessages);
                                }
                                if (newMenus != null) {
                                    menuConfig.reload(newMenus);
                                }
                                if (hotbarManager != null) {
                                    hotbarManager.load();
                                }

                                // Re-register courses
                                new java.util.ArrayList<>(basicParkourAPI.getCourseRegistry().getCourses())
                                        .forEach(c -> basicParkourAPI.getCourseRegistry().unregisterCourse(c.getId()));
                                basicParkourAPI.getCourseRegistry().loadAll(newCourses);

                                // Reload generator templates
                                if (generatorManager != null) {
                                    generatorManager.loadTemplates(newTemplates);
                                }
                                if (entryListener != null) {
                                    entryListener.reloadEntries();
                                }

                                if (hologramManager != null) {
                                    hologramManager.selectProvider();
                                    hologramManager.load();
                                }
                                if (npcManager != null) {
                                    npcManager.load();
                                    npcManager.refreshAllTopHolders();
                                }

                                getLogger().info("[BasicParkour] Safely reloaded. Courses: "
                                        + basicParkourAPI.getCourseRegistry().getCourses().size()
                                        + ", Generator templates: "
                                        + (generatorManager != null ? generatorManager.getTemplates().size() : 0));

                                future.complete(null);
                            } catch (Throwable t) {
                                future.completeExceptionally(t);
                            }
                        });
                    } catch (Throwable t) {
                        future.completeExceptionally(t);
                    }
                });
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });

        return future;
    }

    @NotNull
    public java.util.concurrent.CompletableFuture<Void> reloadPlugin() {
        return reloadPluginAsync();
    }

    // -----------------------------------------------------------------------
    // Built-in modifier registration
    // -----------------------------------------------------------------------

    private void registerBuiltInModifiers() {
        modifierRegistry.register(new JumpPadModifier());
        modifierRegistry.register(new BoostBlockModifier());
        modifierRegistry.register(new KillBlockModifier());
    }

    // -----------------------------------------------------------------------
    // Accessors (for listeners, commands, etc.)
    // -----------------------------------------------------------------------

    @NotNull
    public FoliaScheduler getScheduler() {
        return scheduler;
    }

    @NotNull
    public ParkourRepository getRepository() {
        return repository;
    }

    @NotNull
    public GhostRecorder getGhostRecorder() {
        return ghostRecorder;
    }

    @NotNull
    public SessionManager getSessionManager() {
        return sessionManager;
    }

    @NotNull
    public MessageConfig getMessages() {
        return messages;
    }

    @NotNull
    public MenuConfig getMenuConfig() {
        return menuConfig;
    }

    @NotNull
    public EditorManager getEditorManager() {
        return editorManager;
    }

    @NotNull
    public CommandConfig getCommandConfig() {
        return commandConfig;
    }

    @NotNull
    public BasicParkourAPIImpl getBasicParkourAPI() {
        return basicParkourAPI;
    }

    public GeneratorManagerImpl getGeneratorManager() {
        return generatorManager;
    }

    public me.usainsrht.basicparkour.core.tab.TabHook getTabHook() {
        return tabHook;
    }

    public me.usainsrht.basicparkour.core.hologram.HologramManager getHologramManager() {
        return hologramManager;
    }

    public me.usainsrht.basicparkour.core.npc.NpcManager getNpcManager() {
        return npcManager;
    }

    public me.usainsrht.basicparkour.core.hotbar.HotbarManager getHotbarManager() {
        return hotbarManager;
    }

    public me.usainsrht.basicparkour.core.listener.GeneratorEntryListener getGeneratorEntryListener() {
        return entryListener;
    }
}
