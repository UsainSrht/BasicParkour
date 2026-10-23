package me.usainsrht.basicparkour.core.miniplaceholders;

import io.github.miniplaceholders.api.MiniPlaceholders;
import me.usainsrht.basicparkour.core.BasicParkourPlugin;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Manages MiniPlaceholders integration and provides tag resolvers for message formatting.
 */
public final class MiniPlaceholdersHook {

    private static boolean enabled = false;
    private static BasicParkourMiniPlaceholdersExpansion expansion;

    private MiniPlaceholdersHook() {}

    public static void init(@NotNull BasicParkourPlugin plugin) {
        if (Bukkit.getPluginManager().isPluginEnabled("MiniPlaceholders")) {
            try {
                enabled = true;
                expansion = new BasicParkourMiniPlaceholdersExpansion(plugin);
                expansion.register();
                plugin.getLogger().info("[BasicParkour] MiniPlaceholders integration enabled & expansion registered.");
            } catch (Throwable t) {
                enabled = false;
                plugin.getLogger().warning("[BasicParkour] Failed to initialize MiniPlaceholders: " + t.getMessage());
            }
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * Resolves audience and global placeholders if MiniPlaceholders is enabled.
     */
    @NotNull
    public static TagResolver getAudienceResolver(@Nullable Audience audience) {
        if (!enabled) {
            return TagResolver.empty();
        }
        try {
            return MiniPlaceholders.audienceGlobalPlaceholders();
        } catch (Throwable ignored) {
            return TagResolver.empty();
        }
    }

    /**
     * Resolves global placeholders if MiniPlaceholders is enabled.
     */
    @NotNull
    public static TagResolver getGlobalResolver() {
        if (!enabled) {
            return TagResolver.empty();
        }
        try {
            return MiniPlaceholders.globalPlaceholders();
        } catch (Throwable ignored) {
            return TagResolver.empty();
        }
    }

    /**
     * Combines custom tag resolvers with MiniPlaceholders audience tag resolver.
     */
    @NotNull
    public static TagResolver[] combine(@Nullable Audience audience, @NotNull TagResolver... resolvers) {
        if (!enabled) {
            return resolvers;
        }
        TagResolver audienceResolver = getAudienceResolver(audience);
        TagResolver[] combined = new TagResolver[resolvers.length + 1];
        System.arraycopy(resolvers, 0, combined, 0, resolvers.length);
        combined[resolvers.length] = audienceResolver;
        return combined;
    }

    public static void shutdown() {
        if (expansion != null) {
            try {
                expansion.unregister();
            } catch (Throwable ignored) {}
            expansion = null;
        }
        enabled = false;
    }
}
