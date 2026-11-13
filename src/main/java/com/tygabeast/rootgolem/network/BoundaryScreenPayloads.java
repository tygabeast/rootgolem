package com.tygabeast.rootgolem.network;

import com.tygabeast.rootgolem.RootGolemMod;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Planting Boundary screen: the server opens it with a scan of the boundary; the client asks to change spacing. */
public final class BoundaryScreenPayloads {
    public static final int MAX_POINTS = 128;
    public static final int MAX_TREES = 2048;
    public static final int MAX_GOLEMS = 64;

    private BoundaryScreenPayloads() {}

    /** Packs a column relative to the boundary's min corner (both offsets are under 65536). */
    public static int pack(int dx, int dz) {
        return (dx << 16) | (dz & 0xFFFF);
    }

    public static int unpackX(int packed) {
        return packed >>> 16;
    }

    public static int unpackZ(int packed) {
        return packed & 0xFFFF;
    }

    /**
     * Server -> client: open (or refresh) the screen for the boundary that {@code marker} belongs to.
     * Trees and golems are columns packed relative to the boundary's min x/z corner.
     */
    public record Open(BlockPos marker, List<BlockPos> points, int area, String owner, boolean canEdit,
                       int spacingRaw, int spacingDefault, int golems, int fullGolems,
                       IntArrayList golemColumns, IntArrayList treeColumns, int room) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(RootGolemMod.id("boundary_screen"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Open> STREAM_CODEC = StreamCodec.ofMember(Open::write, Open::read);

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeBlockPos(marker);
            buf.writeVarInt(points.size());
            for (BlockPos p : points) buf.writeBlockPos(p);
            buf.writeVarInt(area);
            buf.writeUtf(owner, 32);
            buf.writeBoolean(canEdit);
            buf.writeVarInt(spacingRaw + 1);           // -1 (default) is sent as 0
            buf.writeVarInt(spacingDefault);
            buf.writeVarInt(golems);
            buf.writeVarInt(fullGolems);
            writeInts(buf, golemColumns);
            writeInts(buf, treeColumns);
            buf.writeVarInt(room);
        }

        private static Open read(RegistryFriendlyByteBuf buf) {
            BlockPos marker = buf.readBlockPos();
            int n = Math.min(buf.readVarInt(), MAX_POINTS);
            List<BlockPos> pts = new ArrayList<>(n);
            for (int i = 0; i < n; i++) pts.add(buf.readBlockPos());
            return new Open(marker, pts, buf.readVarInt(), buf.readUtf(32), buf.readBoolean(), buf.readVarInt() - 1,
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), readInts(buf, MAX_GOLEMS), readInts(buf, MAX_TREES),
                    buf.readVarInt());
        }

        private static void writeInts(RegistryFriendlyByteBuf buf, IntArrayList list) {
            buf.writeVarInt(list.size());
            for (int i = 0; i < list.size(); i++) buf.writeInt(list.getInt(i));
        }

        private static IntArrayList readInts(RegistryFriendlyByteBuf buf, int max) {
            int n = buf.readVarInt();
            if (n < 0 || n > max) throw new IllegalArgumentException("too many entries: " + n);
            IntArrayList list = new IntArrayList(n);
            for (int i = 0; i < n; i++) list.add(buf.readInt());
            return list;
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client -> server: set the tree spacing of the boundary that {@code marker} belongs to (-1 = server default). */
    public record SetSpacing(BlockPos marker, int spacing) implements CustomPacketPayload {
        public static final Type<SetSpacing> TYPE = new Type<>(RootGolemMod.id("set_spacing"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetSpacing> STREAM_CODEC = StreamCodec.ofMember(
                (p, buf) -> { buf.writeBlockPos(p.marker); buf.writeVarInt(p.spacing + 1); },
                buf -> new SetSpacing(buf.readBlockPos(), buf.readVarInt() - 1));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
