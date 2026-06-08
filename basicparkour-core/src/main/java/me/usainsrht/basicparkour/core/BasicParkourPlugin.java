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
    private BasicParkourAPIImpl basicParkourAPI;
    private CommandConfig commandConfig;
    private GeneratorManagerImpl generatorManager;

    // ────────────────────────────────────────────────────────────────────────────

    @Override
    public void onEnable() {
        // ── 1. Configuration ─────────────────────────────────────────────────
        saveDefaultConfig();
        messages = new MessageConfig(getConfig());
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

        // ── 5. Session manager ───────────────────────────────────────────────
        sessionManager = new SessionManager(this, scheduler, messages);

        // ── 6. Ghost recorder ────────────────────────────────────────────────
        ghostRecorder = new GhostRecorderImpl(this, scheduler);

        // ── 7. API singleton ─────────────────────────────────────────────────
        basicParkourAPI = new BasicParkourAPIImpl(sessionManager, modifierRegistry, repository);
        BasicParkourAPI.register(basicParkourAPI);

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
        pm.registerEvents(new PlayerQuitListener(sessionManager), this);
        // Collision suppression is handled via ParkourTeamManager inside SessionManager
        // (no event needed)

        // ── 10b. Generator entry listener ────────────────────────────────────
        GeneratorEntryListener entryListener = new GeneratorEntryListener(this, generatorManager);
        pm.registerEvents(entryListener, this);

        // ── 11. Commands (Paper Brigadier) ──────────────────────────────────────
        BasicParkourCommand cmdHandler =
                new BasicParkourCommand(this, sessionManager, commandConfig);
        cmdHandler.setGeneratorReferences(generatorManager, entryListener);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                event -> cmdHandler.register(event.registrar()));

        // ── 12. bStats Metrics ────────────────────────────────────────────────
        new Metrics(this, 31868);

        getLogger().info("[BasicParkour] Plugin enabled successfully! "
                + "Courses loaded: " + basicParkourAPI.getCourseRegistry().getCourses().size());
    }

    @Override
    public void onDisable() {
        if (generatorManager != null)
            generatorManager.shutdown();
        if (sessionManager != null)
            sessionManager.shutdown();
        if (repository != null)
            repository.close();
        BasicParkourAPI.unregister();
        getLogger().info("[BasicParkour] Plugin disabled.");
    }

    // -----------------------------------------------------------------------
    // Reload
    // -----------------------------------------------------------------------

    /**
     * Reloads configuration files and course definitions without restarting the
     * plugin.
     * Active sessions are preserved.
     */
    public void reloadPlugin() {
        reloadConfig();
        messages.reload(getConfig());

        // Re-load courses
        CourseLoader loader = new CourseLoader(this);
        // Unregister existing and re-register
        new java.util.ArrayList<>(basicParkourAPI.getCourseRegistry().getCourses())
                .forEach(c -> basicParkourAPI.getCourseRegistry().unregisterCourse(c.getId()));
        basicParkourAPI.getCourseRegistry().loadAll(loader.loadAll());

        getLogger().info("[BasicParkour] Reloaded. Courses: "
                + basicParkourAPI.getCourseRegistry().getCourses().size());
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
    public MessageConfig getMessages() {
        return messages;
    }

    @NotNull
    public CommandConfig getCommandConfig() {
        return commandConfig;
    }

    @NotNull
    public BasicParkourAPIImpl getBasicParkourAPI() {
        return basicParkourAPI;
    }
}
