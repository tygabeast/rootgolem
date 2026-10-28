package com.tygabeast.rootgolem.entity;

import com.tygabeast.rootgolem.RootGolemConfig;
import com.tygabeast.rootgolem.RootGolemMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;

/** Small terrain helpers shared by the golem's goals. None of them load chunks. */
final class Terrain {
    static final int NONE = Integer.MIN_VALUE;
    /** Plants the golem clears out of a planting spot (short and tall grass, ferns, dead bushes by default). */
    static final TagKey<Block> CLEARABLE = TagKey.create(Registries.BLOCK, RootGolemMod.id("clearable_plants"));

    /** Ground the golem keeps a gap from (dirt path and gravel by default). */
    static final TagKey<Block> KEEP_CLEAR = TagKey.create(Registries.BLOCK, RootGolemMod.id("keep_clear"));

    private Terrain() {}

    /**
     * Y of the first free block above the ground at (x, z) within {@code vr} of baseY, or NONE.
     * Columns in chunks that don't tick entities count as NONE: a golem that walked there would
     * freeze until a player came close, so it never targets them.
     */
    static int groundY(ServerLevel level, int x, int z, int baseY, int vr, BlockPos.MutableBlockPos cursor) {
        if (!level.isPositionEntityTicking(cursor.set(x, baseY, z))) return NONE;
        if (!level.dimensionType().hasCeiling()) {
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            return Math.abs(y - baseY) <= vr ? y : NONE;
        }
        // Nether-like dimensions: the heightmap points at the roof, so scan a short column instead.
        for (int y = baseY + vr; y >= baseY - vr; y--) {
            cursor.set(x, y, z);
            if (!isFree(level.getBlockState(cursor))) continue;
            cursor.setY(y - 1);
            BlockState below = level.getBlockState(cursor);
            if (!below.isAir() && !isFree(below)) return y;
        }
        return NONE;
    }

    /** Air, or something a block can be placed into directly (short grass, snow layer...). */
    static boolean isFree(BlockState state) {
        return state.isAir() || (state.canBeReplaced() && state.getFluidState().isEmpty()
                && !state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF));
    }

    /** A plant the golem breaks to make room, when clearing is enabled. */
    static boolean isClearable(BlockState state) {
        return !state.isAir() && state.is(CLEARABLE) && RootGolemConfig.CLEAR_PLANTS.get();
    }

    /** Free now, or free once the golem clears a plant out of it. */
    static boolean isPlantableSpace(BlockState state) {
        return isFree(state) || isClearable(state);
    }
}
