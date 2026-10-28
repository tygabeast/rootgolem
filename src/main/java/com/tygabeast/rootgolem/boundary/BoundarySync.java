package com.tygabeast.rootgolem.boundary;

import com.tygabeast.rootgolem.RootGolemConfig;
import com.tygabeast.rootgolem.RootGolemMod;
import com.tygabeast.rootgolem.network.BoundarySyncPayload;
import com.tygabeast.rootgolem.network.BoundarySyncPayload.Snapshot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Sends nearby boundary outlines to players holding a marker, so their client can draw them.
 *
 * Cost: one cheap held-item check per player every 10 ticks. Data is only sent when the player is
 * holding a marker AND something changed (boundaries edited, selection changed, moved 32+ blocks,
 * changed dimension).
 */
public final class BoundarySync {
    private static final int RANGE = 96;
    private static final int RESEND_DISTANCE_SQR = 32 * 32;

    private record Sent(ResourceKey<Level> dim, long version, BlockPos pos, Optional<BlockPos> selected) {}

    private static final Map<UUID, Sent> SENT = new HashMap<>();

    private BoundarySync() {}

    public static boolean isHoldingMarker(Player player) {
        var item = RootGolemMod.BOUNDARY_MARKER_ITEM.get();
        return player.getMainHandItem().is(item) || player.getOffhandItem().is(item);
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 10 != 0) return;
        if (!isHoldingMarker(player)) return;

        ServerLevel level = player.serverLevel();
        BoundaryData data = BoundaryData.get(level);
        Optional<BlockPos> selected = selectedMarker(data, player);
        Sent last = SENT.get(player.getUUID());
        if (last != null && last.dim == level.dimension() && last.version == data.version()
                && last.selected.equals(selected) && last.pos.distSqr(player.blockPosition()) < RESEND_DISTANCE_SQR) {
            return;
        }

        BlockPos at = player.blockPosition();
        List<Snapshot> list = new ArrayList<>();
        for (Boundary b : data.near(at.getX(), at.getZ(), RANGE)) {
            list.add(new Snapshot(b.isClosed(), List.copyOf(b.points)));
            if (list.size() >= BoundarySyncPayload.MAX_BOUNDARIES) break;
        }
        PacketDistributor.sendToPlayer(player, new BoundarySyncPayload(level.dimension(), list, selected, RootGolemConfig.MAX_EDGE_LENGTH.get()));
        SENT.put(player.getUUID(), new Sent(level.dimension(), data.version(), at, selected));
    }

    /** The player's insertion marker, if it still exists in this dimension. */
    private static Optional<BlockPos> selectedMarker(BoundaryData data, Player player) {
        BoundarySelection sel = BoundarySelection.get(player);
        if (sel == null || sel.after() == null) return Optional.empty();
        Boundary b = data.atMarker(sel.after());
        return b != null && b.id.equals(sel.boundary()) ? Optional.of(sel.after()) : Optional.empty();
    }

    public static void forget(Player player) {
        SENT.remove(player.getUUID());
    }

    public static void clear() {
        SENT.clear();
    }
}
