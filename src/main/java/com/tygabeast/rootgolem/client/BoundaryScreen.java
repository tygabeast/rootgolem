package com.tygabeast.rootgolem.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.math.Axis;
import com.tygabeast.rootgolem.RootGolemMod;
import com.tygabeast.rootgolem.boundary.Boundary;
import com.tygabeast.rootgolem.network.BoundaryScreenPayloads;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Planting Boundary screen: a map of the boundary, its details, and the tree spacing presets.
 *
 * Cost: the map (interior, outline, trees) is drawn once into a 232x232 texture whenever data
 * arrives from the server; each frame only blits it and draws markers, golems, the player arrow and
 * text. Nothing is requested while the screen is open, except when a spacing button is pressed.
 */
public class BoundaryScreen extends Screen {
    private static final ResourceLocation TEX = RootGolemMod.id("textures/gui/boundary.png");
    private static final ResourceLocation MAP_TEX = RootGolemMod.id("dynamic/boundary_map");
    private static final int W = 264, H = 184;
    private static final int MAP_X = 14, MAP_Y = 30, MAP_SIZE = 116, MAP_RES = 232, MAP_MARGIN = 10;
    private static final int[] PRESET_U = {272, 308, 328, 348, 368};
    private static final int[] PRESET_W = {34, 18, 18, 18, 18};
    private static final int[] PRESET_X = {140, 176, 196, 216, 236};
    private static final int TEXT = 0xFFECE0C4, DIM = 0xFFB4A482, WARN = 0xFFE07060;
    private static final double REACH_SQR = 12.0D * 12.0D;

    private BoundaryScreenPayloads.Open data;
    private DynamicTexture mapTexture;
    private int left, top;
    // Map transform: texel = offset + (block - min + 0.5) * scale
    private int minX, minZ;
    private float scale, offX, offZ;
    private final List<PresetButton> presets = new ArrayList<>();

    public BoundaryScreen(BoundaryScreenPayloads.Open data) {
        super(Component.translatable("screen.rootgolem.title"));
        this.data = data;
    }

