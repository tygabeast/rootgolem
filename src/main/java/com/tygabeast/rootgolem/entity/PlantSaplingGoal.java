package com.tygabeast.rootgolem.entity;

import com.tygabeast.rootgolem.RootGolemConfig;
import com.tygabeast.rootgolem.RootGolemMod;
import com.tygabeast.rootgolem.RootGolemSounds;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import com.tygabeast.rootgolem.boundary.Boundary;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Picks a random sapling from the golem's inventory, finds a free spot inside the golem's boundary,
 * walks there and plants it (one sapling, or four in a 2x2 square).
 *
 * Cost: the search runs once per plantInterval (default every 4 seconds, jittered so many golems do
 * not search on the same tick), tests a bounded number of random spots with the cheapest checks
 * first, never loads chunks, and reads one heightmap value plus two blocks per column for the
 * tree-spacing check. Columns found to hold a tree are remembered for the rest of the attempt, so
 * nearby candidates are rejected without re-scanning.
 */
final class PlantSaplingGoal extends Goal {
    static final TagKey<Block> UNPLANTABLE_GROUND = TagKey.create(Registries.BLOCK, RootGolemMod.id("unplantable_ground"));
    private static final int MAX_TYPES_PER_ATTEMPT = 3;
    private static final int MAX_RUN_TICKS = 400;
    private static final double REACH_SQR = 3.0D * 3.0D;
    private static final int MAX_BLOCKERS = 32;
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private final RootGolem golem;
    private final double speed;
    private final int[] slotScratch = new int[RootGolem.INVENTORY_SIZE];
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private final int[] blockerX = new int[MAX_BLOCKERS];
    private final int[] blockerZ = new int[MAX_BLOCKERS];
    private int blockerCount;

    private int nextAttemptTick;
    private boolean hadSaplings;
    private int runTicks;
    private Item plantItem;
    private Boundary boundary;
    private BlockPos origin;      // single spot, or the -x/-z corner of a 2x2
    private boolean big;

    PlantSaplingGoal(RootGolem golem, double speed) {
        this.golem = golem;
        this.speed = speed;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (golem.tickCount < nextAttemptTick || golem.isBeingViewed()) return false;
        int interval = RootGolemConfig.PLANT_INTERVAL.get();
        nextAttemptTick = golem.tickCount + interval + golem.getRandom().nextInt(interval / 4 + 1);
        if (!(golem.level() instanceof ServerLevel level)) return false;
        boundary = golem.boundary();
        if (boundary == null) return false;
        boolean found = findPlan(level);
        if (hadSaplings) golem.onPlantSearch(found);
        return found;
    }

    @Override
    public boolean canContinueToUse() {
        return plantItem != null && runTicks < MAX_RUN_TICKS && !golem.isBeingViewed();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        runTicks = 0;
        moveToTarget();
    }

    @Override
    public void stop() {
        plantItem = null;
        origin = null;
        boundary = null;
        golem.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (plantItem == null) return; // finished; see CollectSaplingsGoal.tick
        runTicks++;
        double cx = origin.getX() + (big ? 1.0D : 0.5D);
        double cz = origin.getZ() + (big ? 1.0D : 0.5D);
        golem.getLookControl().setLookAt(cx, origin.getY() + 0.5D, cz);

        if (golem.distanceToSqr(cx, origin.getY(), cz) <= REACH_SQR) {
            golem.getNavigation().stop();
            plant((ServerLevel) golem.level());
            plantItem = null; // ends the goal
        } else if (runTicks % 20 == 0 && golem.getNavigation().isDone()) {
            moveToTarget(); // repath at most once a second if we got stuck
        }
    }

    private void moveToTarget() {
        golem.getNavigation().moveTo(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D, speed);
    }

    // ---- choosing what and where --------------------------------------------------------------

