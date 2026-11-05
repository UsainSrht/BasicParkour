package me.usainsrht.basicparkour.core.display;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Offline-player formatting: unresolved placeholder tags vanish, real MiniMessage tags survive. */
class PlayerNameResolverTagTest {

    private static String plain(String format) {
        Component c = MiniMessage.miniMessage().deserialize(format,
            Placeholder.unparsed("player", "Steve"), PlayerNameResolver.STRIP_UNKNOWN_PLACEHOLDERS);
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    @Test
    void unresolvedPlaceholderTagsAreStripped() {
        assertEquals("Steve", plain("<luckperms_prefix><player>"));
        assertEquals("Steve", plain("<luckperms_prefix:default><player>"));
    }

    @Test
    void namedColorsWithUnderscoreStillWork() {
        assertEquals("Steve", plain("<dark_red><luckperms_prefix><player></dark_red>"));
        TagResolver r = PlayerNameResolver.STRIP_UNKNOWN_PLACEHOLDERS;
        assertEquals(false, r.has("dark_red"));
        assertEquals(false, r.has("lang_or"));
        assertEquals(true, r.has("luckperms_prefix"));
    }
}