    /** Opens the screen, or refreshes it in place if it is already open for the same boundary. */
    static void show(BoundaryScreenPayloads.Open payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof BoundaryScreen s && s.data.points().equals(payload.points())) {
            s.update(payload);
        } else {
            mc.setScreen(new BoundaryScreen(payload));
        }
    }

    private void update(BoundaryScreenPayloads.Open payload) {
        data = payload;
        buildMap();
        for (PresetButton b : presets) b.active = data.canEdit();
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        presets.clear();
        for (int i = 0; i < Boundary.SPACING_PRESETS.length; i++) {
            PresetButton b = new PresetButton(i);
            b.active = data.canEdit();
            presets.add(addRenderableWidget(b));
        }
        addRenderableWidget(new DoneButton(left + 190, top + 154));
        buildMap();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        LocalPlayer p = minecraft.player;
        BlockPos m = data.marker();
        if (p == null || p.distanceToSqr(m.getX() + 0.5D, m.getY() + 0.5D, m.getZ() + 0.5D) > REACH_SQR) onClose();
    }

    @Override
    public void removed() {
        if (mapTexture != null) {
            minecraft.getTextureManager().release(MAP_TEX);   // also frees the native image
            mapTexture = null;
        }
    }

    // ---- map texture --------------------------------------------------------------------------

    private void buildMap() {
        List<BlockPos> pts = data.points();
        int n = pts.size();
        if (n < 3) return;
        double[] xs = new double[n], zs = new double[n];
        int maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        minX = minZ = Integer.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            BlockPos p = pts.get(i);
            xs[i] = p.getX() + 0.5D; zs[i] = p.getZ() + 0.5D;
            minX = Math.min(minX, p.getX()); maxX = Math.max(maxX, p.getX());
            minZ = Math.min(minZ, p.getZ()); maxZ = Math.max(maxZ, p.getZ());
        }
        int bw = maxX - minX + 1, bd = maxZ - minZ + 1;
        scale = Math.min((MAP_RES - 2f * MAP_MARGIN) / bw, (MAP_RES - 2f * MAP_MARGIN) / bd);
        offX = (MAP_RES - bw * scale) / 2f;
        offZ = (MAP_RES - bd * scale) / 2f;

        boolean[] inside = new boolean[bw * bd];
        for (int cz = 0; cz < bd; cz++)
            for (int cx = 0; cx < bw; cx++) inside[cz * bw + cx] = contains(xs, zs, minX + cx + 0.5D, minZ + cz + 0.5D);

        NativeImage img = new NativeImage(MAP_RES, MAP_RES, false);
        int bg = abgr(58, 74, 40);
        for (int ty = 0; ty < MAP_RES; ty++) {
            for (int tx = 0; tx < MAP_RES; tx++) {
                int cx = Mth.floor((tx + 0.5f - offX) / scale), cz = Mth.floor((ty + 0.5f - offZ) / scale);
                int c = bg;
                if (cx >= 0 && cz >= 0 && cx < bw && cz < bd && inside[cz * bw + cx]) {
                    c = ((cx / 2 + cz / 2) & 1) == 0 ? abgr(100, 134, 56) : abgr(108, 142, 60);
                }
                img.setPixelRGBA(tx, ty, c);
            }
        }
        float r = Math.max(1.5f, scale * 0.45f);
        for (int i = 0; i < data.treeColumns().size(); i++) {
            int packed = data.treeColumns().getInt(i);
            disc(img, offX + (BoundaryScreenPayloads.unpackX(packed) + 0.5f) * scale,
                    offZ + (BoundaryScreenPayloads.unpackZ(packed) + 0.5f) * scale, r, abgr(36, 72, 26));
        }
        int line = abgr(196, 236, 120);
        for (int i = 0; i < n; i++) {
            BlockPos a = pts.get(i), b = pts.get((i + 1) % n);
            line(img, tx(a.getX()), tz(a.getZ()), tx(b.getX()), tz(b.getZ()), line);
        }

        if (mapTexture == null) {
            mapTexture = new DynamicTexture(img);
            minecraft.getTextureManager().register(MAP_TEX, mapTexture);
        } else {
            mapTexture.setPixels(img);                      // closes the previous image
            mapTexture.upload();
        }
    }

    private float tx(int blockX) { return offX + (blockX - minX + 0.5f) * scale; }
    private float tz(int blockZ) { return offZ + (blockZ - minZ + 0.5f) * scale; }

    /** Same rule as the server: strictly inside, and not on the line between two markers. */
    private static boolean contains(double[] xs, double[] zs, double px, double pz) {
        boolean in = false;
        for (int i = 0, j = xs.length - 1; i < xs.length; j = i++) {
            double dx = xs[i] - xs[j], dz = zs[i] - zs[j], len = dx * dx + dz * dz;
            double t = len == 0 ? 0 : Mth.clamp(((px - xs[j]) * dx + (pz - zs[j]) * dz) / len, 0, 1);
            double ex = xs[j] + dx * t - px, ez = zs[j] + dz * t - pz;
            if (ex * ex + ez * ez < 0.25D) return false;
            if ((zs[i] > pz) != (zs[j] > pz) && px < (xs[j] - xs[i]) * (pz - zs[i]) / (zs[j] - zs[i]) + xs[i]) in = !in;
        }
        return in;
    }

    private static void disc(NativeImage img, float cx, float cy, float r, int c) {
        for (int y = (int) (cy - r); y <= (int) (cy + r); y++)
            for (int x = (int) (cx - r); x <= (int) (cx + r); x++)
                if (x >= 0 && y >= 0 && x < MAP_RES && y < MAP_RES && (x + 0.5f - cx) * (x + 0.5f - cx) + (y + 0.5f - cy) * (y + 0.5f - cy) <= r * r)
                    img.setPixelRGBA(x, y, c);
    }

    private static void line(NativeImage img, float x0, float y0, float x1, float y1, int c) {
        int steps = (int) Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)) + 1;
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            int x = (int) (x0 + (x1 - x0) * t), y = (int) (y0 + (y1 - y0) * t);
            for (int oy = 0; oy < 2; oy++)
                for (int ox = 0; ox < 2; ox++)
                    if (x + ox >= 0 && y + oy >= 0 && x + ox < MAP_RES && y + oy < MAP_RES) img.setPixelRGBA(x + ox, y + oy, c);
        }
    }

    private static int abgr(int r, int g, int b) {
        return 0xFF000000 | (b << 16) | (g << 8) | r;
    }

    // ---- drawing ------------------------------------------------------------------------------

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.blit(TEX, left, top, 0, 0, W, H, 512, 256);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);       // background + buttons
        Font f = font;
        g.drawString(f, title, left + 36, top + 15, TEXT, true);
        drawMap(g);
        small(g, Component.translatable("screen.rootgolem.map_caption"), left + MAP_X, top + 150, DIM);

        int y = top + 31;
        stat(g, "markers", Component.literal(String.valueOf(data.points().size())), y);
        stat(g, "area", Component.translatable("screen.rootgolem.area_value", String.format("%,d", data.area())), y += 11);
        stat(g, "size", Component.translatable("screen.rootgolem.size_value", width(), depth()), y += 11);
        stat(g, "owner", data.owner().isEmpty() ? Component.translatable("screen.rootgolem.nobody") : Component.literal(data.owner()), y += 11);
        stat(g, "golems", data.golems() == 0 ? Component.translatable("screen.rootgolem.golems_none")
                : data.fullGolems() > 0 ? Component.translatable("screen.rootgolem.golems_full", data.golems(), data.fullGolems())
                : Component.translatable("screen.rootgolem.golems_working", data.golems()), y += 11);

        g.drawString(f, Component.translatable("screen.rootgolem.spacing"), left + 140, top + 88, TEXT, true);
        int spacing = data.spacingRaw() < 0 ? data.spacingDefault() : data.spacingRaw();
        small(g, data.spacingRaw() < 0 ? Component.translatable("screen.rootgolem.spacing_line_default", spacing)
                : Component.translatable("screen.rootgolem.spacing_line", spacing), left + 140, top + 122, DIM);
        small(g, data.room() == 0 ? Component.translatable("screen.rootgolem.room_none")
                : data.room() == 1 ? Component.translatable("screen.rootgolem.room_one")
                : Component.translatable("screen.rootgolem.room", data.room()), left + 140, top + 131, DIM);
        if (!data.canEdit()) {
            Component who = data.owner().isEmpty() ? Component.translatable("screen.rootgolem.nobody") : Component.literal(data.owner());
            small(g, Component.translatable("screen.rootgolem.read_only", who), left + 140, top + 142, WARN);
        }
    }

    private int width() {
        int a = Integer.MAX_VALUE, b = Integer.MIN_VALUE;
        for (BlockPos p : data.points()) { a = Math.min(a, p.getX()); b = Math.max(b, p.getX()); }
        return b - a + 1;
    }

    private int depth() {
        int a = Integer.MAX_VALUE, b = Integer.MIN_VALUE;
        for (BlockPos p : data.points()) { a = Math.min(a, p.getZ()); b = Math.max(b, p.getZ()); }
        return b - a + 1;
    }

    /** Label in dim text, value in light text, cut to fit the panel. */
    private void stat(GuiGraphics g, String key, Component value, int y) {
        g.drawString(font, Component.translatable("screen.rootgolem." + key), left + 140, y, DIM, true);
        int x = left + 180, max = left + 254 - x;
        String s = value.getString();
        if (font.width(s) > max) s = font.plainSubstrByWidth(s, max - font.width("...")) + "...";
        g.drawString(font, s, x, y, TEXT, true);
    }

    /** Text at 3/4 size (secondary lines), so longer sentences fit the right-hand column. */
    private void small(GuiGraphics g, Component c, int x, int y, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.75F, 0.75F, 1F);
        g.drawString(font, c, 0, 0, color, true);
        g.pose().popPose();
    }

    private void drawMap(GuiGraphics g) {
        int mx = left + MAP_X, my = top + MAP_Y;
        if (mapTexture != null) g.blit(MAP_TEX, mx, my, MAP_SIZE, MAP_SIZE, 0, 0, MAP_RES, MAP_RES, MAP_RES, MAP_RES);

        // Golems: small rooted-dirt figure with a mossy top and green eyes.
        for (int i = 0; i < data.golemColumns().size(); i++) {
            int p = data.golemColumns().getInt(i);
            int gx = mx + (int) ((offX + (BoundaryScreenPayloads.unpackX(p) + 0.5f) * scale) / 2);
            int gy = my + (int) ((offZ + (BoundaryScreenPayloads.unpackZ(p) + 0.5f) * scale) / 2);
            g.fill(gx - 2, gy - 3, gx + 3, gy + 3, 0xFF62462F);
            g.fill(gx - 2, gy - 3, gx + 3, gy - 1, 0xFF688434);
            g.fill(gx - 1, gy, gx, gy + 1, 0xFFBAFF96);
            g.fill(gx + 1, gy, gx + 2, gy + 1, 0xFFBAFF96);
        }

        // Markers: glowing bulbs with their numbers (numbers only while there's room for them).
        List<BlockPos> pts = data.points();
        for (int i = 0; i < pts.size(); i++) {
            BlockPos p = pts.get(i);
            int x = mx + (int) (tx(p.getX()) / 2), y = my + (int) (tz(p.getZ()) / 2);
            g.fill(x - 2, y - 2, x + 3, y + 3, 0xFF1E140C);
            g.fill(x - 1, y - 1, x + 2, y + 2, 0xFFFAAA50);
            if (pts.size() <= 24) {
                g.pose().pushPose();
                g.pose().translate(x + 3, y + 2, 0);
                g.pose().scale(0.5F, 0.5F, 1F);
                g.drawString(font, String.valueOf(i + 1), 0, 0, 0xFFFFF0C8, true);
                g.pose().popPose();
            }
        }

        // You: an arrow pointing the way you face, kept inside the map frame.
        LocalPlayer pl = minecraft.player;
        if (pl != null) {
            float px = Mth.clamp((float) (offX + (pl.getX() - minX) * scale) / 2, 3, MAP_SIZE - 3);
            float pz = Mth.clamp((float) (offZ + (pl.getZ() - minZ) * scale) / 2, 3, MAP_SIZE - 3);
            g.pose().pushPose();
            g.pose().translate(mx + px, my + pz, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(pl.getYRot() + 180F));
            g.fill(0, -3, 1, -1, 0xFFFFFFFF);
            g.fill(-1, -1, 2, 1, 0xFFFFFFFF);
            g.fill(-2, 1, 0, 2, 0xFFFFFFFF);
            g.fill(1, 1, 3, 2, 0xFFFFFFFF);
            g.pose().popPose();
        }
    }

    // ---- buttons ------------------------------------------------------------------------------

    /** One tree spacing preset: a wooden tag with saplings; grows moss when selected. */
    private class PresetButton extends AbstractButton {
        private final int index;

        PresetButton(int index) {
            super(left + PRESET_X[index], top + 100, PRESET_W[index], 18, label(index));
            this.index = index;
            int value = Boundary.SPACING_PRESETS[index];
            setTooltip(Tooltip.create(value < 0
                    ? Component.translatable("screen.rootgolem.preset.default", data.spacingDefault())
                    : Component.translatable("screen.rootgolem.preset." + value)));
        }

        private static Component label(int index) {
            int v = Boundary.SPACING_PRESETS[index];
            return v < 0 ? Component.translatable("screen.rootgolem.preset_default_label") : Component.literal(String.valueOf(v));
        }

        private boolean selected() {
            return data.spacingRaw() == Boundary.SPACING_PRESETS[index];
        }

        @Override
        public void onPress() {
            if (selected()) return;
            BlockPos marker = data.marker();
            PacketDistributor.sendToServer(new BoundaryScreenPayloads.SetSpacing(marker, Boundary.SPACING_PRESETS[index]));
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int state = selected() ? 2 : !active ? 3 : isHoveredOrFocused() ? 1 : 0;
            g.blit(TEX, getX(), getY(), PRESET_U[index], state * 20, width, height, 512, 256);
            int color = state == 2 ? 0xFFFFF8BE : state == 3 ? 0xFF968C80 : 0xFFFAF0DC;
            if (index == 0) {
                g.pose().pushPose();
                g.pose().translate(getX() + width / 2F, getY() + 6, 0);
                g.pose().scale(0.75F, 0.75F, 1F);
                g.drawString(font, getMessage(), -font.width(getMessage()) / 2, 0, color, true);
                g.pose().popPose();
            } else {
                g.drawCenteredString(font, getMessage(), getX() + width / 2, getY() + 8, color);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
            defaultButtonNarrationText(out);
        }
    }

    private class DoneButton extends AbstractButton {
        DoneButton(int x, int y) {
            super(x, y, 62, 18, CommonComponents.GUI_DONE);
        }

        @Override
        public void onPress() {
            onClose();
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            g.blit(TEX, getX(), getY(), 272, isHoveredOrFocused() ? 110 : 90, width, height, 512, 256);
            g.drawCenteredString(font, getMessage(), getX() + width / 2, getY() + 5, 0xFFFAF0DC);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) {
            defaultButtonNarrationText(out);
        }
    }
}
