package com.tygabeast.rootgolem.boundary;

import com.tygabeast.rootgolem.RootGolemConfig;
import com.tygabeast.rootgolem.RootGolemMod;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Boundary marker post with a glowing bulb.
 *
 * - Placing markers in a row connects them in placement order.
 * - Clicking the first marker of an open boundary (3+ markers) while holding a marker closes it.
 * - Clicking any other marker while holding a marker selects it: the next markers placed are
 *   inserted after it, which is how an existing boundary is expanded. The selection is also what
 *   a golem gets assigned to when right-clicked with a marker.
 * - Sneak while placing to start a separate boundary.
 * - Clicking a marker of a closed boundary with an empty hand opens the Planting Boundary screen.
 * - With ownerOnly on, only the boundary's owner (and ops) may change or break it.
 */
public class BoundaryMarkerBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(5.0D, 0.0D, 5.0D, 11.0D, 15.0D, 11.0D);

    public BoundaryMarkerBlock(Properties props) {
        super(props);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    // ---- placing ----------------------------------------------------------------------------

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel sl && placer instanceof ServerPlayer player) {
            onPlacedByPlayer(sl, pos, player);
        }
    }

    private static void onPlacedByPlayer(ServerLevel level, BlockPos pos, ServerPlayer player) {
        BoundaryData data = BoundaryData.get(level);
        BoundarySelection sel = BoundarySelection.get(player);
        Boundary chain = sel == null ? null : data.byId(sel.boundary());

        // Where does the new marker go? After the selected marker, or after the last one of an open chain.
        int afterIndex = -1;
        if (chain != null && !player.isShiftKeyDown()) {
            afterIndex = sel.after() == null ? -1 : chain.points.indexOf(sel.after());
            if (afterIndex < 0 && !chain.closed) afterIndex = chain.points.size() - 1;
        }

        Component reason = null;
        if (afterIndex >= 0) {
            reason = BoundaryAccess.canEdit(player, chain)
                    ? insertProblem(chain, afterIndex, pos)
                    : Component.translatable("message.rootgolem.not_owner", BoundaryAccess.ownerName(level.getServer(), chain.owner));
        }

        Boundary b;
        if (afterIndex < 0 || reason != null) {
            b = data.create(pos, player.getUUID());
            player.displayClientMessage(reason != null
                    ? Component.translatable("message.rootgolem.marker.started_because", reason)
                    : Component.translatable("message.rootgolem.marker.started"), true);
        } else {
            boolean appending = afterIndex == chain.points.size() - 1 && !chain.closed;
            BoundaryAccess.tryEdit(player, level, chain); // claims an unowned boundary
            data.insertAfter(chain, afterIndex, pos);
            b = chain;
            int n = b.points.size();
            String key = b.closed ? "message.rootgolem.marker.inserted"
                    : appending && n >= 3 ? "message.rootgolem.marker.added_closable" : "message.rootgolem.marker.added";
            player.displayClientMessage(Component.translatable(key, n), true);
        }
        BoundarySelection.set(player, b.id, pos);
    }

    /** Why {@code pos} can't go after marker {@code afterIndex} (edge too long, boundary too big), or null if it can. */
    @Nullable
    static Component insertProblem(Boundary chain, int afterIndex, BlockPos pos) {
        int n = chain.points.size();
        if (n >= RootGolemConfig.MAX_MARKERS.get()) {
            return Component.translatable("message.rootgolem.marker.too_many", RootGolemConfig.MAX_MARKERS.get());
        }
        BlockPos prev = chain.points.get(afterIndex);
        BlockPos next = afterIndex + 1 < n ? chain.points.get(afterIndex + 1) : (chain.closed ? chain.points.get(0) : null);
        int maxEdge = RootGolemConfig.MAX_EDGE_LENGTH.get();
        if (Boundary.horizontalDistance(prev, pos) > maxEdge || (next != null && Boundary.horizontalDistance(pos, next) > maxEdge)) {
            return Component.translatable("message.rootgolem.marker.too_far", maxEdge);
        }
        int maxSize = RootGolemConfig.MAX_BOUNDARY_SIZE.get();
        if (chain.sizeWith(pos) > maxSize) {
            return Component.translatable("message.rootgolem.marker.too_big", maxSize);
        }
        return null;
    }

    // ---- closing / selecting / info ---------------------------------------------------------

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(RootGolemMod.BOUNDARY_MARKER_ITEM.get())) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        // Holding a marker and clicking a marker never places a new one next to it: it closes or selects.
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        ServerLevel sl = (ServerLevel) level;
        ServerPlayer sp = (ServerPlayer) player;
        if (!tryClose(sl, pos, sp)) select(sl, pos, sp);
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        ServerLevel sl = (ServerLevel) level;
        ServerPlayer sp = (ServerPlayer) player;
        if (!tryClose(sl, pos, sp)) {
            Boundary b = BoundaryData.get(sl).atMarker(pos);
            if (b != null && b.isClosed()) {
                BoundaryScreenHandler.open(sp, sl, b, pos);       // Planting Boundary screen
            } else if (b != null) {
                player.displayClientMessage(Component.translatable("message.rootgolem.marker.info_open",
                        b.points.indexOf(pos) + 1, b.points.size()), true);
            }
        }
        return InteractionResult.CONSUME;
    }

    private static void select(ServerLevel level, BlockPos pos, ServerPlayer player) {
        Boundary b = BoundaryData.get(level).atMarker(pos);
        if (b == null) return;
        if (!BoundaryAccess.canEdit(player, b)) {
            player.displayClientMessage(Component.translatable("message.rootgolem.not_owner",
                    BoundaryAccess.ownerName(level.getServer(), b.owner)), true);
            return;
        }
        BoundarySelection.set(player, b.id, pos);
        int i = b.points.indexOf(pos) + 1;
        player.displayClientMessage(Component.translatable(b.closed
                ? "message.rootgolem.marker.selected_closed" : "message.rootgolem.marker.selected_open", i, b.points.size()), true);
    }

    /** Closes the boundary if {@code pos} is the first marker of an open boundary with at least 3 markers. */
    private static boolean tryClose(ServerLevel level, BlockPos pos, ServerPlayer player) {
        BoundaryData data = BoundaryData.get(level);
        Boundary b = data.atMarker(pos);
        if (b == null || b.closed || b.points.size() < 3 || !b.points.get(0).equals(pos)) return false;
        if (!BoundaryAccess.tryEdit(player, level, b)) return true;

        BlockPos last = b.points.get(b.points.size() - 1);
        int maxEdge = RootGolemConfig.MAX_EDGE_LENGTH.get();
        if (Boundary.horizontalDistance(last, pos) > maxEdge) {
            player.displayClientMessage(Component.translatable("message.rootgolem.marker.close_too_far", maxEdge), true);
            return true;
        }
        data.close(b);
        // Keep the closed boundary selected (so a golem can be assigned straight away), but with no
        // insertion point: the next marker placed starts a new boundary unless a marker is clicked first.
        BoundarySelection.set(player, b.id, null);
        level.playSound(null, pos, SoundEvents.ROOTED_DIRT_PLACE, SoundSource.BLOCKS, 1.0F, 0.7F);
        player.displayClientMessage(Component.translatable("message.rootgolem.marker.closed", b.points.size()), true);
        return true;
    }

    // ---- removing ---------------------------------------------------------------------------

    /** Stops players who may not edit a boundary from breaking its markers. Explosions etc. still can. */
    public static void onBreak(BlockEvent.BreakEvent event) {
        // Cheapest check first: this event fires for every block a player breaks.
        if (!(event.getState().getBlock() instanceof BoundaryMarkerBlock)) return;
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getPlayer() instanceof ServerPlayer player)) return;
        Boundary b = BoundaryData.get(level).atMarker(event.getPos());
        if (b != null && !BoundaryAccess.canEdit(player, b)) {
            player.displayClientMessage(Component.translatable("message.rootgolem.not_owner",
                    BoundaryAccess.ownerName(level.getServer(), b.owner)), true);
            event.setCanceled(true);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel sl) {
            BoundaryData.get(sl).removeMarker(pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
