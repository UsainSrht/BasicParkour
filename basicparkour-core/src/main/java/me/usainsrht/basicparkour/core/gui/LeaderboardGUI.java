package me.usainsrht.basicparkour.core.gui;

import me.usainsrht.basicparkour.api.course.ParkourCourse;
import me.usainsrht.basicparkour.api.storage.LeaderboardEntry;
import me.usainsrht.basicparkour.core.BasicParkourPlugin;
import me.usainsrht.basicparkour.core.util.TimerFormatter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Native GUI displaying top leaderboard records for a parkour course.
 */
public final class LeaderboardGUI extends BasicParkourGUI {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final ParkourCourse course;
    private final int returnPage;

    public LeaderboardGUI(@NotNull BasicParkourPlugin plugin,
                          @NotNull Player player,
                          @NotNull ParkourCourse course,
                          int returnPage) {
        super(plugin, player);
        this.course = course;
        this.returnPage = returnPage;
    }

    @Override
    public void open() {
        ConfigurationSection sec = plugin.getMenuConfig().getSection("leaderboard");
        if (sec == null) {
            player.sendMessage(MINI.deserialize("<red>Leaderboard menu is not configured.</red>"));
            return;
        }

        List<Integer> entrySlots = sec.getIntegerList("entry_slots");
        if (entrySlots.isEmpty()) {
            entrySlots = List.of(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34);
        }

        final List<Integer> slots = entrySlots;
        int limit = slots.size();

        plugin.getRepository().getLeaderboard(course.getId(), limit).thenAccept(board ->
                plugin.getScheduler().runOnEntity(player, () -> {
                    if (!player.isOnline()) return;

                    int size = sec.getInt("size", 54);
                    String rawTitle = sec.getString("title", "Leaderboard: <course_name>");
                    Component title = MINI.deserialize(rawTitle,
                            Placeholder.parsed("course_id", course.getId()),
                            Placeholder.parsed("course_name", MINI.serialize(course.getDisplayName())));

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

                    // Populate entries
                    ConfigurationSection entryItemSec = sec.getConfigurationSection("entry_item");
                    ConfigurationSection emptySec = sec.getConfigurationSection("empty_entry_item");

                    for (int i = 0; i < slots.size(); i++) {
                        int slot = slots.get(i);
                        if (slot >= size) continue;

                        int rank = i + 1;
                        if (i < board.size()) {
                            LeaderboardEntry entry = board.get(i);
                            TagResolver res = TagResolver.resolver(
                                    Placeholder.parsed("rank", String.valueOf(entry.rank())),
                                    Placeholder.parsed("player_name", entry.playerName()),
                                    Placeholder.parsed("blocks", String.valueOf(entry.blocks())),
                                    Placeholder.parsed("time", TimerFormatter.format(entry.timeMs()))
                            );

                            ConfigurationSection itemSec = (entry.blocks() > 0 && sec.isConfigurationSection("entry_item_endless"))
                                    ? sec.getConfigurationSection("entry_item_endless")
                                    : entryItemSec;

                            ItemStack item = itemSec != null
                                    ? plugin.getMenuConfig().parseItem(itemSec, res)
                                    : plugin.getMenuConfig().parseItem("leaderboard.entry_item", res);

                            if (item.getItemMeta() instanceof SkullMeta skullMeta) {
                                skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(entry.playerUuid()));
                                item.setItemMeta(skullMeta);
                            }
                            inventory.setItem(slot, item);
                        } else if (emptySec != null) {
                            TagResolver res = TagResolver.resolver(
                                    Placeholder.parsed("rank", String.valueOf(rank))
                            );
                            inventory.setItem(slot, plugin.getMenuConfig().parseItem(emptySec, res));
                        }
                    }

                    // Back button
                    ConfigurationSection backSec = sec.getConfigurationSection("back_button");
                    if (backSec != null) {
                        int backSlot = backSec.getInt("slot", 49);
                        inventory.setItem(backSlot, plugin.getMenuConfig().parseItem(backSec));
                    }

                    player.openInventory(inventory);
                }));
    }

    @Override
    public void handleClick(@NotNull Player player, int slot, @NotNull ClickType clickType) {
        ConfigurationSection sec = plugin.getMenuConfig().getSection("leaderboard");
        if (sec == null) return;

        ConfigurationSection backSec = sec.getConfigurationSection("back_button");
        if (backSec != null && slot == backSec.getInt("slot", 49)) {
            new CourseSelectorGUI(plugin, player, returnPage).open();
        }
    }
}
