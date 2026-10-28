package com.tygabeast.rootgolem.entity;

import com.tygabeast.rootgolem.boundary.Boundary;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.phys.Vec3;

/**
 * Wandering: with a boundary, the golem strolls to random spots inside it, walks straight back if
 * pushed out, and heads for a random spot anywhere in the boundary when it can't find room to plant
 * where it is. Without a boundary, it strolls near where it stands like a vanilla mob.
 *
 * Cost: target picking only runs when the vanilla stroll timer fires (about every 6 seconds), or
 * every 2 seconds while outside its boundary; at most 16 cheap column samples each time.
 */
final class BoundaryWanderGoal extends WaterAvoidingRandomStrollGoal {
    private static final int LOCAL_RANGE = 12;
    private static final int SAMPLES = 16;

    private final RootGolem golem;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    BoundaryWanderGoal(RootGolem golem, double speed) {
        super(golem, speed);
        this.golem = golem;
    }

    @Override
    public boolean canUse() {
        if (golem.isBeingViewed()) return false;
        // Goals are only evaluated when (tickCount + id) is even, so key the throttle to the same sum.
        int t = golem.tickCount + golem.getId();
        if (t % 40 == 0) {
            Boundary b = golem.boundary();
            BlockPos p = golem.blockPosition();
            // Relocate every 2 s while searching; once the area looks full, only every 10 s to save pathfinding.
            boolean relocate = golem.wantsToRelocate() && (!golem.seemsFull() || t % 200 == 0);
            if (b != null && (!b.contains(p.getX(), p.getZ()) || relocate)) trigger();
        }
        return super.canUse();
    }

    @Nullable
    @Override
    protected Vec3 getPosition() {
        Boundary b = golem.boundary();
        if (b == null || !(golem.level() instanceof ServerLevel level)) return super.getPosition();

        RandomSource rand = golem.getRandom();
        BlockPos at = golem.blockPosition();
        boolean outside = !b.contains(at.getX(), at.getZ());
        boolean relocate = !outside && golem.wantsToRelocate();
        // Inside: stroll locally. Outside: sample the whole boundary and head for the nearest hit.
        // Relocating (no room found here): head for a random spot anywhere in the boundary.
        boolean whole = outside || relocate;
        int minX = whole ? b.minX() : Math.max(b.minX(), at.getX() - LOCAL_RANGE);
        int maxX = whole ? b.maxX() : Math.min(b.maxX(), at.getX() + LOCAL_RANGE);
        int minZ = whole ? b.minZ() : Math.max(b.minZ(), at.getZ() - LOCAL_RANGE);
        int maxZ = whole ? b.maxZ() : Math.min(b.maxZ(), at.getZ() + LOCAL_RANGE);
        if (minX > maxX || minZ > maxZ) return null;

        Vec3 best = null;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < SAMPLES; i++) {
            int x = minX + rand.nextInt(maxX - minX + 1);
            int z = minZ + rand.nextInt(maxZ - minZ + 1);
            if (!b.contains(x, z)) continue;
            int y = Terrain.groundY(level, x, z, at.getY(), whole ? 64 : 6, cursor);
            if (y == Terrain.NONE) continue;
            Vec3 v = new Vec3(x + 0.5D, y, z + 0.5D);
            if (!outside) return v;
            double d = v.distanceToSqr(golem.position());
            if (d < bestDist) {
                bestDist = d;
                best = v;
            }
        }
        return best;
    }
}
