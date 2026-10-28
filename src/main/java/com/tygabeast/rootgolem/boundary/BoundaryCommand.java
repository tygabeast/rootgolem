package com.tygabeast.rootgolem.boundary;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.tygabeast.rootgolem.RootGolemMod;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import com.mojang.authlib.GameProfile;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.world.entity.Entity;
import com.tygabeast.rootgolem.entity.RootGolem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * Operator commands:
 *   /rootgolem boundary create <y> <x z, x z, x z ...>  places markers at those columns and closes the loop
 *   /rootgolem boundary list                             lists boundaries in the current dimension
 *   /rootgolem boundary expand <after> <pos>             inserts a marker at pos right after the marker at after
 *   /rootgolem boundary spacing <marker> <blocks>|default sets a boundary's tree spacing
 *   /rootgolem boundary owner <marker> <player>|none     sets or clears a boundary's owner
 *   /rootgolem golem assign <golems> <marker>            assigns golems to the closed boundary that marker belongs to
 */
public final class BoundaryCommand {
    private BoundaryCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(RootGolemMod.MODID)
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("boundary")
                        .then(Commands.literal("create")
                                .then(Commands.argument("y", IntegerArgumentType.integer())
                                        .then(Commands.argument("points", StringArgumentType.greedyString())
                                                .executes(c -> create(c.getSource(),
                                                        IntegerArgumentType.getInteger(c, "y"),
                                                        StringArgumentType.getString(c, "points"))))))
                        .then(Commands.literal("list").executes(c -> list(c.getSource())))
                        .then(Commands.literal("spacing")
                                .then(Commands.argument("marker", BlockPosArgument.blockPos())
                                        .then(Commands.argument("blocks", IntegerArgumentType.integer(0, 16))
                                                .executes(c -> spacing(c.getSource(), BlockPosArgument.getLoadedBlockPos(c, "marker"),
                                                        IntegerArgumentType.getInteger(c, "blocks"))))
                                        .then(Commands.literal("default")
                                                .executes(c -> spacing(c.getSource(), BlockPosArgument.getLoadedBlockPos(c, "marker"), -1)))))
                        .then(Commands.literal("owner")
                                .then(Commands.argument("marker", BlockPosArgument.blockPos())
                                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                                .executes(c -> owner(c.getSource(),
                                                        BlockPosArgument.getLoadedBlockPos(c, "marker"),
                                                        GameProfileArgument.getGameProfiles(c, "player"))))
                                        .then(Commands.literal("none")
                                                .executes(c -> owner(c.getSource(),
                                                        BlockPosArgument.getLoadedBlockPos(c, "marker"), List.of())))))
                        .then(Commands.literal("expand")
                                .then(Commands.argument("after", BlockPosArgument.blockPos())
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                .executes(c -> expand(c.getSource(),
                                                        BlockPosArgument.getLoadedBlockPos(c, "after"),
                                                        BlockPosArgument.getLoadedBlockPos(c, "pos")))))))
                .then(Commands.literal("golem")
                        .then(Commands.literal("assign")
                                .then(Commands.argument("golems", EntityArgument.entities())
                                        .then(Commands.argument("marker", BlockPosArgument.blockPos())
                                                .executes(c -> assign(c.getSource(),
                                                        EntityArgument.getEntities(c, "golems"),
                                                        BlockPosArgument.getLoadedBlockPos(c, "marker"))))))));
    }

    /** Inserts a new marker at {@code pos} right after the existing marker at {@code after}. */
    private static int expand(CommandSourceStack src, BlockPos after, BlockPos pos) {
        ServerLevel level = src.getLevel();
        BoundaryData data = BoundaryData.get(level);
        Boundary b = data.atMarker(after);
        if (b == null) {
            src.sendFailure(Component.literal("No boundary marker at " + after.toShortString()));
            return 0;
        }
        if (data.atMarker(pos) != null) {
            src.sendFailure(Component.literal("There is already a marker at " + pos.toShortString()));
            return 0;
        }
        int index = b.points.indexOf(after);
        Component problem = BoundaryMarkerBlock.insertProblem(b, index, pos);
        if (problem != null) {
            src.sendFailure(problem);
            return 0;
        }
        level.setBlockAndUpdate(pos, RootGolemMod.BOUNDARY_MARKER.get().defaultBlockState());
        data.insertAfter(b, index, pos);
        src.sendSuccess(() -> Component.literal("Inserted marker; boundary now has " + b.points.size() + " markers"), true);
        return 1;
    }

    private static int spacing(CommandSourceStack src, BlockPos marker, int blocks) {
        BoundaryData data = BoundaryData.get(src.getLevel());
        Boundary b = data.atMarker(marker);
        if (b == null) {
            src.sendFailure(Component.literal("No boundary marker at " + marker.toShortString()));
            return 0;
        }
        data.setSpacing(b, blocks);
        src.sendSuccess(() -> Component.literal("Tree spacing for this boundary: " + b.spacing() + (blocks < 0 ? " (server default)" : "")), true);
        return 1;
    }

    private static int owner(CommandSourceStack src, BlockPos marker, java.util.Collection<GameProfile> profiles) {
        BoundaryData data = BoundaryData.get(src.getLevel());
        Boundary b = data.atMarker(marker);
        if (b == null) {
            src.sendFailure(Component.literal("No boundary marker at " + marker.toShortString()));
            return 0;
        }
        if (profiles.size() > 1) {
            src.sendFailure(Component.literal("Pick one player"));
            return 0;
        }
        GameProfile p = profiles.isEmpty() ? null : profiles.iterator().next();
        data.setOwner(b, p == null ? null : p.getId());
        src.sendSuccess(() -> Component.literal(p == null ? "Boundary is now unowned" : "Boundary now belongs to " + p.getName()), true);
        return 1;
    }

    private static int assign(CommandSourceStack src, java.util.Collection<? extends Entity> targets, BlockPos marker) {
        Boundary b = BoundaryData.get(src.getLevel()).atMarker(marker);
        if (b == null || !b.isClosed()) {
            src.sendFailure(Component.literal("No closed boundary has a marker at " + marker.toShortString()));
            return 0;
        }
        int n = 0;
        for (Entity e : targets) {
            if (e instanceof RootGolem golem && golem.level() == src.getLevel()) {
                golem.assignTo(b);
                n++;
            }
        }
        int count = n;
        src.sendSuccess(() -> Component.literal("Assigned " + count + " Root Golem(s)"), true);
        return n;
    }

    private static int create(CommandSourceStack src, int y, String text) {
        List<BlockPos> pts = new ArrayList<>();
        for (String part : text.split(",")) {
            String[] xz = part.trim().split("\\s+");
            if (xz.length != 2) {
                src.sendFailure(Component.literal("Points must look like: x z, x z, x z"));
                return 0;
            }
            try {
                pts.add(new BlockPos(Integer.parseInt(xz[0]), y, Integer.parseInt(xz[1])));
            } catch (NumberFormatException e) {
                src.sendFailure(Component.literal("Not a number in: " + part.trim()));
                return 0;
            }
        }
        if (pts.size() < 3) {
            src.sendFailure(Component.literal("A boundary needs at least 3 markers"));
            return 0;
        }
        ServerLevel level = src.getLevel();
        for (BlockPos p : pts) {
            if (!level.isLoaded(p)) {
                src.sendFailure(Component.literal("Not loaded: " + p.toShortString()));
                return 0;
            }
        }
        for (BlockPos p : pts) level.setBlockAndUpdate(p, RootGolemMod.BOUNDARY_MARKER.get().defaultBlockState());
        BoundaryData data = BoundaryData.get(level);
        Boundary b = data.create(pts.get(0), null); // unowned: the first player to edit it claims it
        for (int i = 1; i < pts.size(); i++) data.append(b, pts.get(i));
        data.close(b);
        src.sendSuccess(() -> Component.literal("Created closed boundary with " + pts.size() + " markers"), true);
        return pts.size();
    }

    private static int list(CommandSourceStack src) {
        BoundaryData data = BoundaryData.get(src.getLevel());
        int n = 0;
        for (Boundary b : data.all()) {
            n++;
            String s = (b.isClosed() ? "closed" : "open") + ", " + b.points.size() + " markers, x " + b.minX() + ".." + b.maxX()
                    + " z " + b.minZ() + ".." + b.maxZ() + ", spacing " + b.spacing()
                    + (b.owner == null ? ", unowned" : ", owner " + BoundaryAccess.ownerName(src.getServer(), b.owner));
            int[] g = data.golemCounts(b.id, src.getLevel().getGameTime());
            String line = s + ", golems working " + g[0] + " (" + g[1] + " found no room)";
            src.sendSuccess(() -> Component.literal(line), false);
        }
        if (n == 0) src.sendSuccess(() -> Component.literal("No boundaries in this dimension"), false);
        return n;
    }
}
