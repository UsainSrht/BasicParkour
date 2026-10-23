package me.usainsrht.basicparkour.core.gui;

import me.usainsrht.basicparkour.api.BasicParkourAPI;
import me.usainsrht.basicparkour.api.course.ParkourCourse;
import me.usainsrht.basicparkour.api.storage.LeaderboardEntry;
import me.usainsrht.basicparkour.api.storage.PlayerRecord;
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
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Native GUI for browsing, joining, and viewing leaderboards of parkour courses.
 */
public final class CourseSelectorGUI extends BasicParkourGUI {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final int page;
    private final Map<Integer, ParkourCourse> slotToCourse = new HashMap<>();

    public CourseSelectorGUI(@NotNull BasicParkourPlugin plugin, @NotNull Player player) {
        this(plugin, player, 0);
    }

    public CourseSelectorGUI(@NotNull BasicParkourPlugin plugin, @NotNull Player player, int page) {
        super(plugin, player);
        this.page = Math.max(0, page);
    }

    @Override
    public void open() {
        ConfigurationSection sec = plugin.getMenuConfig().getSection("course_selector");
        if (sec == null) {
            player.sendMessage(MINI.deserialize("<red>Course selector menu is not configured.</red>"));
            return;
        }

        List<ParkourCourse> allCourses = new ArrayList<>(
                BasicParkourAPI.get().getCourseRegistry().getCourses());
        allCourses.sort(Comparator.comparing(ParkourCourse::getId));

        List<Integer> rawCourseSlots = sec.getIntegerList("course_slots");
        final List<Integer> courseSlots = rawCourseSlots.isEmpty()
                ? List.of(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34)
                : rawCourseSlots;

        int pageSize = courseSlots.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) allCourses.size() / pageSize));
        int currentPage = Math.min(page, totalPages - 1);

        int fromIndex = currentPage * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, allCourses.size());
        List<ParkourCourse> pageCourses = fromIndex < allCourses.size()
                ? allCourses.subList(fromIndex, toIndex)
                : Collections.emptyList();

        // Asynchronously fetch PBs and records for this page of courses
        List<CompletableFuture<?>> futures = new ArrayList<>();
        Map<String, String> pbMap = new HashMap<>();
        Map<String, String> bestTimeMap = new HashMap<>();
        Map<String, String> bestPlayerMap = new HashMap<>();

        for (ParkourCourse course : pageCourses) {
            String cid = course.getId();
            futures.add(plugin.getRepository().getPersonalBest(player.getUniqueId(), cid)
                    .thenAccept(opt -> pbMap.put(cid, opt.map(r -> TimerFormatter.format(r.timeMs())).orElse("None"))));
            futures.add(plugin.getRepository().getLeaderboard(cid, 1)
                    .thenAccept(board -> {
                        if (!board.isEmpty()) {
                            LeaderboardEntry top = board.get(0);
                            bestTimeMap.put(cid, TimerFormatter.format(top.timeMs()));
                            bestPlayerMap.put(cid, top.playerName());
                        } else {
                            bestTimeMap.put(cid, "None");
                            bestPlayerMap.put(cid, "None");
                        }
                    }));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenRun(() ->
                plugin.getScheduler().runOnEntity(player, () -> {
                    if (!player.isOnline()) return;

                    int size = sec.getInt("size", 54);
                    String rawTitle = sec.getString("title", "Parkour Courses");
                    Component title = MINI.deserialize(rawTitle,
                            Placeholder.parsed("page", String.valueOf(currentPage + 1)),
                            Placeholder.parsed("total_pages", String.valueOf(totalPages)));

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

                    // Populate courses
                    ConfigurationSection courseItemSec = sec.getConfigurationSection("course_item");
                    slotToCourse.clear();
                    for (int i = 0; i < pageCourses.size(); i++) {
                        int slot = courseSlots.get(i);
                        if (slot >= size) continue;

                        ParkourCourse course = pageCourses.get(i);
                        slotToCourse.put(slot, course);

                        String pb = pbMap.getOrDefault(course.getId(), "None");
                        String bestTime = bestTimeMap.getOrDefault(course.getId(), "None");
                        String bestPlayer = bestPlayerMap.getOrDefault(course.getId(), "None");

                        TagResolver resolver = TagResolver.resolver(
                                Placeholder.parsed("course_id", course.getId()),
                                Placeholder.parsed("course_name", MINI.serialize(course.getDisplayName())),
                                Placeholder.parsed("checkpoints", String.valueOf(course.getCheckpoints().size())),
                                Placeholder.parsed("personal_best", pb),
                                Placeholder.parsed("best_time", bestTime),
                                Placeholder.parsed("best_player", bestPlayer)
                        );

                        ItemStack item = courseItemSec != null
                                ? plugin.getMenuConfig().parseItem(courseItemSec, resolver)
                                : plugin.getMenuConfig().parseItem("course_selector.course_item", resolver);

                        inventory.setItem(slot, item);
                    }

                    // Pagination buttons
                    if (currentPage > 0) {
                        ConfigurationSection prevSec = sec.getConfigurationSection("previous_page");
                        if (prevSec != null) {
                            int prevSlot = prevSec.getInt("slot", 48);
                            TagResolver res = TagResolver.resolver(
                                    Placeholder.parsed("page_prev", String.valueOf(currentPage)));
                            inventory.setItem(prevSlot, plugin.getMenuConfig().parseItem(prevSec, res));
                        }
                    }

                    if (currentPage < totalPages - 1) {
                        ConfigurationSection nextSec = sec.getConfigurationSection("next_page");
                        if (nextSec != null) {
                            int nextSlot = nextSec.getInt("slot", 50);
                            TagResolver res = TagResolver.resolver(
                                    Placeholder.parsed("page_next", String.valueOf(currentPage + 2)));
                            inventory.setItem(nextSlot, plugin.getMenuConfig().parseItem(nextSec, res));
                        }
                    }

                    // Close button
                    ConfigurationSection closeSec = sec.getConfigurationSection("close_button");
                    if (closeSec != null) {
                        int closeSlot = closeSec.getInt("slot", 49);
                        inventory.setItem(closeSlot, plugin.getMenuConfig().parseItem(closeSec));
                    }

                    player.openInventory(inventory);
                }));
    }

    @Override
    public void handleClick(@NotNull Player player, int slot, @NotNull ClickType clickType) {
        ConfigurationSection sec = plugin.getMenuConfig().getSection("course_selector");
        if (sec == null) return;

        // Check close button
        ConfigurationSection closeSec = sec.getConfigurationSection("close_button");
        if (closeSec != null && slot == closeSec.getInt("slot", 49)) {
            player.closeInventory();
            return;
        }

        // Check previous page
        ConfigurationSection prevSec = sec.getConfigurationSection("previous_page");
        if (prevSec != null && slot == prevSec.getInt("slot", 48)) {
            new CourseSelectorGUI(plugin, player, page - 1).open();
            return;
        }

        // Check next page
        ConfigurationSection nextSec = sec.getConfigurationSection("next_page");
        if (nextSec != null && slot == nextSec.getInt("slot", 50)) {
            new CourseSelectorGUI(plugin, player, page + 1).open();
            return;
        }

        // Check course click
        ParkourCourse course = slotToCourse.get(slot);
        if (course != null) {
            if (clickType.isRightClick()) {
                // Open Leaderboard GUI for this course
                new LeaderboardGUI(plugin, player, course, page).open();
            } else {
                // Left click -> join course
                player.closeInventory();
                plugin.getSessionManager().startSession(player, course);
            }
        }
    }
}
