package me.usainsrht.basicparkour.core.storage;

import me.usainsrht.basicparkour.api.storage.ParkourRepository;
import me.usainsrht.basicparkour.core.BasicParkourPlugin;
import org.jetbrains.annotations.NotNull;

/**
 * Factory for creating {@link ParkourRepository} instances based on the
 * configured storage backend in {@code config.yml}.
 *
 * <p>Supported types (case-insensitive): {@code memory}, {@code sqlite}, {@code mysql}.</p>
 */
public final class RepositoryFactory {

    private RepositoryFactory() {}

    /**
     * Creates and initialises the appropriate repository.
     *
     * @param plugin the plugin instance (for config and data folder access)
     * @return the initialised repository
     * @throws Exception if storage initialisation fails
     */
    @NotNull
    public static ParkourRepository create(@NotNull BasicParkourPlugin plugin) throws Exception {
        String type = plugin.getConfig().getString("storage.type", "sqlite").toLowerCase();
        ParkourRepository repo = switch (type) {
            case "memory" -> new InMemoryRepository();
            case "mysql", "postgresql" -> new MySQLRepository(
                plugin.getConfig().getConfigurationSection("storage.mysql")
            );
            default -> new SQLiteRepository(plugin.getDataFolder());
        };
        repo.init();
        plugin.getLogger().info("[BasicParkour] Storage backend: " + type.toUpperCase());
        return repo;
    }
}
