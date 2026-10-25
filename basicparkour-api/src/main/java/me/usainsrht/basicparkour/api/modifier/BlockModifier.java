package me.usainsrht.basicparkour.api.modifier;

import me.usainsrht.basicparkour.api.session.ParkourSession;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * An SPI that allows third-party plugins to define custom block interactions
 * within a parkour course.
 *
 * <h2>Registration</h2>
 * <pre>{@code
 * BasicParkourAPI.get().getModifierRegistry().register(new MyCustomModifier());
 * }</pre>
 *
 * <h2>Invocation</h2>
 * The Basic Parkour engine calls {@link #apply(Player, ParkourSession)} on the player's
 * entity scheduler thread when the player's feet enter a block that {@link #matches(Block)}.
 */
public interface BlockModifier {

    /**
     * The unique {@link NamespacedKey} that identifies this modifier.
     * Used in course YAML files via {@code modifiers[].type}.
     *
     * @return namespaced key
     */
    @NotNull
    NamespacedKey getKey();

    /**
     * Returns {@code true} if this modifier should activate for the given block.
     *
     * <p>The default implementation matches the block's material against
     * {@link #getDefaultMaterial()}. Override for custom matching logic.</p>
     *
     * @param block the block the player is standing on or inside
     * @return whether this modifier applies
     */
    boolean matches(@NotNull Block block);

    /**
     * Applies this modifier's effect to the player within the context of their session.
     * This method is always called on the entity's owning region thread.
     *
     * @param player  the affected player
     * @param session the player's active session
     */
    void apply(@NotNull Player player, @NotNull ParkourSession session);

    /**
     * Returns a human-readable description of this modifier for admin display.
     *
     * @return display name string
     */
    @NotNull
    default String getDisplayName() {
        return getKey().toString();
    }

    /**
     * Creates a new block modifier matching a specific material.
     *
     * @param key      the unique namespaced key
     * @param material the material to trigger on
     * @param action   the effect applied to player and session
     * @return a new BlockModifier
     */
    @NotNull
    static BlockModifier of(@NotNull NamespacedKey key,
                            @NotNull org.bukkit.Material material,
                            @NotNull java.util.function.BiConsumer<Player, ParkourSession> action) {
        return builder(key)
            .matchMaterial(material)
            .onApply(action)
            .build();
    }

    /**
     * Creates a new block modifier matching a custom block predicate.
     *
     * @param key       the unique namespaced key
     * @param predicate block condition
     * @param action    the effect applied to player and session
     * @return a new BlockModifier
     */
    @NotNull
    static BlockModifier of(@NotNull NamespacedKey key,
                            @NotNull java.util.function.Predicate<Block> predicate,
                            @NotNull java.util.function.BiConsumer<Player, ParkourSession> action) {
        return builder(key)
            .matching(predicate)
            .onApply(action)
            .build();
    }

    /**
     * Creates a builder for fluent block modifier definition.
     *
     * @param key the unique namespaced key
     * @return a new Builder
     */
    @NotNull
    static Builder builder(@NotNull NamespacedKey key) {
        return new Builder(key);
    }

    /**
     * Fluent builder for {@link BlockModifier}.
     */
    final class Builder {
        private final NamespacedKey key;
        private String displayName;
        private java.util.function.Predicate<Block> predicate;
        private java.util.function.BiConsumer<Player, ParkourSession> action;

        public Builder(@NotNull NamespacedKey key) {
            this.key = java.util.Objects.requireNonNull(key, "key cannot be null");
            this.displayName = key.toString();
        }

        @NotNull
        public Builder displayName(@NotNull String displayName) {
            this.displayName = displayName;
            return this;
        }

        @NotNull
        public Builder matchMaterial(@NotNull org.bukkit.Material material) {
            java.util.Objects.requireNonNull(material, "material cannot be null");
            return matching(b -> b.getType() == material);
        }

        @NotNull
        public Builder matchMaterials(@NotNull org.bukkit.Material... materials) {
            java.util.Set<org.bukkit.Material> set = java.util.Set.of(materials);
            return matching(b -> set.contains(b.getType()));
        }

        @NotNull
        public Builder matchMaterials(@NotNull java.util.Collection<org.bukkit.Material> materials) {
            java.util.Set<org.bukkit.Material> set = java.util.Set.copyOf(materials);
            return matching(b -> set.contains(b.getType()));
        }

        @NotNull
        public Builder matching(@NotNull java.util.function.Predicate<Block> predicate) {
            this.predicate = java.util.Objects.requireNonNull(predicate, "predicate cannot be null");
            return this;
        }

        @NotNull
        public Builder onApply(@NotNull java.util.function.BiConsumer<Player, ParkourSession> action) {
            this.action = java.util.Objects.requireNonNull(action, "action cannot be null");
            return this;
        }

        @NotNull
        public BlockModifier build() {
            if (predicate == null) {
                throw new IllegalStateException("BlockModifier matching predicate/material must be defined.");
            }
            if (action == null) {
                throw new IllegalStateException("BlockModifier onApply action must be defined.");
            }

            final NamespacedKey finalKey = key;
            final String finalDisplayName = displayName;
            final java.util.function.Predicate<Block> finalPredicate = predicate;
            final java.util.function.BiConsumer<Player, ParkourSession> finalAction = action;

            return new BlockModifier() {
                @Override
                @NotNull
                public NamespacedKey getKey() {
                    return finalKey;
                }

                @Override
                public boolean matches(@NotNull Block block) {
                    return finalPredicate.test(block);
                }

                @Override
                public void apply(@NotNull Player player, @NotNull ParkourSession session) {
                    finalAction.accept(player, session);
                }

                @Override
                @NotNull
                public String getDisplayName() {
                    return finalDisplayName;
                }
            };
        }
    }
}
