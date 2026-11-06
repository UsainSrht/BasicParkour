package me.usainsrht.basicparkour.core.access;

import me.usainsrht.basicparkour.api.BasicParkourAPI;
import me.usainsrht.basicparkour.api.course.ParkourCourse;
import me.usainsrht.basicparkour.api.generator.GeneratedParkourCourse;
import me.usainsrht.basicparkour.api.generator.GeneratorTemplate;
import me.usainsrht.basicparkour.core.BasicParkourPlugin;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Decides which courses and endless templates a sender may see and play.
 *
 * <p>Per-course and per-template permission nodes are configured at {@code commands.permissions.course} /
 * {@code commands.permissions.endless} ({@code {id}} is the lower-cased id). A sender with the admin permission may
 * always do everything. For everyone else a node that no permission plugin has set falls back to
 * {@code commands.permissions.default-access}, so an existing server keeps working until nodes are denied (for
 * example {@code -basicparkour.endless.medium}) or the default is switched to {@code false} for a whitelist model.</p>
 *
 * <p>Generated runs ({@code generated_N}) are private, short-lived courses and are never listed or joinable by name.</p>
 */
public final class AccessControl {

    private final BasicParkourPlugin plugin;

    public AccessControl(@NotNull BasicParkourPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Checks ─────────────────────────────────────────────────────────────

    public boolean isAdmin(@NotNull CommandSender sender) {
        return sender.hasPermission(plugin.getCommandConfig().getPermAdmin());
    }

    public boolean canPlayTemplate(@NotNull CommandSender sender, @NotNull String templateId) {
        return check(sender, node("endless", "basicparkour.endless.{id}", templateId));
    }

    public boolean canPlayCourse(@NotNull CommandSender sender, @NotNull String courseId) {
        return check(sender, node("course", "basicparkour.course.{id}", courseId));
    }

    /** The permission node a sender needs for a template (for messages / docs). */
    public @NotNull String templateNode(@NotNull String templateId) {
        return node("endless", "basicparkour.endless.{id}", templateId);
    }

    public @NotNull String courseNode(@NotNull String courseId) {
        return node("course", "basicparkour.course.{id}", courseId);
    }

    // ── Listings ───────────────────────────────────────────────────────────

    /** Premade courses only (never {@code generated_N} runs), no permission filtering. For admin listings. */
    public @NotNull List<ParkourCourse> allPremadeCourses() {
        List<ParkourCourse> out = new ArrayList<>();
        for (ParkourCourse c : BasicParkourAPI.get().getCourseRegistry().getCourses()) {
            if (!(c instanceof GeneratedParkourCourse)) out.add(c);
        }
        return out;
    }

    /** Premade courses the sender may play. */
    public @NotNull List<ParkourCourse> visibleCourses(@NotNull CommandSender sender) {
        List<ParkourCourse> out = new ArrayList<>();
        for (ParkourCourse c : allPremadeCourses()) {
            if (canPlayCourse(sender, c.getId())) out.add(c);
        }
        return out;
    }

    /** Endless templates the sender may play. */
    public @NotNull List<GeneratorTemplate> visibleTemplates(@NotNull CommandSender sender) {
        List<GeneratorTemplate> out = new ArrayList<>();
        for (GeneratorTemplate t : BasicParkourAPI.get().getGeneratorManager().getTemplates()) {
            if (canPlayTemplate(sender, t.id())) out.add(t);
        }
        return out;
    }

    // ── Internals ──────────────────────────────────────────────────────────

    private boolean check(@NotNull CommandSender sender, @NotNull String node) {
        if (isAdmin(sender)) return true;
        // A node nobody has set resolves to OP-only in Bukkit, so decide the default ourselves
        if (sender.isPermissionSet(node)) return sender.hasPermission(node);
        return plugin.getConfig().getBoolean("commands.permissions.default-access", true);
    }

    private @NotNull String node(@NotNull String key, @NotNull String fallback, @NotNull String id) {
        String pattern = plugin.getConfig().getString("commands.permissions." + key, fallback);
        return pattern.replace("{id}", id.toLowerCase(Locale.ROOT));
    }
}
