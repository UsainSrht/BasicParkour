package me.usainsrht.basicparkour.core.npc;

import me.usainsrht.basicparkour.core.BasicParkourPlugin;
import net.citizensnpcs.api.event.NPCLeftClickEvent;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.trait.Trait;
import net.citizensnpcs.api.trait.TraitName;
import net.citizensnpcs.api.util.DataKey;
import org.bukkit.event.EventHandler;

/**
 * Citizens trait allowing NPCs to trigger BasicParkour actions when interacted with.
 */
@TraitName("basicparkour")
public class CitizensParkourTrait extends Trait {

    private String action = "JOIN_COURSE";
    private String courseId = "";

    public CitizensParkourTrait() {
        super("basicparkour");
    }

    @Override
    public void load(DataKey key) {
        action = key.getString("action", "JOIN_COURSE");
        courseId = key.getString("course", "");
    }

    @Override
    public void save(DataKey key) {
        key.setString("action", action);
        key.setString("course", courseId);
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getCourseId() {
        return courseId;
    }

    public void setCourseId(String courseId) {
        this.courseId = courseId;
    }

    @EventHandler
    public void onRightClick(NPCRightClickEvent event) {
        if (event.getNPC() != this.getNPC()) return;
        handleClick(event.getClicker());
    }

    @EventHandler
    public void onLeftClick(NPCLeftClickEvent event) {
        if (event.getNPC() != this.getNPC()) return;
        handleClick(event.getClicker());
    }

    private void handleClick(org.bukkit.entity.Player player) {
        NpcManager manager = BasicParkourPlugin.getPlugin(BasicParkourPlugin.class).getNpcManager();
        if (manager == null) return;
        try {
            NpcAction act = NpcAction.valueOf(action.toUpperCase());
            manager.executeAction(player, act, courseId);
        } catch (IllegalArgumentException ignored) {}
    }
}
