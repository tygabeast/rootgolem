package com.tygabeast.rootgolem.client;

import com.tygabeast.rootgolem.boundary.BoundarySync;
import com.tygabeast.rootgolem.network.BoundarySyncPayload.Snapshot;
import com.tygabeast.rootgolem.network.ClientBoundaryData;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Vector3f;

/**
 * Draws boundaries while the player holds a marker: dots that travel along each connection from
 * one marker to the next (green = closed, orange = still open, white = the edge the next marker
 * will be inserted into), a sparkle over the first marker of an open boundary (click it to close),
 * and a rising spark over the player's selected marker.
 *
 * Cost: nothing unless a marker is held. Then every 3 ticks, one particle per 2 blocks of edge
 * within 48 blocks of the player, capped at 300 per spawn.
 */
public final class BoundaryParticles {
    private static final DustParticleOptions CLOSED = new DustParticleOptions(new Vector3f(0.35F, 0.85F, 0.25F), 1.0F);
    private static final DustParticleOptions OPEN = new DustParticleOptions(new Vector3f(1.0F, 0.6F, 0.15F), 1.0F);
    private static final DustParticleOptions SELECTED = new DustParticleOptions(new Vector3f(0.95F, 0.95F, 1.0F), 1.2F);
    private static final double STEP = 2.0D;
    private static final double RANGE_SQR = 48.0D * 48.0D;
    private static final int CAP = 300;

    private static int tick;

    private BoundaryParticles() {}

    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || mc.isPaused()) return;
        if (!BoundarySync.isHoldingMarker(player)) return;
        if (++tick % 3 != 0) return;

        List<Snapshot> boundaries = ClientBoundaryData.forDimension(level.dimension());
        if (boundaries.isEmpty()) return;

        double phase = (tick / 3 % 4) * (STEP / 4.0D); // dots creep forward along each edge
        double px = player.getX(), py = player.getY(), pz = player.getZ();
        int budget = CAP;
        BlockPos selected = ClientBoundaryData.selected(level.dimension()).orElse(null);
        if (selected != null) {
            level.addParticle(ParticleTypes.END_ROD, selected.getX() + 0.5D, selected.getY() + 1.3D, selected.getZ() + 0.5D, 0.0D, 0.01D, 0.0D);
        }

        for (Snapshot s : boundaries) {
            List<BlockPos> pts = s.points();
            int n = pts.size();
            DustParticleOptions dust = s.closed() ? CLOSED : OPEN;
            int edges = s.closed() ? n : n - 1;
            for (int i = 0; i < edges && budget > 0; i++) {
                BlockPos a = pts.get(i), b = pts.get((i + 1) % n);
                // The edge new markers will be inserted into is drawn white.
                DustParticleOptions edgeDust = a.equals(selected) ? SELECTED : dust;
                double ax = a.getX() + 0.5D, ay = a.getY() + 0.85D, az = a.getZ() + 0.5D;
                double dx = b.getX() + 0.5D - ax, dy = b.getY() + 0.85D - ay, dz = b.getZ() + 0.5D - az;
                double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (len < 0.01D) continue;
                for (double t = phase; t < len && budget > 0; t += STEP) {
                    double f = t / len;
                    double x = ax + dx * f, y = ay + dy * f, z = az + dz * f;
                    double ex = x - px, ey = y - py, ez = z - pz;
                    if (ex * ex + ey * ey + ez * ez > RANGE_SQR) continue;
                    level.addParticle(edgeDust, x, y, z, 0.0D, 0.0D, 0.0D);
                    budget--;
                }
            }
            if (!s.closed() && n >= 3) {
                BlockPos first = pts.get(0);
                level.addParticle(ParticleTypes.HAPPY_VILLAGER, first.getX() + 0.5D, first.getY() + 1.2D, first.getZ() + 0.5D, 0.0D, 0.0D, 0.0D);
            }
        }
    }
}
