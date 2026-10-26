package me.usainsrht.basicparkour.core.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ConfigBooleanParsingTest {

    @Test
    public void testGetBooleanOrNullKeyPresent() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("prevent-damage", true);
        config.set("cancel-knockback", false);

        Boolean preventDamage = CourseLoader.getBooleanOrNull(config, "prevent-damage");
        assertNotNull(preventDamage);
        assertTrue(preventDamage);

        Boolean knockback = CourseLoader.getBooleanOrNull(config, "prevent-knockback", "cancel-knockback");
        assertNotNull(knockback);
        assertFalse(knockback);
    }

    @Test
    public void testGetBooleanOrNullKeyAbsentReturnsNullWithoutNPE() {
        YamlConfiguration config = new YamlConfiguration();
        // Neither key is set - this previously triggered:
        // java.lang.NullPointerException: Cannot invoke "java.lang.Boolean.booleanValue()"
        // due to Java ternary operator unboxing rules with primitive boolean and null.
        Boolean preventKnockback = CourseLoader.getBooleanOrNull(config, "prevent-knockback", "cancel-knockback");
        assertNull(preventKnockback);

        Boolean preventProjectile = CourseLoader.getBooleanOrNull(config,
            "prevent-projectile-launch", "disable-projectile-launch", "disable-ender-pearls");
        assertNull(preventProjectile);

        Boolean generatorFallDamage = GeneratorTemplateLoader.getBooleanOrNull(config,
            "fall-damage-fails-session", "fall-damage-fails");
        assertNull(generatorFallDamage);
    }
}
