package com.tygabeast.rootgolem.network;

import com.tygabeast.rootgolem.RootGolemMod;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Outlines of the boundaries near a player (markers in order, and whether each loop is closed),
 * plus the marker this player has selected as the insertion point, if any, and the server's maximum
 * marker distance (for the distance counter).
 */
public record BoundarySyncPayload(ResourceKey<Level> dimension, List<Snapshot> boundaries, Optional<BlockPos> selected, int maxEdge)
        implements CustomPacketPayload {
    public static final int MAX_BOUNDARIES = 64;
    public static final int MAX_POINTS = 128;

    public record Snapshot(boolean closed, List<BlockPos> points) {
        static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Snapshot::closed,
                BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_POINTS)), Snapshot::points,
                Snapshot::new);
    }

    public static final Type<BoundarySyncPayload> TYPE = new Type<>(RootGolemMod.id("boundary_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BoundarySyncPayload> STREAM_CODEC = StreamCodec.composite(
            ResourceKey.streamCodec(Registries.DIMENSION), BoundarySyncPayload::dimension,
            Snapshot.CODEC.apply(ByteBufCodecs.list(MAX_BOUNDARIES)), BoundarySyncPayload::boundaries,
            ByteBufCodecs.optional(BlockPos.STREAM_CODEC), BoundarySyncPayload::selected,
            ByteBufCodecs.VAR_INT, BoundarySyncPayload::maxEdge,
            BoundarySyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