    private boolean findPlan(ServerLevel level) {
        SimpleContainer inv = golem.getInventory();
        int count = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && Plantables.mode(s.getItem()) != Plantables.Mode.NONE) slotScratch[count++] = i;
        }
        hadSaplings = count > 0;
        if (count == 0) return false;

        RandomSource rand = golem.getRandom();
        blockerCount = 0;

        // Random sapling. If that type has nowhere to go, try a couple of others.
        for (int t = 0; t < MAX_TYPES_PER_ATTEMPT && count > 0; t++) {
            int pick = rand.nextInt(count);
            Item item = inv.getItem(slotScratch[pick]).getItem();
            slotScratch[pick] = slotScratch[--count];

            Plantables.Mode mode = Plantables.mode(item);
            boolean enough = inv.countItem(item) >= 4;
            boolean wantBig;
            if (mode == Plantables.Mode.ONLY_2X2) {
                if (!enough) continue;
                wantBig = true;
            } else {
                wantBig = mode == Plantables.Mode.SINGLE_OR_2X2 && enough
                        && rand.nextDouble() < RootGolemConfig.TWO_BY_TWO_CHANCE.get();
            }

            ItemStack probe = new ItemStack(item);
            if (search(level, probe, wantBig)) return true;
            if (wantBig && mode != Plantables.Mode.ONLY_2X2 && search(level, probe, false)) return true;
        }
        return false;
    }

    private boolean search(ServerLevel level, ItemStack probe, boolean wantBig) {
        RandomSource rand = golem.getRandom();
        int r = RootGolemConfig.SEARCH_RADIUS.get();
        int vr = RootGolemConfig.VERTICAL_RANGE.get();
        int spacing = boundary.spacing(); // per-boundary preset, or the server default
        BlockPos center = golem.blockPosition();
        int size = wantBig ? 2 : 1;

        // Sample only where the boundary and the golem's search square overlap.
        int minX = Math.max(boundary.minX(), center.getX() - r);
        int maxX = Math.min(boundary.maxX(), center.getX() + r) - (size - 1);
        int minZ = Math.max(boundary.minZ(), center.getZ() - r);
        int maxZ = Math.min(boundary.maxZ(), center.getZ() + r) - (size - 1);
        if (minX > maxX || minZ > maxZ) return false;

        for (int i = RootGolemConfig.SEARCH_ATTEMPTS.get(); i > 0; i--) {
            int x = minX + rand.nextInt(maxX - minX + 1);
            int z = minZ + rand.nextInt(maxZ - minZ + 1);
            if (nearKnownBlocker(x, z, size, spacing)) continue;
            int y = Terrain.groundY(level, x, z, center.getY(), vr, cursor);
            if (y == Terrain.NONE) continue;
            BlockPos pos = new BlockPos(x, y, z);

            boolean ok = canPlantAt(level, probe, pos);
            if (ok && wantBig) {
                ok = canPlantAt(level, probe, pos.east())
                        && canPlantAt(level, probe, pos.south())
                        && canPlantAt(level, probe, pos.south().east());
            }
            if (ok && clearOfPaths(level, pos, size) && hasRoom(level, pos, size, spacing)) {
                plantItem = probe.getItem();
                origin = pos;
                big = wantBig;
                return true;
            }
        }
        return false;
    }

    /** Cheapest checks first: boundary, free space, ground, then the block's own placement rules. */
    private boolean canPlantAt(ServerLevel level, ItemStack probe, BlockPos pos) {
        if (!boundary.contains(pos.getX(), pos.getZ())) return false;
        if (!level.isLoaded(pos)) return false;
        if (!Terrain.isPlantableSpace(level.getBlockState(pos))) return false;
        if (!Terrain.isPlantableSpace(level.getBlockState(pos.above()))) return false;
        if (level.getBlockState(pos.below()).is(UNPLANTABLE_GROUND)) return false;

        BlockItem blockItem = (BlockItem) probe.getItem();
        BlockState state = blockItem.getBlock().getStateForPlacement(context(level, probe, pos));
        return state != null && state.canSurvive(level, pos);
    }

    // ---- tree spacing -------------------------------------------------------------------------

    private boolean nearKnownBlocker(int x, int z, int size, int spacing) {
        for (int i = 0; i < blockerCount; i++) {
            int bx = blockerX[i], bz = blockerZ[i];
            if (bx >= x - spacing && bx <= x + size - 1 + spacing && bz >= z - spacing && bz <= z + size - 1 + spacing) return true;
        }
        return false;
    }

    /**
     * Keeps {@code spacing} empty blocks between the new sapling(s) and any sapling or trunk.
     * Per column: the heightmap top (where a sapling would sit) and the block below it (a trunk's top log),
     * which also catches trees on slopes above or below the golem.
     */
    private boolean hasRoom(ServerLevel level, BlockPos origin, int size, int spacing) {
        if (spacing <= 0) return true;
        int minX = origin.getX() - spacing, maxX = origin.getX() + size - 1 + spacing;
        int minZ = origin.getZ() - spacing, maxZ = origin.getZ() + size - 1 + spacing;
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                if (!level.hasChunk(cx, cz)) return false; // never load chunks; try elsewhere
            }
        }
        boolean ceiling = level.dimensionType().hasCeiling();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                boolean blocked = false;
                if (!ceiling) {
                    int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    blocked = isTree(level.getBlockState(cursor.set(x, top, z)))
                            || isTree(level.getBlockState(cursor.set(x, top - 1, z)));
                } else {
                    for (int dy = -1; dy <= 1 && !blocked; dy++) {
                        blocked = isTree(level.getBlockState(cursor.set(x, origin.getY() + dy, z)));
                    }
                }
                if (blocked) {
                    if (blockerCount < MAX_BLOCKERS) {
                        blockerX[blockerCount] = x;
                        blockerZ[blockerCount++] = z;
                    }
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Keeps {@code pathClearance} blocks between a new sapling and path or gravel ground
     * (#rootgolem:keep_clear), so trunks and 2x2 trees don't spill onto walkways.
     * At most (2 * clearance + size)^2 columns, one heightmap read and one block each.
     */
    private boolean clearOfPaths(ServerLevel level, BlockPos origin, int size) {
        int c = RootGolemConfig.PATH_CLEARANCE.get();
        if (c <= 0) return true;
        boolean ceiling = level.dimensionType().hasCeiling();
        for (int x = origin.getX() - c; x <= origin.getX() + size - 1 + c; x++) {
            for (int z = origin.getZ() - c; z <= origin.getZ() + size - 1 + c; z++) {
                if (!level.hasChunk(x >> 4, z >> 4)) return false;
                int groundY = ceiling ? origin.getY() - 1 : level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                if (level.getBlockState(cursor.set(x, groundY, z)).is(Terrain.KEEP_CLEAR)) return false;
            }
        }
        return true;
    }

    private static boolean isTree(BlockState s) {
        return s.is(BlockTags.SAPLINGS) || s.is(BlockTags.LOGS) || s.getBlock() instanceof SaplingBlock;
    }

    private static BlockPlaceContext context(ServerLevel level, ItemStack stack, BlockPos pos) {
        return new BlockPlaceContext(level, null, InteractionHand.MAIN_HAND, stack,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }

    // ---- planting -----------------------------------------------------------------------------

    private void plant(ServerLevel level) {
        SimpleContainer inv = golem.getInventory();
        int needed = big ? 4 : 1;
        if (inv.countItem(plantItem) < needed) return; // a player took some out on the way
        boolean consume = RootGolemConfig.CONSUME_SAPLINGS.get();
        ItemStack probe = new ItemStack(plantItem);

        BlockPos[] spots = big
                ? new BlockPos[]{origin, origin.east(), origin.south(), origin.south().east()}
                : new BlockPos[]{origin};
        for (BlockPos p : spots) {
            if (!canPlantAt(level, probe, p)) return; // something changed while walking
        }

        // Clear grass and similar plants first (both halves of tall grass go with the lower one).
        for (BlockPos p : spots) {
            if (Terrain.isClearable(level.getBlockState(p))) level.destroyBlock(p, false, golem);
            if (Terrain.isClearable(level.getBlockState(p.above()))) level.destroyBlock(p.above(), false, golem);
        }

        BlockItem blockItem = (BlockItem) plantItem;
        for (BlockPos p : spots) {
            // Placing through BlockItem.place runs the block's own placement logic, so modded
            // saplings with special states or block entities are set up exactly as if a player placed them.
            ItemStack one = new ItemStack(plantItem);
            if (blockItem.place(context(level, one, p)).consumesAction() && consume) {
                inv.removeItemType(plantItem, 1);
            }
        }
        if (RootGolemConfig.DEBUG_LOG.get()) {
            LOGGER.info("[rootgolem] planted {} x{} at {} {}", plantItem, spots.length, origin.getX(), origin.getZ());
        }
        golem.swing(InteractionHand.MAIN_HAND);
        // Feedback: a soft dig sound and a small puff of soil and green sparkles over the new sapling(s).
        // One sound and two particle packets per planting (at most every few seconds per golem).
        double cx = origin.getX() + (big ? 1.0D : 0.5D), cz = origin.getZ() + (big ? 1.0D : 0.5D);
        double spread = big ? 0.6D : 0.3D;
        level.playSound(null, cx, origin.getY(), cz, RootGolemSounds.PLANT.get(), SoundSource.NEUTRAL, 0.9F, 1.0F);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ROOTED_DIRT.defaultBlockState()),
                cx, origin.getY() + 0.1D, cz, big ? 16 : 8, spread, 0.05D, spread, 0.05D);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, cx, origin.getY() + 0.4D, cz, big ? 6 : 3, spread, 0.2D, spread, 0.0D);
    }
}
