package com.tygabeast.rootgolem.boundary;

import com.tygabeast.rootgolem.RootGolemConfig;
import com.tygabeast.rootgolem.RootGolemMod;
import com.tygabeast.rootgolem.entity.RootGolem;
import com.tygabeast.rootgolem.network.BoundaryScreenPayloads;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server side of the Planting Boundary screen.
 *
 * Opening scans the boundary once: one heightmap read and up to three block reads per column
 * inside it (at most 128 x 128 columns), skipping unloaded chunks, plus one entity search for its
 * golems. Opens and spacing changes are limited to one per player every 10 ticks, so a client
 * cannot make the server rescan continuously.
 */
public final class BoundaryScreenHandler {
    private static final double REACH_SQR = 12.0D * 12.0D;
    private static final int THROTTLE_TICKS = 10;
    private static final TagKey<Block> KEEP_CLEAR = TagKey.create(Registries.BLOCK, RootGolemMod.id("keep_clear"));
    private static final TagKey<Block> UNPLANTABLE = TagKey.create(Registries.BLOCK, RootGolemMod.id("unplantable_ground"));
    private static final Map<UUID, Long> LAST = new HashMap<>();

    private BoundaryScreenHandler() {}

    /** Opens the screen for a closed boundary; called when a marker is clicked with an empty hand. */
    public static void open(ServerPlayer player, ServerLevel level, Boundary b, BlockPos marker) {
        if (throttled(player, level)) return;
        PacketDistributor.sendToPlayer(player, scan(player, level, b, marker));
    }

