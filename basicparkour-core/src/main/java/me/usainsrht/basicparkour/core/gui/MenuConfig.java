package me.usainsrht.basicparkour.core.gui;

import me.usainsrht.itemapi.yamlitem.YamlItem;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Manages menus.yml configuration and parsing of GUI items using ItemAPI (YamlItem).
 */
public final class MenuConfig {

    private FileConfiguration config;

    public MenuConfig(@NotNull FileConfiguration config) {
        this.config = config;
    }

    public void reload(@NotNull FileConfiguration newConfig) {
        this.config = newConfig;
    }

    @Nullable
    public ConfigurationSection getSection(@NotNull String path) {
        return config.getConfigurationSection(path);
    }

    @NotNull
    public ItemStack parseItem(@NotNull String path, @NotNull TagResolver... resolvers) {
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) {
            return ItemStack.of(Material.AIR);
        }
        try {
            return YamlItem.parse(section, me.usainsrht.basicparkour.core.miniplaceholders.MiniPlaceholdersHook.combine(null, resolvers));
        } catch (Exception e) {
            return ItemStack.of(Material.STONE);
        }
    }

    @NotNull
    public ItemStack parseItem(@NotNull ConfigurationSection section, @NotNull TagResolver... resolvers) {
        try {
            return YamlItem.parse(section, me.usainsrht.basicparkour.core.miniplaceholders.MiniPlaceholdersHook.combine(null, resolvers));
        } catch (Exception e) {
            return ItemStack.of(Material.STONE);
        }
    }

    @NotNull
    public FileConfiguration getConfig() {
        return config;
    }
}
