package me.usainsrht.basicparkour.core.hologram;

import de.oliver.fancyholograms.api.FancyHologramsPlugin;
import de.oliver.fancyholograms.api.HologramManager;
import de.oliver.fancyholograms.api.data.TextHologramData;
import de.oliver.fancyholograms.api.hologram.Hologram;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Hologram provider implementing integration with FancyHolograms by Oliver.
 */
public final class FancyHologramsProvider implements HologramProvider {

    @Override
    public @NotNull String getName() {
        return "FancyHolograms";
    }

    @Override
    public boolean isAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("FancyHolograms") && FancyHologramsPlugin.isEnabled();
    }

    @Override
    public void createOrUpdateHologram(@NotNull String name, @NotNull Location location, @NotNull List<String> lines) {
        createOrUpdateHologram(name, location, lines, HologramDisplayProperties.EMPTY);
    }

    @Override
    public void createOrUpdateHologram(@NotNull String name, @NotNull Location location, @NotNull List<String> lines,
                                       @NotNull HologramDisplayProperties display) {
        if (!isAvailable()) return;

        try {
            HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
            Optional<Hologram> existing = manager.getHologram(name);

            if (existing.isPresent()) {
                Hologram holo = existing.get();
                if (holo.getData() instanceof TextHologramData textData) {
                    textData.setLocation(location);
                    textData.setText(new ArrayList<>(lines));
                    applyDisplay(textData, display);
                    holo.refreshForViewers();
                }
            } else {
                TextHologramData data = new TextHologramData(name, location);
                data.setText(new ArrayList<>(lines));
                applyDisplay(data, display);
                Hologram holo = manager.create(data);
                manager.addHologram(holo);
            }
        } catch (Throwable t) {
            Bukkit.getLogger().warning("[BasicParkour] Error in FancyHologramsProvider (" + name + "): " + t.getMessage());
        }
    }

    /** Applies every property that is set; unset properties keep FancyHolograms' defaults. */
    private static void applyDisplay(@NotNull TextHologramData data, @NotNull HologramDisplayProperties p) {
        if (p.billboard() != null) data.setBillboard(p.billboard());
        if (p.alignment() != null) data.setTextAlignment(p.alignment());
        if (p.background() != null) data.setBackground(p.backgroundColor());
        if (p.textShadow() != null) data.setTextShadow(p.textShadow());
        if (p.seeThrough() != null) data.setSeeThrough(p.seeThrough());
        if (p.scale() != null) data.setScale(new org.joml.Vector3f(p.scale(), p.scale(), p.scale()));
        if (p.hasBrightness()) data.setBrightness(new org.bukkit.entity.Display.Brightness(p.blockLight(), p.skyLight()));
        if (p.shadowRadius() != null) data.setShadowRadius(p.shadowRadius());
        if (p.shadowStrength() != null) data.setShadowStrength(p.shadowStrength());
    }

    @Override
    public void deleteHologram(@NotNull String name) {
        if (!isAvailable()) return;

        try {
            HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
            manager.getHologram(name).ifPresent(manager::removeHologram);
        } catch (Throwable t) {
            Bukkit.getLogger().warning("[BasicParkour] Error deleting FancyHologram (" + name + "): " + t.getMessage());
        }
    }
}
