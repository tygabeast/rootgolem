package com.tygabeast.rootgolem.boundary;

import com.mojang.authlib.GameProfile;
import com.tygabeast.rootgolem.RootGolemConfig;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Who may change a boundary (and the golems tending it).
 *
 * With ownerOnly on (the default), only the boundary's owner and operators may extend, close,
 * re-space or break it, or assign and manage its golems. An unowned boundary (made by a command or
 * before 1.3.0) is claimed by the first player who edits it.
 */
public final class BoundaryAccess {
    private BoundaryAccess() {}

    /** Read-only check: may this player change the boundary? */
    public static boolean canEdit(ServerPlayer player, Boundary b) {
        return !RootGolemConfig.OWNER_ONLY.get() || b.owner == null
                || b.owner.equals(player.getUUID()) || player.hasPermissions(2);
    }

    /**
     * Checks permission for an edit about to happen, claiming an unowned boundary for the player.
     * Sends the "belongs to" message and returns false when not allowed.
     */
    public static boolean tryEdit(ServerPlayer player, ServerLevel level, Boundary b) {
        if (!canEdit(player, b)) {
            player.displayClientMessage(Component.translatable("message.rootgolem.not_owner", ownerName(level.getServer(), b.owner)), true);
            return false;
        }
        if (b.owner == null) BoundaryData.get(level).setOwner(b, player.getUUID());
        return true;
    }

    public static String ownerName(MinecraftServer server, @Nullable UUID owner) {
        if (owner == null) return "";
        var cache = server.getProfileCache();
        return cache == null ? owner.toString().substring(0, 8)
                : cache.get(owner).map(GameProfile::getName).orElse(owner.toString().substring(0, 8));
    }
}
