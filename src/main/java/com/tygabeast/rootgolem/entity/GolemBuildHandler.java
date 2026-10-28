package com.tygabeast.rootgolem.entity;

import com.tygabeast.rootgolem.RootGolemConfig;
import com.tygabeast.rootgolem.RootGolemMod;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Builds a Root Golem from four rooted dirt blocks in the iron golem T shape with a carved pumpkin
 * or jack o'lantern on top. Works for players, dispensers and commands alike, because it listens to
 * the neighbour update every block placement sends.
 *
 * Unlike the iron golem, the four corner spaces (beside the head and beside the leg) only need to be
 * free of solid blocks: grass, flowers, saplings, torches and snow layers there are fine. If a
 * pumpkin is placed on rooted dirt but the shape isn't right, nearby players are told which block
 * is wrong.
 *
 * Cost: this event fires for every block update in the world, so the first check is a single block
 * comparison; nothing else runs unless a pumpkin was just placed on rooted dirt.
 */
public final class GolemBuildHandler {
    private static final double TELL_RANGE_SQR = 8.0D * 8.0D;

    private GolemBuildHandler() {}

    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        BlockState placed = event.getState();
        if (!placed.is(Blocks.CARVED_PUMPKIN) && !placed.is(Blocks.JACK_O_LANTERN)) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockPos head = event.getPos();
        BlockPos body = head.below();
        if (!level.getBlockState(body).is(Blocks.ROOTED_DIRT)) return;
        if (!RootGolemConfig.ALLOW_BUILDING.get()) return;

        // Try both orientations (arms along X or along Z); remember the closer miss for the hint.
        Problem best = null;
        for (Direction side : new Direction[]{Direction.EAST, Direction.SOUTH}) {
            Problem p = check(level, head, side);
            if (p == null) {
                spawn(level, head, side);
                return;
            }
            if (best == null || p.score > best.score) best = p;
        }
        tellNearby(level, head, best);
    }

    /** Which cell is wrong, and how many cells were right (to pick the orientation the player meant). */
    private record Problem(BlockPos pos, BlockState found, boolean needsRootedDirt, int score) {}

    private static Problem check(ServerLevel level, BlockPos head, Direction side) {
        BlockPos body = head.below(), leg = body.below();
        BlockPos[] dirt = {body.relative(side), body.relative(side.getOpposite()), leg};
        BlockPos[] empty = {head.relative(side), head.relative(side.getOpposite()), leg.relative(side), leg.relative(side.getOpposite())};
        int score = 0;
        Problem first = null;
        for (BlockPos p : dirt) {
            BlockState s = level.getBlockState(p);
            if (s.is(Blocks.ROOTED_DIRT)) score += 2;
            else if (first == null) first = new Problem(p, s, true, 0);
        }
        for (BlockPos p : empty) {
            BlockState s = level.getBlockState(p);
            if (isOpen(level, p, s)) score++;
            else if (first == null) first = new Problem(p, s, false, 0);
        }
        return first == null ? null : new Problem(first.pos, first.found, first.needsRootedDirt, score);
    }

    /** Nothing solid: air, grass, flowers, saplings, torches, snow layers... anything you can walk through. */
    private static boolean isOpen(ServerLevel level, BlockPos pos, BlockState s) {
        return s.isAir() || s.canBeReplaced() || s.getCollisionShape(level, pos).isEmpty();
    }

    private static void spawn(ServerLevel level, BlockPos head, Direction side) {
        RootGolem golem = RootGolemMod.ROOT_GOLEM.get().create(level);
        if (golem == null) return;
        BlockPos body = head.below(), leg = body.below();
        BlockPos[] parts = {head, body, body.relative(side), body.relative(side.getOpposite()), leg};
        for (BlockPos p : parts) {
            level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, p, Block.getId(level.getBlockState(p)));
            level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        golem.moveTo(leg.getX() + 0.5D, leg.getY() + 0.05D, leg.getZ() + 0.5D, 0.0F, 0.0F);
        level.addFreshEntity(golem);
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, golem.getBoundingBox().inflate(5.0D))) {
            CriteriaTriggers.SUMMONED_ENTITY.trigger(p, golem);
        }
        for (BlockPos p : parts) level.blockUpdated(p, Blocks.AIR);
    }

    private static void tellNearby(ServerLevel level, BlockPos head, Problem p) {
        Component msg = p.needsRootedDirt
                ? Component.translatable("message.rootgolem.build.needs_dirt", p.pos.getX(), p.pos.getY(), p.pos.getZ())
                : Component.translatable("message.rootgolem.build.blocked", p.found.getBlock().getName(), p.pos.getX(), p.pos.getY(), p.pos.getZ());
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(head.getX() + 0.5D, head.getY(), head.getZ() + 0.5D) <= TELL_RANGE_SQR) {
                player.displayClientMessage(msg, true);
            }
        }
    }
}
