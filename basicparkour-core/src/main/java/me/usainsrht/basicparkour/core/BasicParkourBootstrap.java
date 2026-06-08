package me.usainsrht.basicparkour.core;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.bootstrap.PluginProviderContext;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

/**
 * Paper plugin bootstrapper — executed very early in server startup, before
 * {@code onEnable()}.
 *
 * <p>This class is required by {@code paper-plugin.yml} when a
 * {@code bootstrapper:} field is declared. Commands are registered here via
 * {@link io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents#COMMANDS}
 * so that they are available to datapack command functions as early as
 * possible.</p>
 *
 * <p>Because the full plugin context (SessionManager, repository, etc.) is not
 * yet available at bootstrap time, the command handler holds a lazy reference to
 * the plugin instance that is resolved on first execution.</p>
 */
@SuppressWarnings("UnstableApiUsage")
public final class BasicParkourBootstrap implements PluginBootstrap {

    @Override
    public void bootstrap(@NotNull BootstrapContext context) {
        // Commands are registered in onEnable via getLifecycleManager() on the
        // JavaPlugin instance, because the full plugin context (SessionManager,
        // repository, CommandConfig) is required at that point.
        // If early command registration (datapack phase) is needed in the future,
        // move the Commands.register call here using context.getLifecycleManager().
    }

    @Override
    public @NotNull JavaPlugin createPlugin(@NotNull PluginProviderContext context) {
        return new BasicParkourPlugin();
    }
}
