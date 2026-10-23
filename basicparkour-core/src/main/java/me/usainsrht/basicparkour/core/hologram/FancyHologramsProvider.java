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
        if (!isAvailable()) return;

        try {
            HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
            Optional<Hologram> existing = manager.getHologram(name);

            if (existing.isPresent()) {
                Hologram holo = existing.get();
                if (holo.getData() instanceof TextHologramData textData) {
                    textData.setLocation(location);
                    textData.setText(new ArrayList<>(lines));
                    holo.refreshForViewers();
                }
            } else {
                TextHologramData data = new TextHologramData(name, location);
                data.setText(new ArrayList<>(lines));
                Hologram holo = manager.create(data);
                manager.addHologram(holo);
            }
        } catch (Throwable t) {
            Bukkit.getLogger().warning("[BasicParkour] Error in FancyHologramsProvider (" + name + "): " + t.getMessage());
        }
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
