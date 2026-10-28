package com.tygabeast.rootgolem.client;

import com.tygabeast.rootgolem.RootGolemMod;
import com.tygabeast.rootgolem.boundary.BoundaryMarkerBlock;
import com.tygabeast.rootgolem.boundary.BoundarySync;
import com.tygabeast.rootgolem.network.BoundaryInfoPayloads;
import com.tygabeast.rootgolem.network.BoundarySyncPayload.Snapshot;
import com.tygabeast.rootgolem.network.ClientBoundaryData;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Text under the crosshair for working with boundaries.
 *
 * Aiming at a marker (any item in hand): info about its boundary - marker number, size, tree
 * spacing, owner, and how many golems are working there and whether they report no room.
 *
 * Otherwise, while holding a marker with one selected: distance from the spot you're aiming at to
 * your current marker against the maximum, plus the other edge that must also fit (back to marker 1
 * for an open boundary, or on to the next marker when inserting). An arrow shows which way the
 * marker is when it isn't roughly ahead of you. Green = fine, yellow = over 80%, red = too far.
 *
 * Cost: lines are rebuilt every 2 client ticks only while one of the modes is active; info is
 * requested from the server at most once a second, only while aiming at a marker; drawing is up to
 * three text draws per frame.
 */
public final class MarkerDistanceHud {
    static final ResourceLocation LAYER_ID = RootGolemMod.id("marker_distance");
    private static final int GREEN = 0xFF7FDB5A, YELLOW = 0xFFFFD24A, RED = 0xFFFF6A5A, INFO = 0xFFE6E6E6;
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    private static final Component[] lines = new Component[3];
    private static final int[] colors = new int[3];
    private static int count;
    private static int tick;
    private static BlockPos lastRequested;
    private static int lastRequestTick;

    private MarkerDistanceHud() {}

    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) {
            count = 0;
            return;
        }
        if ((++tick & 1) != 0) return;
        count = 0;

        // Info mode: aiming at a marker.
        HitResult hit = mc.hitResult;
        if (hit instanceof BlockHitResult bh && hit.getType() == HitResult.Type.BLOCK
                && level.getBlockState(bh.getBlockPos()).getBlock() instanceof BoundaryMarkerBlock) {
            BlockPos pos = bh.getBlockPos();
            if (!pos.equals(lastRequested) || tick - lastRequestTick >= 20) {
                PacketDistributor.sendToServer(new BoundaryInfoPayloads.Request(pos));
                lastRequested = pos;
                lastRequestTick = tick;
            }
            buildInfo(ClientBoundaryData.info(pos));
            return;
        }
        lastRequested = null;

        // Distance mode: holding a marker with one selected.
        if (!BoundarySync.isHoldingMarker(player)) return;
        BlockPos selected = ClientBoundaryData.selected(level.dimension()).orElse(null);
        Snapshot owner = selected == null ? null : findOwner(ClientBoundaryData.forDimension(level.dimension()), selected);
        if (owner == null) return;

        BlockPos spot = placementSpot(hit, level, player);
        int max = ClientBoundaryData.maxEdge();
        List<BlockPos> pts = owner.points();
        int i = pts.indexOf(selected), n = pts.size();

        double d1 = distance(spot, selected);
        add(withArrow(player, selected, Component.translatable("hud.rootgolem.to_marker", i + 1, format(d1), max)), color(d1, max));

        BlockPos other = null;
        String key = null;
        int otherNumber = 0;
        if (i + 1 < n) {                         // inserting between two markers
            other = pts.get(i + 1);
            key = "hud.rootgolem.to_next";
            otherNumber = i + 2;
        } else if (owner.closed()) {             // inserting after the last marker of a closed loop
            other = pts.get(0);
            key = "hud.rootgolem.to_next";
            otherNumber = 1;
        } else if (n >= 2) {                     // building an open boundary: the closing edge must fit too
            other = pts.get(0);
            key = "hud.rootgolem.to_first";
            otherNumber = 1;
        }
        if (other != null) {
            double d2 = distance(spot, other);
            add(withArrow(player, other, Component.translatable(key, otherNumber, format(d2), max)), color(d2, max));
        }
    }

    private static void buildInfo(BoundaryInfoPayloads.Info info) {
        if (info == null) return; // first answer is on its way
        add(info.closed()
                ? Component.translatable("hud.rootgolem.info.closed", info.index(), info.markers(), info.width(), info.depth())
                : Component.translatable("hud.rootgolem.info.open", info.index(), info.markers()), INFO);
        Component spacing = info.defaultSpacing()
                ? Component.translatable("hud.rootgolem.info.spacing_default", info.spacing())
                : Component.translatable("hud.rootgolem.info.spacing", info.spacing());
        Component owner = info.owner().isEmpty() ? Component.translatable("hud.rootgolem.info.nobody") : Component.literal(info.owner());
        add(Component.translatable("hud.rootgolem.info.settings", spacing, owner), INFO);
        if (info.golems() == 0) {
            add(Component.translatable("hud.rootgolem.info.no_golems"), INFO);
        } else if (info.fullGolems() > 0) {
            add(Component.translatable("hud.rootgolem.info.golems_full", info.golems(), info.fullGolems()), YELLOW);
        } else {
            add(Component.translatable("hud.rootgolem.info.golems", info.golems()), GREEN);
        }
    }

    static void render(GuiGraphics g, DeltaTracker delta) {
        int n = count;
        if (n == 0 || Minecraft.getInstance().options.hideGui) return;
        Font font = Minecraft.getInstance().font;
        int cx = g.guiWidth() / 2, y = g.guiHeight() / 2 + 12;
        for (int i = 0; i < n; i++) {
            Component c = lines[i];
            g.drawString(font, c, cx - font.width(c) / 2, y + i * 10, colors[i], true);
        }
    }

    private static void add(Component c, int color) {
        if (count < lines.length) {
            lines[count] = c;
            colors[count++] = color;
        }
    }

    /** Prefixes an arrow pointing toward {@code target} when it is more than 30 degrees off to the side. */
    private static Component withArrow(LocalPlayer player, BlockPos target, Component text) {
        double dx = target.getX() + 0.5D - player.getX(), dz = target.getZ() + 0.5D - player.getZ();
        if (dx * dx + dz * dz < 4.0D) return text; // standing on it
        float targetYaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        float rel = Mth.wrapDegrees(targetYaw - player.getYRot()); // + = to the right
        if (Math.abs(rel) <= 30.0F) return text;
        int idx = Math.floorMod(Math.round(rel / 45.0F), 8);
        return Component.literal(ARROWS[idx] + " ").append(text);
    }

    private static Snapshot findOwner(List<Snapshot> boundaries, BlockPos marker) {
        for (Snapshot s : boundaries) if (s.points().contains(marker)) return s;
        return null;
    }

    /** Where a marker would be placed: the block in front of the aimed face (or the aimed block if it is grass etc.). */
    private static BlockPos placementSpot(HitResult hit, ClientLevel level, LocalPlayer player) {
        if (hit instanceof BlockHitResult bh && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos p = bh.getBlockPos();
            return level.getBlockState(p).canBeReplaced() ? p : p.relative(bh.getDirection());
        }
        return player.blockPosition();
    }

    /** Same horizontal distance the server checks. */
    private static double distance(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static String format(double d) {
        return String.valueOf(Math.round(d * 10.0D) / 10.0D);
    }

    private static int color(double d, int max) {
        return d > max ? RED : d > max * 0.8D ? YELLOW : GREEN;
    }
}
