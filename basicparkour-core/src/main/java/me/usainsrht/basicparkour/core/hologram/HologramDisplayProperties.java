package me.usainsrht.basicparkour.core.hologram;

import org.bukkit.Color;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Text-display look &amp; feel of a hologram (billboard, alignment, background, ...).
 *
 * <p>Every property is optional; {@code null} means "not specified / leave the provider's default".
 * Instances are immutable. A per-hologram instance is layered over the global defaults from
 * {@code hologram.display} in config.yml using {@link #overlay(HologramDisplayProperties)}.</p>
 *
 * <p>Only providers backed by real text-display entities (FancyHolograms) can apply these.</p>
 */
public final class HologramDisplayProperties {

    public static final HologramDisplayProperties EMPTY =
        new HologramDisplayProperties(null, null, null, null, null, null, null, null, null, null);

    private final Display.Billboard billboard;
    private final TextDisplay.TextAlignment alignment;
    /** "default" (vanilla translucent black), "transparent", or a hex colour "#RRGGBB" / "#AARRGGBB". */
    private final String background;
    private final Boolean textShadow;
    private final Boolean seeThrough;
    private final Float scale;
    private final Integer blockLight;
    private final Integer skyLight;
    private final Float shadowRadius;
    private final Float shadowStrength;

    public HologramDisplayProperties(@Nullable Display.Billboard billboard,
                                     @Nullable TextDisplay.TextAlignment alignment,
                                     @Nullable String background,
                                     @Nullable Boolean textShadow,
                                     @Nullable Boolean seeThrough,
                                     @Nullable Float scale,
                                     @Nullable Integer blockLight,
                                     @Nullable Integer skyLight,
                                     @Nullable Float shadowRadius,
                                     @Nullable Float shadowStrength) {
        this.billboard = billboard;
        this.alignment = alignment;
        this.background = background;
        this.textShadow = textShadow;
        this.seeThrough = seeThrough;
        this.scale = scale;
        this.blockLight = blockLight;
        this.skyLight = skyLight;
        this.shadowRadius = shadowRadius;
        this.shadowStrength = shadowStrength;
    }

    // ── Accessors ──────────────────────────────────────────────────────────

    public @Nullable Display.Billboard billboard() { return billboard; }
    public @Nullable TextDisplay.TextAlignment alignment() { return alignment; }
    public @Nullable String background() { return background; }
    public @Nullable Boolean textShadow() { return textShadow; }
    public @Nullable Boolean seeThrough() { return seeThrough; }
    public @Nullable Float scale() { return scale; }
    public @Nullable Integer blockLight() { return blockLight; }
    public @Nullable Integer skyLight() { return skyLight; }
    public @Nullable Float shadowRadius() { return shadowRadius; }
    public @Nullable Float shadowStrength() { return shadowStrength; }

    public boolean isEmpty() {
        return billboard == null && alignment == null && background == null && textShadow == null
            && seeThrough == null && scale == null && blockLight == null && skyLight == null
            && shadowRadius == null && shadowStrength == null;
    }

    /** Brightness override is applied only when both block and sky light are given. */
    public boolean hasBrightness() {
        return blockLight != null && skyLight != null;
    }

    /**
     * Resolves {@link #background()} to a colour. Returns {@code null} for "default" or unset
     * (vanilla background), a fully transparent colour for "transparent".
     */
    public @Nullable Color backgroundColor() {
        if (background == null) return null;
        String v = background.trim();
        if (v.equalsIgnoreCase("default") || v.isEmpty()) return null;
        if (v.equalsIgnoreCase("transparent") || v.equalsIgnoreCase("none")) return Color.fromARGB(0, 0, 0, 0);
        try {
            String hex = v.startsWith("#") ? v.substring(1) : v;
            if (hex.length() == 6) {
                return Color.fromARGB(0xFF, Integer.parseInt(hex.substring(0, 2), 16),
                    Integer.parseInt(hex.substring(2, 4), 16), Integer.parseInt(hex.substring(4, 6), 16));
            }
            if (hex.length() == 8) {
                return Color.fromARGB(Integer.parseInt(hex.substring(0, 2), 16),
                    Integer.parseInt(hex.substring(2, 4), 16), Integer.parseInt(hex.substring(4, 6), 16),
                    Integer.parseInt(hex.substring(6, 8), 16));
            }
        } catch (NumberFormatException ignored) {
            // fall through to default
        }
        return null;
    }

    // ── Merging ────────────────────────────────────────────────────────────

    /** Returns this instance with every property that {@code override} specifies replaced by it. */
    public @NotNull HologramDisplayProperties overlay(@Nullable HologramDisplayProperties override) {
        if (override == null || override.isEmpty()) return this;
        return new HologramDisplayProperties(
            override.billboard != null ? override.billboard : billboard,
            override.alignment != null ? override.alignment : alignment,
            override.background != null ? override.background : background,
            override.textShadow != null ? override.textShadow : textShadow,
            override.seeThrough != null ? override.seeThrough : seeThrough,
            override.scale != null ? override.scale : scale,
            override.blockLight != null ? override.blockLight : blockLight,
            override.skyLight != null ? override.skyLight : skyLight,
            override.shadowRadius != null ? override.shadowRadius : shadowRadius,
            override.shadowStrength != null ? override.shadowStrength : shadowStrength);
    }

    // ── (De)serialisation ──────────────────────────────────────────────────

    public static @NotNull HologramDisplayProperties fromSection(@Nullable ConfigurationSection sec) {
        if (sec == null) return EMPTY;
        ConfigurationSection brightness = sec.getConfigurationSection("brightness");
        return new HologramDisplayProperties(
            parseEnum(Display.Billboard.class, sec.getString("billboard")),
            parseEnum(TextDisplay.TextAlignment.class, sec.getString("alignment", sec.getString("text-alignment"))),
            sec.isSet("background") ? String.valueOf(sec.get("background")) : null,
            sec.isSet("text-shadow") ? sec.getBoolean("text-shadow") : null,
            sec.isSet("see-through") ? sec.getBoolean("see-through") : null,
            sec.isSet("scale") ? (float) Math.max(0.01, sec.getDouble("scale")) : null,
            brightness != null && brightness.isSet("block") ? clampLight(brightness.getInt("block")) : null,
            brightness != null && brightness.isSet("sky") ? clampLight(brightness.getInt("sky")) : null,
            sec.isSet("shadow-radius") ? (float) Math.max(0, sec.getDouble("shadow-radius")) : null,
            sec.isSet("shadow-strength") ? (float) Math.max(0, sec.getDouble("shadow-strength")) : null);
    }

    public void serialize(@NotNull ConfigurationSection sec) {
        if (billboard != null) sec.set("billboard", billboard.name());
        if (alignment != null) sec.set("alignment", alignment.name());
        if (background != null) sec.set("background", background);
        if (textShadow != null) sec.set("text-shadow", textShadow);
        if (seeThrough != null) sec.set("see-through", seeThrough);
        if (scale != null) sec.set("scale", scale);
        if (blockLight != null) sec.set("brightness.block", blockLight);
        if (skyLight != null) sec.set("brightness.sky", skyLight);
        if (shadowRadius != null) sec.set("shadow-radius", shadowRadius);
        if (shadowStrength != null) sec.set("shadow-strength", shadowStrength);
    }

    private static int clampLight(int v) {
        return Math.max(0, Math.min(15, v));
    }

    private static <E extends Enum<E>> @Nullable E parseEnum(@NotNull Class<E> type, @Nullable String value) {
        if (value == null) return null;
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
