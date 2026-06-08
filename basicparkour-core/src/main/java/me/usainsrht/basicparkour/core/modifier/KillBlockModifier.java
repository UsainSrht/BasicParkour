package me.usainsrht.basicparkour.core.modifier;

import me.usainsrht.basicparkour.api.modifier.BlockModifier;
import me.usainsrht.basicparkour.api.session.ParkourSession;
import me.usainsrht.basicparkour.core.session.SessionManager;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Block modifier that instantly fails the player's session, triggering a respawn
 * at their last checkpoint.
 *
 * <p>Default trigger material: {@link Material#RED_CONCRETE}.
 * Kill blocks do NOT teleport the player themselves — that is handled by
 * {@link SessionManager#failSession}.</p>
 */
public final class KillBlockModifier implements BlockModifier {

    public static final NamespacedKey KEY = new NamespacedKey("basicparkour", "kill");

    private final Material triggerMaterial;

    public KillBlockModifier(@NotNull Material triggerMaterial) {
        this.triggerMaterial = triggerMaterial;
    }

    /** Default: RED_CONCRETE. */
    public KillBlockModifier() {
        this(Material.RED_CONCRETE);
    }

    @Override @NotNull public NamespacedKey getKey() { return KEY; }

    @Override
    public boolean matches(@NotNull Block block) {
        return block.getType() == triggerMaterial;
    }

    @Override
    public void apply(@NotNull Player player, @NotNull ParkourSession session) {
        session.fail(ParkourSession.FailReason.KILL_BLOCK);
    }

    @Override @NotNull
    public String getDisplayName() {
        return "Kill Block (%s)".formatted(triggerMaterial);
    }
}
