package me.usainsrht.basicparkour.api.generator;

/**
 * Determines where in the world a generated parkour run is built.
 */
public enum EntryMode {

    /**
     * Each run is placed at a randomly selected, non-overlapping position inside
     * a dedicated world. The world name and search radius are configured in the
     * generator template YAML.
     */
    DEDICATED_WORLD,

    /**
     * Each run starts at a specific admin-placed marker location. The marker is
     * registered via {@code /basicparkour setentry <templateId>} while standing
     * at the desired start point. Useful for placing generated parkours at fixed
     * positions in a lobby world.
     */
    MARKER
}
