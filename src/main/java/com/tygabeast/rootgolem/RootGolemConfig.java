package com.tygabeast.rootgolem;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server (per-world) settings. Found in serverconfig/rootgolem-server.toml inside each world. */
public final class RootGolemConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue PLANT_INTERVAL;
    public static final ModConfigSpec.IntValue SEARCH_RADIUS;
    public static final ModConfigSpec.IntValue VERTICAL_RANGE;
    public static final ModConfigSpec.IntValue SEARCH_ATTEMPTS;
    public static final ModConfigSpec.DoubleValue TWO_BY_TWO_CHANCE;
    public static final ModConfigSpec.IntValue TREE_SPACING;
    public static final ModConfigSpec.BooleanValue CONSUME_SAPLINGS;
    public static final ModConfigSpec.BooleanValue CLEAR_PLANTS;
    public static final ModConfigSpec.IntValue PATH_CLEARANCE;
    public static final ModConfigSpec.BooleanValue COLLECT_SAPLINGS;
    public static final ModConfigSpec.BooleanValue OWNER_ONLY;
    public static final ModConfigSpec.IntValue MAX_EDGE_LENGTH;
    public static final ModConfigSpec.IntValue MAX_BOUNDARY_SIZE;
    public static final ModConfigSpec.IntValue MAX_MARKERS;
    public static final ModConfigSpec.BooleanValue ALLOW_BUILDING;
    public static final ModConfigSpec.BooleanValue DEBUG_LOG;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("planting");
        PLANT_INTERVAL = b.comment("Ticks between planting attempts (20 ticks = 1 second). Lower = faster planting, more CPU.")
                .defineInRange("plantInterval", 80, 20, 12000);
        SEARCH_RADIUS = b.comment("Each attempt looks for a spot inside the golem's boundary within this many blocks of the golem.",
                        "The golem roams its whole boundary, so over time it covers all of it; this only keeps each search and walk short.")
                .defineInRange("searchRadius", 16, 4, 48);
        VERTICAL_RANGE = b.comment("How far above or below the golem a planting spot may be.")
                .defineInRange("verticalRange", 4, 1, 16);
        SEARCH_ATTEMPTS = b.comment("Random spots tested per sapling type on each attempt. Higher finds spots more reliably but costs more.")
                .defineInRange("searchAttempts", 16, 4, 64);
        TWO_BY_TWO_CHANCE = b.comment("Chance to plant a 2x2 square when the sapling supports both single and 2x2 trees (spruce, jungle) and the golem holds at least 4.",
                        "Saplings that only grow as 2x2 (dark oak) always use 2x2.")
                .defineInRange("twoByTwoChance", 0.5D, 0.0D, 1.0D);
        TREE_SPACING = b.comment("Default empty blocks kept between a new sapling and other saplings or tree trunks.",
                        "Each boundary can override this with a stick (click one of its markers).")
                .defineInRange("treeSpacing", 8, 0, 16);
        CONSUME_SAPLINGS = b.comment("Whether planting uses up the sapling.")
                .define("consumeSaplings", true);
        CLEAR_PLANTS = b.comment("Whether the golem breaks grass, ferns and other plants in #rootgolem:clearable_plants that are in a planting spot.",
                        "Broken plants drop nothing.")
                .define("clearPlants", true);
        PATH_CLEARANCE = b.comment("Blocks kept between a new sapling and path or gravel ground (#rootgolem:keep_clear). 0 = off.")
                .defineInRange("pathClearance", 2, 0, 8);
        COLLECT_SAPLINGS = b.comment("Whether golems pick up saplings lying on the ground inside their boundary.")
                .define("collectSaplings", true);
        b.pop();
        b.push("boundary");
        MAX_EDGE_LENGTH = b.comment("Maximum distance between two connected boundary markers.")
                .defineInRange("maxEdgeLength", 64, 4, 128);
        MAX_BOUNDARY_SIZE = b.comment("Maximum width or depth of a whole boundary, in blocks.")
                .defineInRange("maxBoundarySize", 128, 8, 256);
        MAX_MARKERS = b.comment("Maximum markers in one boundary.")
                .defineInRange("maxMarkers", 64, 3, 128);
        OWNER_ONLY = b.comment("Only a boundary's owner (and operators) may extend, close, re-space or break it, or assign and manage its golems.",
                        "Unowned boundaries (made by commands or before 1.3.0) are claimed by the first player who edits them.")
                .define("ownerOnly", true);
        b.pop();
        b.push("golem");
        ALLOW_BUILDING = b.comment("Allow building a Root Golem from rooted dirt in the iron golem T shape with a carved pumpkin or jack o'lantern on top.")
                .define("allowBuilding", true);
        DEBUG_LOG = b.comment("Log every sapling the golem plants to the server log. For testing only.")
                .define("debugLog", false);
        b.pop();
        SPEC = b.build();
    }

    private RootGolemConfig() {}
}
