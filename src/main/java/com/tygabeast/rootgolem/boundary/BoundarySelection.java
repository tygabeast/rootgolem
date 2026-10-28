package com.tygabeast.rootgolem.boundary;

import com.tygabeast.rootgolem.RootGolemMod;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/**
 * Which boundary a player is working on, and the marker that new markers go after (null when there
 * is no insertion point, e.g. right after closing a boundary). Set by placing a marker or clicking one
 * while holding a marker; also what a golem is assigned to when right-clicked with a marker.
 * Stored in the player's own saved data, so it survives relogging.
 */
public record BoundarySelection(UUID boundary, @Nullable BlockPos after) {
    private static final String KEY = RootGolemMod.MODID + ":selection";

    @Nullable
    public static BoundarySelection get(Player player) {
        CompoundTag pd = player.getPersistentData();
        if (!pd.contains(KEY)) return null;
        CompoundTag t = pd.getCompound(KEY);
        if (!t.hasUUID("Boundary")) return null;
        return new BoundarySelection(t.getUUID("Boundary"), t.contains("After") ? BlockPos.of(t.getLong("After")) : null);
    }

    public static void set(Player player, UUID boundary, @Nullable BlockPos after) {
        CompoundTag t = new CompoundTag();
        t.putUUID("Boundary", boundary);
        if (after != null) t.putLong("After", after.asLong());
        player.getPersistentData().put(KEY, t);
    }
}
