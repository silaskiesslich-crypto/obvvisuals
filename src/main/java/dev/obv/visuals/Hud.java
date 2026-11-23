package dev.obv.visuals;

import dev.obv.visuals.Model.HudElem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.List;

/** HUD: FPS, CPS, Koordinaten, Rüstung. */
public final class Hud {
    private Hud() {}

    private static final ArrayDeque<Long> LEFT = new ArrayDeque<>();
    private static final ArrayDeque<Long> RIGHT = new ArrayDeque<>();
    private static boolean lastL, lastR;

    private static void poll(MinecraftClient mc) {
        long now = System.currentTimeMillis();
        if (mc.getWindow() != null && mc.currentScreen == null) {
            long h = mc.getWindow().getHandle();
            boolean l = GLFW.glfwGetMouseButton(h, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
            boolean r = GLFW.glfwGetMouseButton(h, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
            if (l && !lastL && LEFT.size() < 100) LEFT.addLast(now);
            if (r && !lastR && RIGHT.size() < 100) RIGHT.addLast(now);
            lastL = l; lastR = r;
        }
        while (!LEFT.isEmpty() && now - LEFT.peekFirst() > 1000) LEFT.pollFirst();
        while (!RIGHT.isEmpty() && now - RIGHT.peekFirst() > 1000) RIGHT.pollFirst();
    }

    public static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;
        poll(mc);
        Model.Hud h = Store.active().hud;
        TextRenderer tr = mc.textRenderer;
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();

        if (h.fps.enabled) box(ctx, tr, h, h.fps, sw, sh, List.of("FPS: " + mc.getCurrentFps()));
        if (h.cps.enabled) box(ctx, tr, h, h.cps, sw, sh, List.of("CPS: " + LEFT.size() + " | " + RIGHT.size()));
        if (h.coords.enabled) {
            var p = mc.player;
            box(ctx, tr, h, h.coords, sw, sh, List.of(
                    String.format("XYZ: %.1f / %.1f / %.1f", p.getX(), p.getY(), p.getZ()),
                    "Blick: " + p.getHorizontalFacing().asString()));
        }
        if (h.armor.enabled) armor(ctx, tr, mc, h, sw, sh);
    }

    private static void box(DrawContext ctx, TextRenderer tr, Model.Hud h, HudElem e, int sw, int sh, List<String> lines) {
        int w = 0;
        for (String l : lines) w = Math.max(w, tr.getWidth(l));
        w += 10;
        int hgt = lines.size() * 10 + 6;
        float s = e.scale;
        int px = Math.max(0, Math.min((int) (e.x * sw), sw - (int) (w * s)));
        int py = Math.max(0, Math.min((int) (e.y * sh), sh - (int) (hgt * s)));
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(px, py);
        m.scale(s, s);
        Gfx.rrect(ctx, 0, 0, w, hgt, 3, ((int) (h.bgAlpha * 255) << 24) | 0x08080C);
        ctx.fill(0, 2, 2, hgt - 2, 0xFF000000 | e.color);
        int y = 4;
        for (String l : lines) { ctx.drawText(tr, l, 6, y, 0xFF000000 | e.color, h.shadow); y += 10; }
        m.popMatrix();
    }

    private static void armor(DrawContext ctx, TextRenderer tr, MinecraftClient mc, Model.Hud h, int sw, int sh) {
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        HudElem e = h.armor;
        float s = e.scale;
        int w = 52, hgt = 4 * 18 + 4;
        int px = Math.max(0, Math.min((int) (e.x * sw), sw - (int) (w * s)));
        int py = Math.max(0, Math.min((int) (e.y * sh), sh - (int) (hgt * s)));
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(px, py);
        m.scale(s, s);
        Gfx.rrect(ctx, 0, 0, w, hgt, 3, ((int) (h.bgAlpha * 255) << 24) | 0x08080C);
        int y = 3;
        for (EquipmentSlot slot : slots) {
            ItemStack st = mc.player.getEquippedStack(slot);
            if (!st.isEmpty()) {
                ctx.drawItem(st, 3, y);
                if (st.isDamageable()) {
                    int left = st.getMaxDamage() - st.getDamage();
                    float f = left / (float) st.getMaxDamage();
                    int col = f > 0.5f ? 0xFF55FF55 : f > 0.2f ? 0xFFFFAA00 : 0xFFFF5555;
                    ctx.drawText(tr, String.valueOf(left), 22, y + 4, col, h.shadow);
                }
            }
            y += 18;
        }
        m.popMatrix();
    }
}
