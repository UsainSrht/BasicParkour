package me.usainsrht.basicparkour.core.gui;

import me.usainsrht.basicparkour.api.BasicParkourAPI;
import me.usainsrht.basicparkour.api.generator.GeneratorTemplate;
import me.usainsrht.basicparkour.core.BasicParkourPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Native GUI for selecting and launching endless parkour generator modes.
 */
public final class GeneratorSelectorGUI extends BasicParkourGUI {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final Map<Integer, GeneratorTemplate> slotToTemplate = new HashMap<>();

    public GeneratorSelectorGUI(@NotNull BasicParkourPlugin plugin, @NotNull Player player) {
        super(plugin, player);
    }

    @Override
    public void open() {
        ConfigurationSection sec = plugin.getMenuConfig().getSection("generator_selector");
        if (sec == null) {
            player.sendMessage(MINI.deserialize("<red>Generator selector menu is not configured.</red>"));
            return;
        }

        List<GeneratorTemplate> templates = new ArrayList<>(
                BasicParkourAPI.get().getGeneratorManager().getTemplates());
        templates.sort(Comparator.comparing(GeneratorTemplate::id));

        int size = sec.getInt("size", 27);
        String rawTitle = sec.getString("title", "Endless Parkour");
        Component title = MINI.deserialize(rawTitle);

        this.inventory = Bukkit.createInventory(this, size, title);

        // Fillers
        ConfigurationSection fillerSec = sec.getConfigurationSection("filler");
        if (fillerSec != null) {
            ItemStack fillerItem = plugin.getMenuConfig().parseItem(fillerSec);
            for (int slot : sec.getIntegerList("filler_slots")) {
                if (slot >= 0 && slot < size) {
                    inventory.setItem(slot, fillerItem);
                }
            }
        }

        // Templates
        List<Integer> slots = sec.getIntegerList("template_slots");
        if (slots.isEmpty()) {
            slots = List.of(11, 13, 15);
        }

        ConfigurationSection itemSec = sec.getConfigurationSection("template_item");
        slotToTemplate.clear();

        for (int i = 0; i < templates.size() && i < slots.size(); i++) {
            int slot = slots.get(i);
            if (slot >= size) continue;

            GeneratorTemplate tpl = templates.get(i);
            slotToTemplate.put(slot, tpl);

            TagResolver res = TagResolver.resolver(
                    Placeholder.parsed("template_id", tpl.id()),
                    Placeholder.parsed("template_name", tpl.displayName()),
                    Placeholder.parsed("difficulty", tpl.difficulty().name()),
                    Placeholder.parsed("algorithm", tpl.lengthType().name())
            );

            ItemStack item = itemSec != null
                    ? plugin.getMenuConfig().parseItem(itemSec, res)
                    : plugin.getMenuConfig().parseItem("generator_selector.template_item", res);

            inventory.setItem(slot, item);
        }

        // Close button
        ConfigurationSection closeSec = sec.getConfigurationSection("close_button");
        if (closeSec != null) {
            int closeSlot = closeSec.getInt("slot", 22);
            inventory.setItem(closeSlot, plugin.getMenuConfig().parseItem(closeSec));
        }

        player.openInventory(inventory);
    }

    @Override
    public void handleClick(@NotNull Player player, int slot, @NotNull ClickType clickType) {
        ConfigurationSection sec = plugin.getMenuConfig().getSection("generator_selector");
        if (sec == null) return;

        ConfigurationSection closeSec = sec.getConfigurationSection("close_button");
        if (closeSec != null && slot == closeSec.getInt("slot", 22)) {
            player.closeInventory();
            return;
        }

        GeneratorTemplate tpl = slotToTemplate.get(slot);
        if (tpl != null) {
            player.closeInventory();
            player.sendMessage(MINI.deserialize("<gray>Generating endless parkour run for <aqua>" + tpl.displayName() + "</aqua>...</gray>"));
            BasicParkourAPI.get().getGeneratorManager().generate(player, tpl);
        }
    }
}
