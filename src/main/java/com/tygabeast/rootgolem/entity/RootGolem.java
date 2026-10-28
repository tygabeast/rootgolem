package com.tygabeast.rootgolem.entity;

import com.tygabeast.rootgolem.RootGolemMod;
import com.tygabeast.rootgolem.RootGolemSounds;
import com.tygabeast.rootgolem.boundary.Boundary;
import com.tygabeast.rootgolem.boundary.BoundaryAccess;
import com.tygabeast.rootgolem.boundary.BoundaryData;
import com.tygabeast.rootgolem.boundary.BoundarySelection;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class RootGolem extends AbstractGolem implements InventoryCarrier {
    public static final int INVENTORY_SIZE = 18;
    /** How far the golem strays from where it stands while it has no boundary. */
    static final int UNBOUND_WANDER = 8;
    private static final int BOUNDARY_RECHECK_TICKS = 200;

    private final GolemInventory inventory = new GolemInventory(this);
    private int viewers;
    @Nullable private UUID boundaryId;
    private int nextBoundaryCheck;
    private int failedSearches;

    public RootGolem(EntityType<? extends RootGolem> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 60.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.STEP_HEIGHT, 1.0D);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new StayWhileOpenGoal(this));
        goalSelector.addGoal(2, new PanicGoal(this, 1.2D));
        goalSelector.addGoal(3, new CollectSaplingsGoal(this, 0.9D));
        goalSelector.addGoal(4, new PlantSaplingGoal(this, 0.9D));
        goalSelector.addGoal(5, new BoundaryWanderGoal(this, 0.6D));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    // ---- boundary ---------------------------------------------------------------------------

    /** The closed boundary this golem tends, or null if it has none (or it was broken open). */
    @Nullable
    public Boundary boundary() {
        if (boundaryId == null || !(level() instanceof ServerLevel sl)) return null;
        Boundary b = BoundaryData.get(sl).byId(boundaryId);
        return b != null && b.isClosed() ? b : null;
    }

    /** Adopts the smallest closed boundary around the golem. Returns it, or null if there is none. */
    @Nullable
    private Boundary findBoundary() {
        if (!(level() instanceof ServerLevel sl)) return null;
        BlockPos p = blockPosition();
        Boundary b = BoundaryData.get(sl).findContaining(p.getX(), p.getZ());
        if (b != null) {
            boundaryId = b.id;
            clearRestriction();
        }
        return b;
    }

    /**
     * May this player manage the golem (open it, feed it, reassign it)? Only if it has no boundary,
     * or the player may edit the boundary it tends. Sends the "belongs to" message when not.
     */
    private boolean mayManage(ServerPlayer player) {
        Boundary current = boundary();
        if (current == null || BoundaryAccess.canEdit(player, current)) return true;
        player.displayClientMessage(Component.translatable("message.rootgolem.not_owner",
                BoundaryAccess.ownerName(player.server, current.owner())), true);
        return false;
    }

    /** Assigns the golem to the boundary the player last selected or built, wherever it is in this dimension. */
    private void assignToSelection(ServerPlayer player) {
        BoundarySelection sel = BoundarySelection.get(player);
        Boundary b = sel == null ? null : BoundaryData.get(player.serverLevel()).byId(sel.boundary());
        if (b == null) {
            player.displayClientMessage(Component.translatable("message.rootgolem.assign.none"), true);
        } else if (!b.isClosed()) {
            player.displayClientMessage(Component.translatable("message.rootgolem.assign.open"), true);
        } else if (mayManage(player) && BoundaryAccess.tryEdit(player, player.serverLevel(), b)) {
            assignTo(b);
            playSound(SoundEvents.ROOTED_DIRT_PLACE, 1.0F, 0.8F);
            player.displayClientMessage(Component.translatable("message.rootgolem.assign.done", b.points().size()), true);
        }
    }

    /** Tends {@code b} from now on; if the golem is outside it, it walks there. */
    public void assignTo(Boundary b) {
        boundaryId = b.id;
        clearRestriction();
    }

    // ---- planting progress (drives relocation and the status message) -------------------------

    /** Called after each planting search that had saplings to plant. */
    void onPlantSearch(boolean found) {
        failedSearches = found ? 0 : Math.min(failedSearches + 1, 1000);
    }

    /** After a couple of failed searches here, the golem heads for another part of its boundary. */
    boolean wantsToRelocate() {
        return failedSearches >= 2;
    }

    /** Many failed searches in a row, across several spots: the boundary is probably out of room. */
    boolean seemsFull() {
        return failedSearches >= 5;
    }

    @Override
    protected void customServerAiStep() {
        // Vanilla stops a mob's wandering once no player has been within 32 blocks for 5 seconds.
        // A golem with a boundary is a worker, so it keeps roaming its area regardless.
        // It also reports its status once a second, for the marker info line.
        if (tickCount % 20 == 0 && boundaryId != null) {
            Boundary b = boundary();
            if (b != null) {
                setNoActionTime(0);
                BoundaryData.get((ServerLevel) level()).reportGolem(b.id, getId(), seemsFull(), level().getGameTime());
            }
        }

        // Re-check every 10 seconds when unassigned, so a golem built before its boundary was finished picks it up.
        if (tickCount >= nextBoundaryCheck) {
            nextBoundaryCheck = tickCount + BOUNDARY_RECHECK_TICKS;
            if (boundary() == null && findBoundary() == null && !hasRestriction()) {
                restrictTo(blockPosition(), UNBOUND_WANDER);
            }
        }
        super.customServerAiStep();
    }

    // ---- inventory --------------------------------------------------------------------------

    @Override
    public SimpleContainer getInventory() {
        return inventory;
    }

    /** Only plantable items are ever picked up (by CollectSaplingsGoal; vanilla loot pickup stays off). */
    @Override
    public boolean wantsToPickUp(ItemStack stack) {
        return Plantables.isPlantable(stack);
    }

    public boolean isBeingViewed() {
        return viewers > 0;
    }

    void onViewerChange(int delta) {
        viewers = Math.max(0, viewers + delta);
        if (viewers > 0) getNavigation().stop();
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);

        if (held.is(Items.BONE_MEAL)) {
            if (getHealth() >= getMaxHealth()) return InteractionResult.PASS;
            if (!level().isClientSide) {
                heal(getMaxHealth() * 0.25F);
                held.consume(1, player);
                playSound(SoundEvents.BONE_MEAL_USE, 1.0F, 1.0F);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }

        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level().isClientSide) return InteractionResult.SUCCESS;
        ServerPlayer sp = (ServerPlayer) player;

        if (held.is(RootGolemMod.BOUNDARY_MARKER_ITEM.get())) {
            assignToSelection(sp);
            return InteractionResult.CONSUME;
        }
        if (!mayManage(sp)) return InteractionResult.CONSUME;

        if (!held.isEmpty() && Plantables.isPlantable(held)) {
            ItemStack rest = inventory.addItem(held.copy());
            int moved = held.getCount() - rest.getCount();
            if (moved > 0) {
                if (!player.getAbilities().instabuild) held.shrink(moved);
                playSound(SoundEvents.ITEM_PICKUP, 0.6F, 1.0F);
            } else {
                player.displayClientMessage(Component.translatable("message.rootgolem.full"), true);
            }
            return InteractionResult.CONSUME;
        }

        if (held.isEmpty() && player.isSecondaryUseActive()) {
            // Re-check: adopt the boundary it stands in, if this player may use it; otherwise keep the current one.
            BlockPos p = blockPosition();
            Boundary here = BoundaryData.get(sp.serverLevel()).findContaining(p.getX(), p.getZ());
            if (here != null && BoundaryAccess.canEdit(sp, here)) assignTo(here);
            Boundary b = boundary();
            Component msg;
            if (b == null) msg = Component.translatable("message.rootgolem.no_boundary");
            else if (seemsFull()) msg = Component.translatable("message.rootgolem.boundary_no_room", b.points().size());
            else msg = Component.translatable("message.rootgolem.boundary_found", b.points().size());
            player.displayClientMessage(msg, true);
            return InteractionResult.CONSUME;
        }

        player.openMenu(new SimpleMenuProvider(
                (id, playerInv, p) -> new ChestMenu(MenuType.GENERIC_9x2, id, playerInv, inventory, 2),
                getDisplayName()));
        return InteractionResult.CONSUME;
    }

    // ---- ticking ----------------------------------------------------------------------------

    @Override
    public void aiStep() {
        // PathfinderMob does not advance arm-swing animation on its own; Monster does it the same way.
        updateSwingTime();
        super.aiStep();
    }

    // ---- saving -----------------------------------------------------------------------------

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        writeInventoryToTag(tag, registryAccess());
        if (boundaryId != null) tag.putUUID("Boundary", boundaryId);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        readInventoryFromTag(tag, registryAccess());
        boundaryId = tag.hasUUID("Boundary") ? tag.getUUID("Boundary") : null;
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        Containers.dropContents(level, this, inventory);
    }

    // ---- sounds -----------------------------------------------------------------------------

    @Override
    protected SoundEvent getAmbientSound() {
        return RootGolemSounds.AMBIENT.get(); // a quiet creak and rustle of leaves
    }

    @Override
    public int getAmbientSoundInterval() {
        return 400; // vanilla default is 80; a golem working all day shouldn't creak constantly
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return RootGolemSounds.HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return RootGolemSounds.DEATH.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(RootGolemSounds.STEP.get(), 0.8F, 1.0F);
    }

    @Override
    public float getVoicePitch() {
        return 0.7F + (random.nextFloat() - 0.5F) * 0.1F;
    }

    // ---- inventory container ----------------------------------------------------------------

    /** Golem inventory that closes when the golem dies or the player walks away, and pauses the golem while open. */
    static final class GolemInventory extends SimpleContainer {
        private final RootGolem golem;

        GolemInventory(RootGolem golem) {
            super(INVENTORY_SIZE);
            this.golem = golem;
        }

        @Override
        public boolean stillValid(Player player) {
            return golem.isAlive() && player.distanceToSqr(golem) < 64.0D;
        }

        @Override
        public void startOpen(Player player) {
            if (!player.isSpectator()) golem.onViewerChange(1);
        }

        @Override
        public void stopOpen(Player player) {
            if (!player.isSpectator()) golem.onViewerChange(-1);
        }
    }
}
