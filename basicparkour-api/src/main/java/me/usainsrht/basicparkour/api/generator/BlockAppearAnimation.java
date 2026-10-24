package me.usainsrht.basicparkour.api.generator;

/**
 * Defines the visual animation used when new parkour platforms/blocks appear
 * during an endless or sliding-window parkour run.
 */
public enum BlockAppearAnimation {

    /**
     * No animation. The block is placed instantly in the world.
     */
    NONE,

    /**
     * Default slide animation: A block display entity is spawned at the previous platform
     * location and glides smoothly to its final target position before switching to a real block.
     */
    DEFAULT,

    /**
     * Grow animation: A block display entity spawns at the final target location at zero scale,
     * expands smoothly to a full block, and then switches to a real block.
     */
    GROW
}