    public static void handleSetSpacing(BoundaryScreenPayloads.SetSpacing req, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        ServerLevel level = player.serverLevel();
        BlockPos pos = req.marker();
        if (player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) > REACH_SQR) return;
        BoundaryData data = BoundaryData.get(level);
        Boundary b = data.atMarker(pos);
        if (b == null || !b.isClosed() || !isPreset(req.spacing())) return;
        if (throttled(player, level)) return;
        if (!BoundaryAccess.tryEdit(player, level, b)) return;
        if (b.spacing != req.spacing()) {
            data.setSpacing(b, req.spacing());
            level.playSound(null, pos, SoundEvents.AZALEA_LEAVES_PLACE, SoundSource.BLOCKS, 0.8F, 1.2F);
        }
        PacketDistributor.sendToPlayer(player, scan(player, level, b, pos)); // refresh: the room estimate changes
    }

    private static boolean isPreset(int spacing) {
        for (int p : Boundary.SPACING_PRESETS) if (p == spacing) return true;
        return false;
    }

    private static boolean throttled(ServerPlayer player, ServerLevel level) {
        long now = level.getGameTime();
        Long last = LAST.get(player.getUUID());
        if (last != null && now - last < THROTTLE_TICKS) return true;
        LAST.put(player.getUUID(), now);
        return false;
    }

    public static void forget(UUID player) {
        LAST.remove(player);
    }

    public static void clear() {
        LAST.clear();
    }

    // ---- scan ---------------------------------------------------------------------------------

    private static BoundaryScreenPayloads.Open scan(ServerPlayer player, ServerLevel level, Boundary b, BlockPos marker) {
        int minX = b.minX(), minZ = b.minZ();
        int w = b.maxX() - minX + 1, d = b.maxZ() - minZ + 1;
        int spacing = b.spacing();
        boolean ceiling = level.dimensionType().hasCeiling();

        // 0 = outside or unknown, 1 = inside but can't be planted, 2 = plantable, 3 = tree
        byte[] cell = new byte[w * d];
        IntArrayList trees = new IntArrayList();
        BlockPos.MutableBlockPos cur = new BlockPos.MutableBlockPos();
        int area = 0;
        for (int dz = 0; dz < d; dz++) {
            for (int dx = 0; dx < w; dx++) {
                int x = minX + dx, z = minZ + dz;
                if (!b.contains(x, z)) continue;
                area++;
                if (!level.hasChunk(x >> 4, z >> 4)) continue;
                if (ceiling) {
                    cell[dz * w + dx] = 2;               // no heightmap to read there; assume plantable
                    continue;
                }
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockState at = level.getBlockState(cur.set(x, top, z));
                BlockState below = level.getBlockState(cur.set(x, top - 1, z));
                if (isTree(at) || isTree(below)) {
                    cell[dz * w + dx] = 3;
                    if (trees.size() < BoundaryScreenPayloads.MAX_TREES) trees.add(BoundaryScreenPayloads.pack(dx, dz));
                } else if (below.is(BlockTags.DIRT) && !below.is(UNPLANTABLE) && !below.is(KEEP_CLEAR)) {
                    cell[dz * w + dx] = 2;
                } else {
                    cell[dz * w + dx] = 1;
                }
            }
        }

        int room = estimateRoom(cell, w, d, spacing);

        IntArrayList golems = new IntArrayList();
        AABB box = new AABB(minX, marker.getY() - 64, minZ, b.maxX() + 1, marker.getY() + 64, b.maxZ() + 1);
        List<RootGolem> found = level.getEntitiesOfClass(RootGolem.class, box, g -> {
            Boundary gb = g.boundary();
            return gb != null && gb.id.equals(b.id);
        });
        for (RootGolem g : found) {
            if (golems.size() >= BoundaryScreenPayloads.MAX_GOLEMS) break;
            BlockPos p = g.blockPosition();
            golems.add(BoundaryScreenPayloads.pack(Math.max(0, p.getX() - minX), Math.max(0, p.getZ() - minZ)));
        }
        int[] counts = BoundaryData.get(level).golemCounts(b.id, level.getGameTime());

        return new BoundaryScreenPayloads.Open(marker, List.copyOf(b.points), area,
                BoundaryAccess.ownerName(level.getServer(), b.owner), BoundaryAccess.canEdit(player, b),
                b.spacing, RootGolemConfig.TREE_SPACING.get(), counts[0], counts[1], golems, trees, room);
    }

    /**
     * Rough count of further saplings that fit: go through plantable columns in order and accept each
     * one that is at least {@code spacing} empty blocks from every tree and every accepted column.
     * Trees are checked with a 2D prefix sum (O(1) per column, however many trees there are);
     * accepted columns are few, so each marks the square around it. Linear in the area overall.
     */
    static int estimateRoom(byte[] cell, int w, int d, int spacing) {
        int[] sum = new int[(w + 1) * (d + 1)];             // sum[(z+1)*(w+1)+(x+1)] = trees in [0..x]x[0..z]
        for (int z = 0; z < d; z++) {
            for (int x = 0; x < w; x++) {
                sum[(z + 1) * (w + 1) + x + 1] = (cell[z * w + x] == 3 ? 1 : 0)
                        + sum[z * (w + 1) + x + 1] + sum[(z + 1) * (w + 1) + x] - sum[z * (w + 1) + x];
            }
        }
        boolean[] blocked = new boolean[w * d];
        int room = 0;
        for (int z = 0; z < d; z++) {
            for (int x = 0; x < w; x++) {
                int i = z * w + x;
                if (cell[i] != 2 || blocked[i]) continue;
                int x0 = Math.max(0, x - spacing), x1 = Math.min(w - 1, x + spacing);
                int z0 = Math.max(0, z - spacing), z1 = Math.min(d - 1, z + spacing);
                int trees = sum[(z1 + 1) * (w + 1) + x1 + 1] - sum[z0 * (w + 1) + x1 + 1]
                        - sum[(z1 + 1) * (w + 1) + x0] + sum[z0 * (w + 1) + x0];
                if (trees > 0) continue;
                room++;
                block(blocked, w, d, x, z, spacing);
            }
        }
        return room;
    }

    private static void block(boolean[] blocked, int w, int d, int cx, int cz, int r) {
        for (int z = Math.max(0, cz - r); z <= Math.min(d - 1, cz + r); z++) {
            for (int x = Math.max(0, cx - r); x <= Math.min(w - 1, cx + r); x++) blocked[z * w + x] = true;
        }
    }

    private static boolean isTree(BlockState s) {
        return s.is(BlockTags.SAPLINGS) || s.is(BlockTags.LOGS) || s.getBlock() instanceof SaplingBlock;
    }
}
