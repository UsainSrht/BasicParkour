package me.usainsrht.basicparkour.core.hologram;

import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Hologram provider implementing integration with DecentHolograms.
 */
public final class DecentHologramsProvider implements HologramProvider {

    @Override
    public @NotNull String getName() {
        return "DecentHolograms";
    }

    @Override
    public boolean isAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("DecentHolograms");
    }

    @Override
    public void createOrUpdateHologram(@NotNull String name, @NotNull Location location, @NotNull List<String> lines) {
        if (!isAvailable()) return;

        try {
            List<String> formattedLines = convertLines(lines);
            Hologram existing = DHAPI.getHologram(name);
            if (existing != null) {
                DHAPI.moveHologram(existing, location);
                DHAPI.setHologramLines(existing, new ArrayList<>(formattedLines));
            } else {
                DHAPI.createHologram(name, location, false, new ArrayList<>(formattedLines));
            }
        } catch (Throwable t) {
            Bukkit.getLogger().warning("[BasicParkour] Error in DecentHologramsProvider (" + name + "): " + t.getMessage());
        }
    }

    private @NotNull List<String> convertLines(@NotNull List<String> lines) {
        List<String> result = new ArrayList<>(lines.size());
        for (String line : lines) {
            try {
                if (line.contains("<") && line.contains(">")) {
                    net.kyori.adventure.text.Component comp = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(line);
                    result.add(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(comp));
                } else {
                    result.add(line.replace('&', '§'));
                }
            } catch (Exception e) {
                result.add(line.replace('&', '§'));
            }
        }
        return result;
    }

    @Override
    public void deleteHologram(@NotNull String name) {
        if (!isAvailable()) return;

        try {
            if (DHAPI.getHologram(name) != null) {
                DHAPI.removeHologram(name);
            }
        } catch (Throwable t) {
            Bukkit.getLogger().warning("[BasicParkour] Error deleting DecentHologram (" + name + "): " + t.getMessage());
        }
    }
}
