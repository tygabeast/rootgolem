package com.tygabeast.rootgolem.boundary;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** All boundaries in one dimension, saved with the world (data/rootgolem_boundaries.dat). */
public final class BoundaryData extends SavedData {
    private static final String NAME = "rootgolem_boundaries";
    private static final Factory<BoundaryData> FACTORY = new Factory<>(BoundaryData::new, BoundaryData::load);
    /** Globally unique change stamps, so a client never mistakes one world's data for another's. */
    private static final AtomicLong STAMPS = new AtomicLong();

    private final Map<UUID, Boundary> boundaries = new LinkedHashMap<>();
    private final Long2ObjectOpenHashMap<Boundary> byMarker = new Long2ObjectOpenHashMap<>();
    private long version = STAMPS.incrementAndGet();

    public static BoundaryData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public long version() {
        return version;
    }

    public Collection<Boundary> all() {
        return boundaries.values();
    }

    @Nullable
    public Boundary byId(@Nullable UUID id) {
        return id == null ? null : boundaries.get(id);
    }

    @Nullable
    public Boundary atMarker(BlockPos pos) {
        return byMarker.get(pos.asLong());
    }

    /** The smallest closed boundary containing the column, or null. */
    @Nullable
    public Boundary findContaining(int x, int z) {
        Boundary best = null;
        for (Boundary b : boundaries.values()) {
            if (b.contains(x, z) && (best == null || b.boxArea() < best.boxArea())) best = b;
        }
        return best;
    }

    /** Boundaries whose bounding box comes within {@code range} blocks of (x, z). */
    public List<Boundary> near(int x, int z, int range) {
        List<Boundary> out = new ArrayList<>();
        for (Boundary b : boundaries.values()) {
            if (x >= b.minX() - range && x <= b.maxX() + range && z >= b.minZ() - range && z <= b.maxZ() + range) out.add(b);
        }
        return out;
    }

    // ---- changes ------------------------------------------------------------------------------

    Boundary create(BlockPos first, @Nullable UUID owner) {
        Boundary b = new Boundary(UUID.randomUUID());
        b.owner = owner;
        boundaries.put(b.id, b);
        append(b, first);
        return b;
    }

    void append(Boundary b, BlockPos pos) {
        BlockPos p = pos.immutable();
        b.points.add(p);
        byMarker.put(p.asLong(), b);
        b.recompute();
        changed();
    }

    /** Inserts a marker right after index {@code after}, between it and the next marker. */
    void insertAfter(Boundary b, int after, BlockPos pos) {
        BlockPos p = pos.immutable();
        b.points.add(after + 1, p);
        byMarker.put(p.asLong(), b);
        b.recompute();
        changed();
    }

    void close(Boundary b) {
        b.closed = true;
        changed();
    }

    /** Called when a marker block disappears. Neighbouring markers join up; a boundary under 3 markers reopens. */
    void removeMarker(BlockPos pos) {
        Boundary b = byMarker.remove(pos.asLong());
        if (b == null) return;
        b.points.remove(pos);
        if (b.points.isEmpty()) {
            boundaries.remove(b.id);
            golemStatus.remove(b.id);
        } else {
            if (b.points.size() < 3) b.closed = false;
            b.recompute();
        }
        changed();
    }

    void setOwner(Boundary b, @Nullable UUID owner) {
        b.owner = owner;
        changed();
    }

    void setSpacing(Boundary b, int spacing) {
        b.spacing = spacing;
        changed();
    }

    // ---- golem status (not saved) ---------------------------------------------------------------
    // Golems report in about once a second while they tick; reports older than 5 seconds are dropped,
    // so the counts cover golems that are currently loaded and working.

    private record GolemStatus(boolean full, long time) {}
    private final Map<UUID, Map<Integer, GolemStatus>> golemStatus = new HashMap<>();

    public void reportGolem(UUID boundary, int entityId, boolean full, long gameTime) {
        Map<Integer, GolemStatus> m = golemStatus.computeIfAbsent(boundary, k -> new HashMap<>());
        m.put(entityId, new GolemStatus(full, gameTime));
        // Unloaded or dead golems stop reporting; prune them here too so the map can't grow without reads.
        if (m.size() > 1) m.values().removeIf(st -> gameTime - st.time > 100);
    }

    /** {working, full} golem counts for a boundary. */
    public int[] golemCounts(UUID boundary, long gameTime) {
        Map<Integer, GolemStatus> m = golemStatus.get(boundary);
        if (m == null) return new int[]{0, 0};
        m.values().removeIf(s -> gameTime - s.time > 100);
        int full = 0;
        for (GolemStatus s : m.values()) if (s.full) full++;
        if (m.isEmpty()) golemStatus.remove(boundary);
        return new int[]{m.size(), full};
    }

    private void changed() {
        version = STAMPS.incrementAndGet();
        setDirty();
    }

    // ---- saving -------------------------------------------------------------------------------

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Boundary b : boundaries.values()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Id", b.id);
            t.putBoolean("Closed", b.closed);
            if (b.owner != null) t.putUUID("Owner", b.owner);
            if (b.spacing >= 0) t.putInt("Spacing", b.spacing);
            long[] pts = new long[b.points.size()];
            for (int i = 0; i < pts.length; i++) pts[i] = b.points.get(i).asLong();
            t.putLongArray("Points", pts);
            list.add(t);
        }
        tag.put("Boundaries", list);
        return tag;
    }

    private static BoundaryData load(CompoundTag tag, HolderLookup.Provider registries) {
        BoundaryData data = new BoundaryData();
        ListTag list = tag.getList("Boundaries", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            if (!t.hasUUID("Id")) continue;
            Boundary b = new Boundary(t.getUUID("Id"));
            b.closed = t.getBoolean("Closed");
            b.owner = t.hasUUID("Owner") ? t.getUUID("Owner") : null;
            b.spacing = t.contains("Spacing") ? t.getInt("Spacing") : -1;
            for (long l : t.getLongArray("Points")) {
                BlockPos p = BlockPos.of(l);
                if (data.byMarker.containsKey(l)) continue; // never let two boundaries share a marker
                b.points.add(p);
                data.byMarker.put(l, b);
            }
            if (b.points.isEmpty()) continue;
            b.recompute();
            data.boundaries.put(b.id, b);
        }
        return data;
    }
}
