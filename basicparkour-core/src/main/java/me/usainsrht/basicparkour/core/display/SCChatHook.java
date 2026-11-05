package me.usainsrht.basicparkour.core.display;

import me.usainsrht.scchat.SCChat;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Thin wrapper around SCChat (optional soft dependency).
 *
 * <p>This is the only class that references SCChat types. Callers must check {@link #isAvailable()} first, so
 * the class (and with it SCChat's) is never loaded on servers that do not have SCChat.</p>
 */
final class SCChatHook {

    private SCChatHook() {}

    static boolean isAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("SCChat");
    }

    /**
     * SCChat's display name for any player (offline players load their LuckPerms user asynchronously).
     * Completes with {@code null} if SCChat fails, so callers can fall back to the plain name.
     */
    static @NotNull CompletableFuture<Component> displayName(@NotNull UUID uuid) {
        try {
            OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
            return SCChat.getInstance().getChatManager().getDisplayName(player).exceptionally(ex -> null);
        } catch (Throwable t) {
            return CompletableFuture.completedFuture(null);
        }
    }
}
