package me.usainsrht.basicparkour.core.hologram;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HologramConfigTest {

    @Test
    void testDefaultsWhenSectionNull() {
        HologramConfig config = new HologramConfig(null);

        assertTrue(config.isEnabled());
        assertEquals("auto", config.getProvider());
        assertEquals(60L, config.getRefreshIntervalSeconds());
        assertTrue(config.isUpdateOnRecord());
        assertFalse(config.isUpdateOnCompletion());
        assertEquals(10, config.getDefaultLimit());

        assertFalse(config.getLeaderboardHeader().isEmpty());
        assertNotNull(config.getLeaderboardEntryFormat());
        assertNotNull(config.getLeaderboardEntryFormatBlocks());
        assertNotNull(config.getLeaderboardEmpty());
        assertFalse(config.getLeaderboardFooter().isEmpty());
        assertNull(config.getLeaderboardLines());

        assertFalse(config.getCourseInfoHeader().isEmpty());
        assertNotNull(config.getCourseInfoCheckpoints());
        assertNotNull(config.getCourseInfoRecord());
        assertNotNull(config.getCourseInfoRecordBlocks());
        assertNotNull(config.getCourseInfoRecordNone());
        assertFalse(config.getCourseInfoFooter().isEmpty());
        assertNull(config.getCourseInfoLines());
    }

    @Test
    void testCustomConfiguration() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("hologram.enabled", false);
        yaml.set("hologram.provider", "FancyHolograms");
        yaml.set("hologram.update.interval-seconds", 30L);
        yaml.set("hologram.update.on-record", false);
        yaml.set("hologram.update.on-completion", true);
        yaml.set("hologram.default-limit", 5);

        yaml.set("hologram.leaderboard.header", List.of("Line 1", "Line 2"));
        yaml.set("hologram.leaderboard.entry-format", "#{rank} {player} {time}");
        yaml.set("hologram.leaderboard.empty", "No records!");
        yaml.set("hologram.leaderboard.footer", List.of("Footer 1"));
        yaml.set("hologram.leaderboard.lines", List.of("Title", "{entries}", "End"));

        HologramConfig config = new HologramConfig(yaml.getConfigurationSection("hologram"));

        assertFalse(config.isEnabled());
        assertEquals("FancyHolograms", config.getProvider());
        assertEquals(30L, config.getRefreshIntervalSeconds());
        assertFalse(config.isUpdateOnRecord());
        assertTrue(config.isUpdateOnCompletion());
        assertEquals(5, config.getDefaultLimit());

        assertEquals(List.of("Line 1", "Line 2"), config.getLeaderboardHeader());
        assertEquals("#{rank} {player} {time}", config.getLeaderboardEntryFormat());
        assertEquals("No records!", config.getLeaderboardEmpty());
        assertEquals(List.of("Footer 1"), config.getLeaderboardFooter());
        assertEquals(List.of("Title", "{entries}", "End"), config.getLeaderboardLines());
    }

    @Test
    void testBackwardCompatibility() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("hologram.provider", "DecentHolograms");
        yaml.set("hologram.refresh-interval-seconds", 45L);
        yaml.set("hologram.update-on-record", true);

        HologramConfig config = new HologramConfig(yaml.getConfigurationSection("hologram"));

        assertTrue(config.isEnabled());
        assertEquals("DecentHolograms", config.getProvider());
        assertEquals(45L, config.getRefreshIntervalSeconds());
        assertTrue(config.isUpdateOnRecord());
    }
}
