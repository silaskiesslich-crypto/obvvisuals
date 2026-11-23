package dev.obv.visuals;

import net.minecraft.client.gui.DrawContext;

/** Kleine Zeichenhilfen (abgerundete Rechtecke, Verläufe, Textur-Raster). Nur normale DrawContext-Aufrufe. */
public final class Gfx {
    private Gfx() {}

    public static int alpha(int col, float a) {
        int ca = (col >>> 24);
        if (ca == 0) ca = 255;
        int na = Math.max(0, Math.min(255, Math.round(ca * a)));
        return (na << 24) | (col & 0xFFFFFF);
    }

    public static int mix(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = a >>> 24, ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int ba = b >>> 24, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return (Math.round(aa + (ba - aa) * t) << 24) | (Math.round(ar + (br - ar) * t) << 16)
                | (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
    }

    public static void rrect(DrawContext c, int x, int y, int w, int h, int r, int col) {
        if (w <= 0 || h <= 0) return;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (r == 0) { c.fill(x, y, x + w, y + h, col); return; }
        c.fill(x, y + r, x + w, y + h - r, col);
        for (int i = 0; i < r; i++) {
            double d = r - i - 0.5;
            int inset = r - (int) Math.round(Math.sqrt(r * (double) r - d * d));
            c.fill(x + inset, y + i, x + w - inset, y + i + 1, col);
            c.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, col);
        }
    }

    /** Horizontaler Verlauf in groben Streifen. */
    public static void hgrad(DrawContext c, int x, int y, int w, int h, int a, int b) {
        int step = 3;
        for (int i = 0; i < w; i += step) {
            c.fill(x + i, y, x + Math.min(w, i + step), y + h, mix(a, b, w <= 1 ? 0 : i / (float) (w - 1)));
        }
    }

    public static void drawGrid(DrawContext c, Imgs.Grid g, int x, int y, int bw, int bh, float fade) {
        c.fill(x, y, x + bw, y + bh, alpha(0xFF101018, fade));
        if (g == null) return;
        int cell = Math.max(1, Math.min(bw / g.w, bh / g.h));
        int ox = x + (bw - cell * g.w) / 2, oy = y + (bh - cell * g.h) / 2;
        for (int gy = 0; gy < g.h; gy++) for (int gx = 0; gx < g.w; gx++) {
            int p = g.px[gy * g.w + gx];
            int a = p >>> 24;
            if (a < 12) {
                if (((gx + gy) & 1) == 0) c.fill(ox + gx * cell, oy + gy * cell, ox + gx * cell + cell, oy + gy * cell + cell, alpha(0xFF1C1C26, fade));
                continue;
            }
            int col = mix(0xFF181822, 0xFF000000 | (p & 0xFFFFFF), a / 255f);
            c.fill(ox + gx * cell, oy + gy * cell, ox + gx * cell + cell, oy + gy * cell + cell, alpha(col, fade));
        }
    }
}
