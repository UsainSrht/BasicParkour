package me.usainsrht.basicparkour.api.generator;

import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/**
 * Defines the block palette used when generating a parkour course.
 *
 * <p>Themes are loaded from the {@code theme:} section inside a {@code generators/*.yml} file.
 * The generator picks blocks from these lists randomly (weighted uniformly) during platform
 * and obstacle placement.</p>
 *
 * @param name              Human-readable theme name (e.g. "forest").
 * @param platformMaterials Primary block types used for platforms.
 * @param accentMaterials   Secondary blocks mixed into platforms for visual variety.
 * @param obstacleMaterials Blocks used for obstacles placed on top of platforms
 *                          (slabs, fences, trapdoors, etc.).
 */
public record GeneratorTheme(
    @NotNull String name,
    @NotNull @Unmodifiable List<Material> platformMaterials,
    @NotNull @Unmodifiable List<Material> accentMaterials,
    @NotNull @Unmodifiable List<Material> obstacleMaterials
) {
    public GeneratorTheme {
        platformMaterials = List.copyOf(platformMaterials);
        accentMaterials   = List.copyOf(accentMaterials);
        obstacleMaterials = List.copyOf(obstacleMaterials);
        if (platformMaterials.isEmpty()) {
            throw new IllegalArgumentException("GeneratorTheme '" + name + "' must have at least one platform material.");
        }
    }

    /**
     * Returns the primary platform material (index 0), used as a safe fallback
     * when no accent block is available.
     */
    public @NotNull Material primaryPlatform() {
        return platformMaterials.get(0);
    }
}
