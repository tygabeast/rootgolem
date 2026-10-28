package com.tygabeast.rootgolem.network;

import com.tygabeast.rootgolem.RootGolemMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Marker info line: the client asks about the marker it is aiming at, the server answers. */
public final class BoundaryInfoPayloads {
    private BoundaryInfoPayloads() {}

    /** Client -> server: "tell me about the marker at this position". */
    public record Request(BlockPos marker) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(RootGolemMod.id("boundary_info_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> STREAM_CODEC =
                BlockPos.STREAM_CODEC.map(Request::new, Request::marker).cast();

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server -> client: the marker's boundary, its settings and how its golems are doing. */
    public record Info(BlockPos marker, int index, int markers, boolean closed, int width, int depth,
                       int spacing, boolean defaultSpacing, String owner, int golems, int fullGolems)
            implements CustomPacketPayload {
        public static final Type<Info> TYPE = new Type<>(RootGolemMod.id("boundary_info"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Info> STREAM_CODEC = StreamCodec.ofMember(Info::write, Info::read);

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeBlockPos(marker);
            buf.writeVarInt(index);
            buf.writeVarInt(markers);
            buf.writeBoolean(closed);
            buf.writeVarInt(width);
            buf.writeVarInt(depth);
            buf.writeVarInt(spacing);
            buf.writeBoolean(defaultSpacing);
            buf.writeUtf(owner, 32);
            buf.writeVarInt(golems);
            buf.writeVarInt(fullGolems);
        }

        private static Info read(RegistryFriendlyByteBuf buf) {
            return new Info(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readUtf(32), buf.readVarInt(), buf.readVarInt());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
