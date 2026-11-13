package com.tygabeast.rootgolem.network;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** The client's copy of nearby boundary outlines and marker info. Plain data only (no client classes). */
public final class ClientBoundaryData {
    private static volatile BoundarySyncPayload latest;
    private static volatile BoundaryInfoPayloads.Info info;

    public static void setInfo(BoundaryInfoPayloads.Info i) {
        info = i;
    }

    /** Latest info for the marker at {@code pos}, or null if none has arrived yet. */
    public static BoundaryInfoPayloads.Info info(BlockPos pos) {
        BoundaryInfoPayloads.Info i = info;
        return i != null && i.marker().equals(pos) ? i : null;
    }

    /** Set by the client setup; opens the Planting Boundary screen. Null on dedicated servers. */
    public static volatile java.util.function.Consumer<BoundaryScreenPayloads.Open> screenOpener;

    public static void openScreen(BoundaryScreenPayloads.Open payload) {
        var opener = screenOpener;
        if (opener != null) opener.accept(payload);
    }

    private ClientBoundaryData() {}

    public static void set(BoundarySyncPayload payload) {
        latest = payload;
    }

    public static List<BoundarySyncPayload.Snapshot> forDimension(ResourceKey<Level> dim) {
        BoundarySyncPayload p = latest;
        return p != null && p.dimension().equals(dim) ? p.boundaries() : List.of();
    }

    public static int maxEdge() {
        BoundarySyncPayload p = latest;
        return p != null ? p.maxEdge() : 64;
    }

    public static Optional<BlockPos> selected(ResourceKey<Level> dim) {
        BoundarySyncPayload p = latest;
        return p != null && p.dimension().equals(dim) ? p.selected() : Optional.empty();
    }
}
