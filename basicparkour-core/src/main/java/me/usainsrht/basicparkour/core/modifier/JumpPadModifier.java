package me.usainsrht.basicparkour.core.modifier;

import me.usainsrht.basicparkour.api.modifier.BlockModifier;
import me.usainsrht.basicparkour.api.session.ParkourSession;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

/**
 * Block modifier that launches the player straight upward with configurable strength.
 *
 * <p>Default trigger material: {@link Material#SLIME_BLOCK}.
 * Configurable via course YAML modifier overrides.</p>
 *
 * <p>Velocity is applied as a pure Y-axis impulse, ignoring any existing velocity
 * to ensure consistent launch height regardless of the player's current speed.</p>
 */
public final class JumpPadModifier implements BlockModifier {

    public static final NamespacedKey KEY = new NamespacedKey("basicparkour", "jump_pad");

    private final Material triggerMaterial;
    private final double strength;

    /**
     * @param triggerMaterial the block material that triggers this modifier
     * @param strength        vertical velocity magnitude (recommended: 0.5–2.0)
     */
    public JumpPadModifier(@NotNull Material triggerMaterial, double strength) {
        this.triggerMaterial = triggerMaterial;
        this.strength = strength;
    }

    /** Default constructor: SLIME_BLOCK with strength 1.2. */
    public JumpPadModifier() {
        this(Material.SLIME_BLOCK, 1.2);
    }

    @Override
    @NotNull
    public NamespacedKey getKey() { return KEY; }

    @Override
    public boolean matches(@NotNull Block block) {
        return block.getType() == triggerMaterial;
    }

    @Override
    public void apply(@NotNull Player player, @NotNull ParkourSession session) {
        // Preserve X/Z momentum, replace Y with configured upward impulse
        Vector current = player.getVelocity();
        player.setVelocity(new Vector(current.getX(), strength, current.getZ()));
    }

    @Override
    @NotNull
    public String getDisplayName() { return "Jump Pad (%s, strength=%.2f)".formatted(triggerMaterial, strength); }
}
