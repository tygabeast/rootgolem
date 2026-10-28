package com.tygabeast.rootgolem.entity;

import com.tygabeast.rootgolem.RootGolemConfig;
import com.tygabeast.rootgolem.boundary.Boundary;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.InventoryCarrier;

/**
 * Picks up saplings (and anything else plantable) lying on the ground inside the golem's boundary,
 * e.g. dropped by decaying leaves, so a forest keeps replanting itself.
 *
 * Cost: one item-entity search in a 24 x 8 x 24 box every 2 seconds (jittered), skipped entirely
 * when the golem has no boundary, is being viewed or the feature is off.
 */
final class CollectSaplingsGoal extends Goal {
    private static final int SCAN_INTERVAL = 40;
    private static final double RANGE = 12.0D;
    private static final double PICKUP_SQR = 2.5D * 2.5D;
    private static final int MAX_RUN_TICKS = 300;

    private final RootGolem golem;
    private final double speed;
    private int nextScan;
    private int runTicks;
    private ItemEntity target;

    CollectSaplingsGoal(RootGolem golem, double speed) {
        this.golem = golem;
        this.speed = speed;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (golem.tickCount < nextScan || golem.isBeingViewed()) return false;
        nextScan = golem.tickCount + SCAN_INTERVAL + golem.getRandom().nextInt(10);
        if (!RootGolemConfig.COLLECT_SAPLINGS.get()) return false;
        Boundary b = golem.boundary();
        if (b == null) return false;
        SimpleContainer inv = golem.getInventory();

        List<ItemEntity> items = golem.level().getEntitiesOfClass(ItemEntity.class,
                golem.getBoundingBox().inflate(RANGE, 4.0D, RANGE),
                e -> e.isAlive() && !e.hasPickUpDelay() && Plantables.isPlantable(e.getItem()) && inv.canAddItem(e.getItem()));
        ItemEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (ItemEntity e : items) {
            BlockPos p = e.blockPosition();
            if (!b.contains(p.getX(), p.getZ())) continue; // only tidy up its own area
            double d = golem.distanceToSqr(e);
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        target = best;
        return best != null;
    }

    @Override
    public boolean canContinueToUse() {
        return target != null && target.isAlive() && runTicks < MAX_RUN_TICKS && !golem.isBeingViewed();
    }

    @Override
    public void start() {
        runTicks = 0;
        golem.getNavigation().moveTo(target, speed);
    }

    @Override
    public void stop() {
        target = null;
        golem.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        // tick() can run once more on a partial goal-selector tick after the goal finished but before
        // canContinueToUse() is checked again, so a cleared target must be handled here.
        if (target == null || !target.isAlive()) {
            target = null;
            return;
        }
        runTicks++;
        golem.getLookControl().setLookAt(target);
        if (golem.distanceToSqr(target) <= PICKUP_SQR) {
            InventoryCarrier.pickUpItem(golem, golem, target); // moves what fits, plays the pickup animation
            golem.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            target = null; // ends the goal; the next scan finds the next item
        } else if (runTicks % 20 == 0 && golem.getNavigation().isDone()) {
            golem.getNavigation().moveTo(target, speed);
        }
    }
}
