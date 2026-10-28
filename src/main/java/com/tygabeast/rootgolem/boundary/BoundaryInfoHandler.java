package com.tygabeast.rootgolem.boundary;

import com.tygabeast.rootgolem.network.BoundaryInfoPayloads;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Answers marker info requests. Only for markers within reach, and at most one request per player
 * every 5 ticks, so a misbehaving client can't make the server do much.
 */
public final class BoundaryInfoHandler {
    private static final double MAX_DIST_SQR = 10.0D * 10.0D;
    private static final Map<UUID, Long> LAST = new HashMap<>();

    private BoundaryInfoHandler() {}

    public static void handle(BoundaryInfoPayloads.Request req, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        Long last = LAST.get(player.getUUID());
        if (last != null && now - last < 5) return;
        LAST.put(player.getUUID(), now);

        BlockPos pos = req.marker();
        if (player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) > MAX_DIST_SQR) return;
        BoundaryData data = BoundaryData.get(level);
        Boundary b = data.atMarker(pos);
        if (b == null) return;

        int[] golems = data.golemCounts(b.id, now);
        PacketDistributor.sendToPlayer(player, new BoundaryInfoPayloads.Info(pos, b.points.indexOf(pos) + 1, b.points.size(),
                b.isClosed(), b.maxX() - b.minX() + 1, b.maxZ() - b.minZ() + 1, b.spacing(), b.usesDefaultSpacing(),
                BoundaryAccess.ownerName(level.getServer(), b.owner), golems[0], golems[1]));
    }

    public static void forget(UUID player) {
        LAST.remove(player);
    }

    public static void clear() {
        LAST.clear();
    }
}
