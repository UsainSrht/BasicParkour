package me.usainsrht.basicparkour.core.modifier;

import me.usainsrht.basicparkour.api.modifier.BlockModifier;
import me.usainsrht.basicparkour.api.session.ParkourSession;
import me.usainsrht.basicparkour.core.util.VectorUtil;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

/**
 * Block modifier that propels the player forward in the direction they are facing,
 * with an optional vertical component.
 *
 * <p>Default trigger material: {@link Material#BLUE_CONCRETE}.
 * The forward vector is computed from the player's yaw (ignoring pitch) to prevent
 * boosting players downward when they look at the ground.</p>
 *
 * <p>Config: {@code forward-strength} and {@code vertical-strength}.</p>
 */
public final class BoostBlockModifier implements BlockModifier {

    public static final NamespacedKey KEY = new NamespacedKey("basicparkour", "boost");

    private final Material triggerMaterial;
    private final double forwardStrength;
    private final double verticalStrength;

    public BoostBlockModifier(@NotNull Material triggerMaterial,
                               double forwardStrength,
                               double verticalStrength) {
        this.triggerMaterial = triggerMaterial;
        this.forwardStrength = forwardStrength;
        this.verticalStrength = verticalStrength;
    }

    /** Default: BLUE_CONCRETE, forward=1.5, vertical=0.3. */
    public BoostBlockModifier() {
        this(Material.BLUE_CONCRETE, 1.5, 0.3);
    }

    @Override @NotNull public NamespacedKey getKey() { return KEY; }

    @Override
    public boolean matches(@NotNull Block block) {
        return block.getType() == triggerMaterial;
    }

    @Override
    public void apply(@NotNull Player player, @NotNull ParkourSession session) {
        Vector forward = VectorUtil.forwardVector(player.getLocation().getYaw())
            .multiply(forwardStrength);
        forward.setY(verticalStrength);
        player.setVelocity(forward);
    }

    @Override @NotNull
    public String getDisplayName() {
        return "Boost (fwd=%.2f, vert=%.2f, material=%s)".formatted(
            forwardStrength, verticalStrength, triggerMaterial);
    }
}
