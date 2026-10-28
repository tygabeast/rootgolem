package com.tygabeast.rootgolem.entity;

import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;

/** Holds the golem still while a player has its inventory open. */
final class StayWhileOpenGoal extends Goal {
    private final RootGolem golem;

    StayWhileOpenGoal(RootGolem golem) {
        this.golem = golem;
        setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        return golem.isBeingViewed();
    }

    @Override
    public void start() {
        golem.getNavigation().stop();
    }
}
