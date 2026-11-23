package dev.obv.visuals;

import dev.obv.visuals.Model.*;
import dev.obv.visuals.Slots.Slot;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** Das OBV VISUALS Menü. Immediate-Mode-UI: alle Elemente werden pro Frame gezeichnet und als Klickflächen registriert. */
public class ObvScreen extends Screen {

    // ---------- Design ----------
    private static final int[][] THEMES = {{0xFF4F8CFF, 0xFF8B5CF6}, {0xFF22D3EE, 0xFFA855F7}, {0xFF3B82F6, 0xFFEC4899}, {0xFF6366F1, 0xFF06B6D4}};
    private static final String[] THEME_NAMES = {"Blau / Violett", "Cyan / Lila", "Blau / Pink", "Indigo / Türkis"};
    private static final int TEXT = 0xFFE8E8F2, DIM = 0xFF8A8AA2, BG = 0xFF0B0B11, CARD = 0xFF14141D, CARD2 = 0xFF1D1D29;
    private static final int[] PALETTE = {0xFFFFFF, 0x3B82F6, 0x8B5CF6, 0x22D3EE, 0x34D399, 0xFACC15, 0xF97316, 0xEF4444, 0xEC4899, 0x15151C};

    private static final String[][] ICONS = {
            {"...##...", "..####..", ".######.", "########", ".######.", "..####..", "...##...", "........"}, // crystals
            {"..####..", ".#....#.", "..####..", "...##...", "#..##..#", "#..##..#", ".######.", "..####.."}, // anchors
            {"......##", "....###.", "...###..", "#.###...", ".###....", "..#.....", ".##.....", "#.#....."}, // items
            {"##.##.##", "########", "#.####.#", "..####..", "..####..", "..####..", "..####..", "........"}, // armor
            {"...#....", "..###...", "...#..#.", "......#.", ".#...###", "###...#.", ".#......", "........"}, // particles
            {"..####..", ".##.....", "##......", "##......", "##......", ".##.....", "..####..", "........"}, // sky
            {"########", "#......#", "#.##...#", "#......#", "#...##.#", "#......#", "########", "..####.."}, // hud
            {"...##...", "...##...", "########", ".######.", "..####..", "..#..#..", ".#....#.", "#......#"}, // extras
            {"..####..", ".######.", "########", ".######.", "..####..", "########", ".######.", "..####.."}, // mixer
            {"..####..", "..####..", "..####..", "...##...", ".######.", "########", "########", "########"}, // profiles
            {"..#..#..", ".######.", "###..###", ".#....#.", ".#....#.", "###..###", ".######.", "..#..#.."}  // settings
    };

    private enum Tab {
        CRYSTALS("Crystals", Slots.Cat.CRYSTALS), ANCHORS("Anchors", Slots.Cat.ANCHORS), ITEMS("Items", Slots.Cat.ITEMS),
        ARMOR("Armor", Slots.Cat.ARMOR), PARTICLES("Particles", Slots.Cat.PARTICLES), SKY("Sky", Slots.Cat.SKY),
        HUD("HUD", null), EXTRAS("Extras", null), MIXER("Pack Mixer", null), PROFILES("Profile", null), SETTINGS("Einstellungen", null);
        final String label; final Slots.Cat cat;
        Tab(String l, Slots.Cat c) { label = l; cat = c; }
    }

    private enum Edit { NONE, SEARCH, NEW_PROFILE, RENAME_PROFILE }

    private static final class Hit { int x, y, w, h, cy1, cy2; IntConsumer press, drag; Runnable release; }
    private record Area(int x, int y, int w, int h, float[] v, float max) {}

    // ---------- Zustand ----------
    private Tab tab = Tab.CRYSTALS;
    private String search = "";
    private Edit editing = Edit.NONE;
    private String editBuf = "";
    private int capturing = 0; // 1 Menü, 2 Zoom, 3 Profilwechsel
    private Slot selected;
    private String selProfile;
    private String confirmDelete = "";
    private String mixPack;
    private List<Slot> mixProvided = List.of();
    private boolean dirty;
    private String toast = ""; private long toastUntil;

    private final List<Hit> hits = new ArrayList<>();
    private final List<Area> areas = new ArrayList<>();
    private final Map<String, Float> anims = new HashMap<>();
    private final Map<String, float[]> scrolls = new HashMap<>();
    private Hit active;
    private long lastNano = System.nanoTime();
    private long openedAt = System.nanoTime();
    private float dt, fade = 0f;
    private int mx, my, clipY1, clipY2;
    private int lx, lw, ly, lTop, lBot, lStart; private String lKey = "";

    public ObvScreen() { super(Text.literal("OBV VISUALS")); }

    // ---------- Hilfen ----------
    private Profile prof() { return Store.active(); }
    private Settings cfg() { return Store.settings; }
    private int ac(int i) { return THEMES[cfg().theme % THEMES.length][i]; }
    private int col(int c) { return Gfx.alpha(c, fade); }
    private float[] sc(String k) { return scrolls.computeIfAbsent(k, x -> new float[1]); }
    private void notify(String s) { toast = s; toastUntil = System.currentTimeMillis() + 3500; }
    private void markDirty() { dirty = true; Store.saveProfile(prof()); }
    private void saveProf() { Store.saveProfile(prof()); }

    private float an(String key, boolean on) {
        float t = on ? 1f : 0f;
        if (!cfg().animations) { anims.put(key, t); return t; }
        float v = anims.getOrDefault(key, 0f);
        v += (t - v) * Math.min(1f, dt * 16f);
        anims.put(key, v);
        return v;
    }

    private boolean hover(int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h && my >= clipY1 && my < clipY2;
    }

    private Hit hit(int x, int y, int w, int h, Runnable r) { return hitDrag(x, y, w, h, m -> r.run(), null, null); }

    private Hit hitDrag(int x, int y, int w, int h, IntConsumer press, IntConsumer drag, Runnable release) {
        Hit t = new Hit(); t.x = x; t.y = y; t.w = w; t.h = h; t.cy1 = clipY1; t.cy2 = clipY2;
        t.press = press; t.drag = drag; t.release = release;
        hits.add(t);
        return t;
    }

    private void text(DrawContext c, String s, int x, int y, int color) { c.drawText(textRenderer, s, x, y, col(color), false); }
    private void ctext(DrawContext c, String s, int cx, int y, int color) { text(c, s, cx - textRenderer.getWidth(s) / 2, y, color); }
    private void rr(DrawContext c, int x, int y, int w, int h, int r, int color) { Gfx.rrect(c, x, y, w, h, r, col(color)); }

    private List<String> wrap(String s, int w) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : s.split(" ")) {
            String test = line.isEmpty() ? word : line + " " + word;
            if (textRenderer.getWidth(test) > w && !line.isEmpty()) { out.add(line.toString()); line = new StringBuilder(word); }
            else { line = new StringBuilder(test); }
        }
        if (!line.isEmpty()) out.add(line.toString());
        return out;
    }

    // ---------- Linear-Layout in scrollbaren Spalten ----------
    private void begin(DrawContext c, String key, int x, int y, int w, int h) {
        lKey = key; lx = x; lw = w; lTop = y; lBot = y + h;
        lStart = y - (int) sc(key)[0]; ly = lStart;
        c.enableScissor(x, y, x + w, y + h);
        clipY1 = y; clipY2 = y + h;
    }

    private void end(DrawContext c) {
        c.disableScissor();
        float max = Math.max(0, (ly - lStart) - (lBot - lTop));
        float[] v = sc(lKey);
        if (v[0] > max) v[0] = max;
        areas.add(new Area(lx, lTop, lw, lBot - lTop, v, max));
        if (max > 0) {
            int bh = lBot - lTop;
            int th = Math.max(16, (int) (bh * bh / (bh + max)));
            int ty = lTop + (int) ((bh - th) * (v[0] / max));
            rr(c, lx + lw - 3, ty, 3, th, 1, 0xFF3A3A50);
        }
        clipY1 = 0; clipY2 = height;
    }

    private void hdr(DrawContext c, String t) {
        text(c, t, lx + 2, ly + 4, ac(0));
        c.fill(lx + 2, ly + 15, lx + lw - 4, ly + 16, col(0xFF262636));
        ly += 20;
    }

    private void info(DrawContext c, String t, int color) {
        for (String l : wrap(t, lw - 8)) { text(c, l, lx + 2, ly, color); ly += 11; }
        ly += 4;
    }

    private void toggle(DrawContext c, String id, String label, boolean val, Consumer<Boolean> set) {
        int y = ly, h = 22;
        text(c, label, lx + 2, y + 7, TEXT);
        int sw = 30, sx = lx + lw - sw - 8, sy = y + 4;
        float a = an(id, val);
        rr(c, sx, sy, sw, 14, 7, Gfx.mix(0xFF2A2A3A, ac(0), a));
        rr(c, sx + 2 + Math.round(a * (sw - 16)), sy + 2, 10, 10, 5, 0xFFFFFFFF);
        hit(lx, y, lw, h, () -> set.accept(!val));
        ly += h;
    }

    private void slider(DrawContext c, String id, String label, float v, float min, float max, String vt, Consumer<Float> set, Runnable release) {
        int y = ly;
        text(c, label, lx + 2, y + 2, TEXT);
        text(c, vt, lx + lw - textRenderer.getWidth(vt) - 8, y + 2, DIM);
        int bx = lx + 4, bw = lw - 16, by = y + 17;
        rr(c, bx, by, bw, 6, 3, 0xFF262634);
        float f = Math.max(0f, Math.min(1f, (v - min) / (max - min)));
        int fw = Math.round(bw * f);
        if (fw > 0) Gfx.hgrad(c, bx, by, fw, 6, col(ac(0)), col(ac(1)));
        rr(c, bx + fw - 4, by - 2, 8, 10, 4, 0xFFFFFFFF);
        IntConsumer upd = m -> set.accept(min + (max - min) * Math.max(0f, Math.min(1f, (m - bx) / (float) bw)));
        hitDrag(bx - 4, y + 10, bw + 8, 20, upd, upd, release);
        ly += 32;
    }

    private void swatches(DrawContext c, String id, String label, int cur, boolean activeColor, IntConsumer set, Runnable off) {
        text(c, label, lx + 2, ly + 2, TEXT);
        ly += 15;
        int size = 16, gap = 5, x = lx + 4, perRow = Math.max(1, (lw - 8) / (size + gap));
        int i = 0;
        int total = PALETTE.length + (off != null ? 1 : 0);
        for (int k = 0; k < total; k++) {
            int cx = x + (i % perRow) * (size + gap), cy = ly + (i / perRow) * (size + gap);
            boolean isOff = off != null && k == PALETTE.length;
            int pc = isOff ? 0 : PALETTE[k];
            boolean sel = isOff ? !activeColor : (activeColor && (cur & 0xFFFFFF) == pc);
            boolean hov = hover(cx, cy, size, size);
            float a = an(id + k, hov);
            if (sel) rr(c, cx - 2, cy - 2, size + 4, size + 4, 5, ac(1));
            rr(c, cx, cy, size, size, 4, isOff ? Gfx.mix(0xFF2A2A3A, 0xFF4A4A60, a) : (0xFF000000 | pc));
            if (isOff) ctext(c, "x", cx + size / 2, cy + 4, DIM);
            final int fk = k;
            hit(cx, cy, size, size, () -> { if (isOff) off.run(); else set.accept(PALETTE[fk]); });
            i++;
        }
        ly += ((i - 1) / perRow + 1) * (size + gap) + 4;
    }

    private void cycle(DrawContext c, String id, String label, String value, Runnable prev, Runnable next) {
        int y = ly;
        text(c, label, lx + 2, y + 6, TEXT);
        int bw = 20, rx = lx + lw - bw - 8;
        int vw = Math.min(lw - textRenderer.getWidth(label) - 70, 170);
        small(c, id + "n", rx, y + 1, bw, 18, ">", next);
        String v = value;
        while (textRenderer.getWidth(v) > vw - 8 && v.length() > 4) v = v.substring(0, v.length() - 2);
        if (!v.equals(value)) v += "…";
        ctext(c, v, rx - 6 - vw / 2, y + 6, DIM);
        small(c, id + "p", rx - vw - 12 - bw + bw, y + 1, bw, 18, "<", prev);
        ly += 22;
    }

    private void small(DrawContext c, String id, int x, int y, int w, int h, String label, Runnable r) {
        boolean hov = hover(x, y, w, h);
        float a = an(id, hov);
        rr(c, x, y, w, h, 5, Gfx.mix(CARD2, 0xFF2D2D40, a));
        ctext(c, label, x + w / 2, y + (h - 8) / 2, TEXT);
        hit(x, y, w, h, r);
    }

    private void btn(DrawContext c, String id, int x, int y, int w, int h, String label, boolean on, boolean enabled, Runnable r) {
        boolean hov = enabled && hover(x, y, w, h);
        float a = an(id, hov);
        if (on) {
            rr(c, x, y, w, h, 5, ac(0));
            rr(c, x + 1, y + 1, w - 2, h - 2, 4, Gfx.mix(0xFF1B2540, 0xFF252050, a));
        } else {
            rr(c, x, y, w, h, 5, Gfx.mix(CARD2, 0xFF2D2D40, a));
        }
        ctext(c, label, x + w / 2, y + (h - 8) / 2, enabled ? (on ? 0xFFFFFFFF : TEXT) : 0xFF55556A);
        if (enabled) hit(x, y, w, h, r);
    }

    private void btnRow(DrawContext c, String id, String label, Runnable r) {
        btn(c, id, lx + 2, ly, lw - 12, 20, label, false, true, r);
        ly += 25;
    }

    // ---------- Rendering ----------
    @Override
    protected void init() { Imgs.clearCaches(); }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        dt = Math.min(0.1f, (now - lastNano) / 1e9f);
        lastNano = now;
        mx = mouseX; my = mouseY;
        hits.clear(); areas.clear();
        clipY1 = 0; clipY2 = height;
        float open = cfg().animations ? Math.min(1f, (now - openedAt) / 2.2e8f) : 1f;
        open = 1f - (1f - open) * (1f - open) * (1f - open);
        fade = open;

        c.fill(0, 0, width, height, Gfx.alpha(0xFF000000, 0.55f * fade));

        float size = cfg().menuSize;
        int pw = Math.min(width - 16, (int) (760 * size)), ph = Math.min(height - 16, (int) (440 * size));
        int px = (width - pw) / 2, py = (height - ph) / 2 + (int) ((1 - open) * 14);
        int sw = 128;

        // Panel
        rr(c, px - 1, py - 1, pw + 2, ph + 2, 9, Gfx.mix(0xFF22223A, ac(1), 0.35f));
        Gfx.rrect(c, px, py, pw, ph, 8, Gfx.alpha(BG, cfg().transparency * fade));
        Gfx.hgrad(c, px + 10, py, pw - 20, 2, col(ac(0)), col(ac(1)));
        Gfx.rrect(c, px + 6, py + 6, sw - 6, ph - 12, 6, Gfx.alpha(CARD, cfg().transparency * fade));

        // Logo
        String logo = "OBV";
        int lxp = px + 16;
        for (int i = 0; i < logo.length(); i++) {
            Text t = Text.literal(String.valueOf(logo.charAt(i))).formatted(Formatting.BOLD);
            c.drawText(textRenderer, t, lxp, py + 14, col(Gfx.mix(ac(0), ac(1), i / 2f)), false);
            lxp += textRenderer.getWidth(t);
        }
        text(c, "VISUALS", lxp + 4, py + 14, DIM);

        // Suche
        int sx = px + 12, sy = py + 32, sbw = sw - 18;
        boolean se = editing == Edit.SEARCH;
        rr(c, sx, sy, sbw, 18, 5, se ? ac(1) : 0xFF262634);
        rr(c, sx + 1, sy + 1, sbw - 2, 16, 4, 0xFF0E0E15);
        String shown = se ? editBuf + ((System.currentTimeMillis() / 500) % 2 == 0 ? "|" : "") : (search.isEmpty() ? "Suchen..." : search);
        while (textRenderer.getWidth(shown) > sbw - 10 && shown.length() > 1) shown = shown.substring(1);
        text(c, shown, sx + 5, sy + 5, (se || !search.isEmpty()) ? TEXT : DIM);
        hit(sx, sy, sbw, 18, () -> startEdit(Edit.SEARCH, search));

        // Kategorien
        int th = Math.max(18, Math.min(26, (ph - 62 - 10) / Tab.values().length));
        int ty = py + 56;
        for (Tab t : Tab.values()) {
            boolean sel = t == tab && search.isBlank();
            boolean hov = hover(px + 10, ty, sw - 14, th);
            float a = an("tab" + t, hov || sel);
            rr(c, px + 10, ty, sw - 14, th - 2, 5, Gfx.mix(CARD, 0xFF26263A, a));
            if (sel) { rr(c, px + 10, ty + 3, 3, th - 8, 1, ac(0)); }
            drawIcon(c, ICONS[t.ordinal()], px + 18, ty + (th - 2 - 16) / 2, sel ? ac(0) : Gfx.mix(DIM, TEXT, a));
            text(c, t.label, px + 40, ty + (th - 2 - 8) / 2, sel ? 0xFFFFFFFF : Gfx.mix(DIM, TEXT, a));
            final Tab ft = t;
            hit(px + 10, ty, sw - 14, th, () -> { tab = ft; search = ""; editing = Edit.NONE; scrolls.clear(); selected = null; if (ft == Tab.MIXER) refreshMixer(); });
            ty += th;
        }

        // Header
        int cx = px + sw + 10, cw = pw - sw - 20;
        String title = search.isBlank() ? tab.label.toUpperCase(Locale.ROOT) : "SUCHE: " + search;
        c.drawText(textRenderer, Text.literal(title).formatted(Formatting.BOLD), cx, py + 14, col(TEXT), false);

        int ax = cx + cw;
        String applyLabel = dirty ? "Anwenden *" : "Anwenden";
        int aw = textRenderer.getWidth(applyLabel) + 22;
        ax -= aw;
        {
            boolean hov = hover(ax, py + 8, aw, 20);
            float a = an("apply", hov);
            Gfx.hgrad(c, ax, py + 8, aw, 20, col(Gfx.mix(ac(0), 0xFFFFFFFF, a * 0.2f)), col(Gfx.mix(ac(1), 0xFFFFFFFF, a * 0.2f)));
            ctext(c, applyLabel, ax + aw / 2, py + 14, 0xFFFFFFFF);
            hit(ax, py + 8, aw, 20, this::applyNow);
        }
        String pl = "Profil: " + prof().name;
        int pw2 = Math.min(textRenderer.getWidth(pl) + 20, cw - aw - 100);
        ax -= pw2 + 8;
        btn(c, "profbtn", ax, py + 8, pw2, 20, pl, false, true, () -> { ObvVisuals.cycleProfile(client); dirty = false; });

        // Inhalt
        int by = py + 38, bh = ph - 38 - 10;
        if (!search.isBlank()) pageSlots(c, cx, by, cw, bh, searchResults());
        else switch (tab) {
            case HUD -> pageHud(c, cx, by, cw, bh);
            case EXTRAS -> pageExtras(c, cx, by, cw, bh);
            case MIXER -> pageMixer(c, cx, by, cw, bh);
            case PROFILES -> pageProfiles(c, cx, by, cw, bh);
            case SETTINGS -> pageSettings(c, cx, by, cw, bh);
            default -> pageSlots(c, cx, by, cw, bh, Slots.of(tab.cat));
        }

        // Toast
        if (System.currentTimeMillis() < toastUntil) {
            int tw = textRenderer.getWidth(toast) + 20;
            rr(c, px + pw / 2 - tw / 2, py + ph - 30, tw, 20, 6, 0xFF1B1B2B);
            Gfx.hgrad(c, px + pw / 2 - tw / 2 + 6, py + ph - 11, tw - 12, 1, col(ac(0)), col(ac(1)));
            ctext(c, toast, px + pw / 2, py + ph - 24, TEXT);
        }
        if (capturing > 0) {
            c.fill(0, 0, width, height, Gfx.alpha(0xFF000000, 0.6f));
            Gfx.rrect(c, width / 2 - 130, height / 2 - 24, 260, 48, 8, 0xFF16161F);
            ctext(c, "Drücke die neue Taste...", width / 2, height / 2 - 10, TEXT);
            ctext(c, "ESC = Taste entfernen", width / 2, height / 2 + 4, DIM);
        }
    }

    private void drawIcon(DrawContext c, String[] icon, int x, int y, int color) {
        for (int r = 0; r < 8; r++) for (int k = 0; k < 8; k++)
            if (icon[r].charAt(k) == '#') c.fill(x + k * 2, y + r * 2, x + k * 2 + 2, y + r * 2 + 2, col(0xFF000000 | color));
    }

    // ---------- Seiten: Texturen ----------
    private List<Slot> searchResults() {
        String q = search.toLowerCase(Locale.ROOT);
        List<Slot> r = new ArrayList<>();
        for (Slot s : Slots.ALL)
            if (s.name().toLowerCase(Locale.ROOT).contains(q) || s.id().contains(q) || s.cat().name().toLowerCase(Locale.ROOT).contains(q)) r.add(s);
        return r;
    }

    private void pageSlots(DrawContext c, int x, int y, int w, int h, List<Slot> list) {
        int listW = Math.max(150, (int) (w * 0.40));
        if (selected == null || !list.contains(selected)) selected = list.isEmpty() ? null : list.get(0);
        begin(c, "list", x, y, listW, h);
        if (list.isEmpty()) info(c, "Keine Treffer.", DIM);
        for (Slot s : list) slotRow(c, s, listW);
        end(c);
        if (selected != null) detail(c, x + listW + 10, y, w - listW - 10, h, selected);
    }

    private void slotRow(DrawContext c, Slot s, int listW) {
        int y = ly, h = 28;
        ly += h;
        if (y + h < lTop || y > lBot) return;
        SlotState st = prof().state(s.id());
        boolean sel = s == selected;
        boolean hov = hover(lx, y, listW - 8, h - 2);
        float a = an("row" + s.id(), hov || sel);
        rr(c, lx, y, listW - 8, h - 2, 6, sel ? Gfx.mix(CARD, Gfx.alpha(ac(0), 0.35f) | 0xFF000000, 0.35f + 0.1f * a) : Gfx.mix(CARD, CARD2, a));
        Gfx.drawGrid(c, Imgs.preview(s, st, false, 8), lx + 4, y + 3, 22, 22, fade);
        String n = s.name();
        while (textRenderer.getWidth(n) > listW - 8 - 36 && n.length() > 3) n = n.substring(0, n.length() - 2);
        text(c, n, lx + 32, y + 5, TEXT);
        boolean none = Imgs.available(s).isEmpty();
        text(c, none ? "nicht gefunden" : st.isActive() ? "● aktiv" : "Vanilla", lx + 32, y + 16, none ? 0xFF7A4A4A : st.isActive() ? ac(0) : DIM);
        hit(lx, y, listW - 8, h - 2, () -> selected = s);
    }

    private void detail(DrawContext c, int x, int y, int w, int h, Slot s) {
        SlotState st = prof().state(s.id());
        begin(c, "detail", x, y, w, h);
        int avail = Imgs.available(s).size();
        hdr(c, s.name());
        info(c, s.cat().name() + " · " + avail + " von " + s.targets().length + " Zieldateien in dieser Version vorhanden", DIM);
        if (avail == 0) { info(c, "Diese Textur existiert in Minecraft 1.21.11 unter keinem bekannten Pfad und wird daher nicht überschrieben.", 0xFFB07070); end(c); return; }

        int pb = 92;
        Gfx.rrect(c, lx + 2, ly, pb + 4, pb + 4, 6, col(0xFF262634));
        Gfx.drawGrid(c, Imgs.preview(s, st, false, 32), lx + 4, ly + 2, pb, pb, fade);
        Gfx.rrect(c, lx + pb + 14, ly, pb + 4, pb + 4, 6, col(0xFF262634));
        Gfx.drawGrid(c, Imgs.preview(s, st, true, 32), lx + pb + 16, ly + 2, pb, pb, fade);
        text(c, "Aktuell", lx + 6, ly + pb + 8, DIM);
        text(c, "Vanilla", lx + pb + 18, ly + pb + 8, DIM);
        ly += pb + 24;

        toggle(c, "en" + s.id(), "Aktiviert", st.enabled, v -> { st.enabled = v; markDirty(); });

        text(c, "Quelle", lx + 2, ly + 2, DIM);
        ly += 14;
        int bw = (lw - 18) / 4;
        Model.Source[] srcs = Model.Source.values();
        String[] names = {"Vanilla", "OBV", "Custom", "Pack"};
        for (int i = 0; i < 4; i++) {
            Model.Source sr = srcs[i];
            boolean ok = sr != Model.Source.OBV || Imgs.hasBundled(s.id());
            btn(c, "src" + s.id() + i, lx + 2 + i * (bw + 3), ly, bw, 20, names[i], st.source == sr, ok, () -> setSource(s, st, sr));
        }
        ly += 28;

        if (st.source == Model.Source.CUSTOM) {
            List<String> files = Imgs.customFiles(s.cat());
            int idx = files.indexOf(st.custom);
            String v = files.isEmpty() ? "– keine Datei –" : (idx < 0 ? "– wählen –" : st.custom);
            cycle(c, "cf" + s.id(), "Datei", v,
                    () -> { if (!files.isEmpty()) { st.custom = files.get((idx - 1 + files.size()) % files.size()); markDirty(); } },
                    () -> { if (!files.isEmpty()) { st.custom = files.get((idx + 1) % files.size()); markDirty(); } });
            btnRow(c, "of" + s.id(), "Ordner öffnen", () -> openFolder(Store.customDir(s.cat())));
            info(c, "Tipp: PNG-Datei aus dem Explorer einfach auf dieses Fenster ziehen, sie wird importiert und diesem Slot zugewiesen.", DIM);
        } else if (st.source == Model.Source.PACK) {
            List<String> packs = Packs.list();
            int idx = packs.indexOf(st.pack);
            cycle(c, "pk" + s.id(), "Pack", packs.isEmpty() ? "– keine Packs –" : (idx < 0 ? "– wählen –" : st.pack),
                    () -> { if (!packs.isEmpty()) { st.pack = packs.get((idx - 1 + packs.size()) % packs.size()); markDirty(); } },
                    () -> { if (!packs.isEmpty()) { st.pack = packs.get((idx + 1) % packs.size()); markDirty(); } });
            if (!st.pack.isBlank() && !Packs.provides(st.pack, s)) info(c, "Dieses Pack enthält keine Textur für diesen Slot.", 0xFFB07070);
        }

        swatches(c, "tint" + s.id(), "Farbe (Einfärben)", st.tint, st.tintStrength > 0.001f,
                col -> { st.tint = col; if (st.tintStrength < 0.05f) st.tintStrength = 0.65f; st.enabled = true; markDirty(); },
                () -> { st.tintStrength = 0f; markDirty(); });
        slider(c, "ts" + s.id(), "Farbstärke", st.tintStrength, 0f, 1f, Math.round(st.tintStrength * 100) + "%",
                v -> { st.tintStrength = v; if (v > 0.001f) st.enabled = true; Imgs.clearCaches(); }, this::markDirty);

        btnRow(c, "rs" + s.id(), "Auf Standard zurücksetzen", () -> { prof().slots.remove(s.id()); markDirty(); Imgs.clearCaches(); });
        end(c);
    }

    private void setSource(Slot s, SlotState st, Model.Source sr) {
        st.source = sr;
        if (sr != Model.Source.VANILLA) st.enabled = true;
        if (sr == Model.Source.CUSTOM && st.custom.isBlank()) { List<String> f = Imgs.customFiles(s.cat()); if (!f.isEmpty()) st.custom = f.get(0); }
        if (sr == Model.Source.PACK && st.pack.isBlank()) { List<String> p = Packs.list(); if (!p.isEmpty()) st.pack = p.get(0); }
        markDirty();
    }

    // ---------- Seite: HUD ----------
    private void hudElem(DrawContext c, String id, String name, HudElem e) {
        hdr(c, name);
        toggle(c, id + "en", "Anzeigen", e.enabled, v -> { e.enabled = v; saveProf(); });
        slider(c, id + "x", "Position X", e.x, 0f, 1f, Math.round(e.x * 100) + "%", v -> e.x = v, this::saveProf);
        slider(c, id + "y", "Position Y", e.y, 0f, 1f, Math.round(e.y * 100) + "%", v -> e.y = v, this::saveProf);
        slider(c, id + "s", "Größe", e.scale, 0.5f, 2.5f, String.format("%.1fx", e.scale), v -> e.scale = Math.round(v * 10) / 10f, this::saveProf);
        swatches(c, id + "c", "Farbe", e.color, true, col -> { e.color = col; saveProf(); }, null);
        ly += 6;
    }

    private void pageHud(DrawContext c, int x, int y, int w, int h) {
        begin(c, "hud", x, y, w, h);
        Model.Hud hud = prof().hud;
        info(c, "HUD-Änderungen sind sofort im Spiel sichtbar (Menü schließen, um sie zu sehen).", DIM);
        hdr(c, "Allgemein");
        slider(c, "hbg", "Hintergrund-Deckkraft", hud.bgAlpha, 0f, 1f, Math.round(hud.bgAlpha * 100) + "%", v -> hud.bgAlpha = v, this::saveProf);
        toggle(c, "hsh", "Textschatten", hud.shadow, v -> { hud.shadow = v; saveProf(); });
        ly += 6;
        hudElem(c, "hf", "FPS-Anzeige", hud.fps);
        hudElem(c, "hc", "CPS-Anzeige (links | rechts)", hud.cps);
        hudElem(c, "hk", "Koordinaten", hud.coords);
        hudElem(c, "ha", "Rüstungsanzeige", hud.armor);
        end(c);
    }

    // ---------- Seite: Extras ----------
    private void pageExtras(DrawContext c, int x, int y, int w, int h) {
        begin(c, "extras", x, y, w, h);
        hdr(c, "Zoom");
        info(c, "Halte die Zoom-Taste gedrückt, um das Sichtfeld zu verkleinern.", DIM);
        Profile p = prof();
        slider(c, "zfov", "Zoom-Sichtfeld (FOV)", p.zoomFov, 30f, 90f, p.zoomFov + "°", v -> p.zoomFov = Math.round(v), this::saveProf);
        btnRow(c, "zkey", "Zoom-Taste: " + keyName(ObvVisuals.ZOOM), () -> capturing = 2);
        ly += 8;
        hdr(c, "Schneller Profilwechsel");
        btnRow(c, "pkey", "Nächstes Profil: " + keyName(ObvVisuals.NEXT_PROFILE), () -> capturing = 3);
        ly += 8;
        hdr(c, "Noch nicht enthalten");
        info(c, "Crystal-Scale, Crystal-/Anchor-Animationen, Glow-Effekte, Armor-Trims, Sky-Themes und Cosmetics greifen in die Render-Pipeline ein und benötigen Mixins. Sie sind für die nächste Ausbaustufe geplant und werden hier bewusst nicht als Schalter vorgetäuscht.", DIM);
        end(c);
    }

    // ---------- Seite: Pack Mixer ----------
    private void refreshMixer() {
        if (mixPack == null) { List<String> l = Packs.list(); if (!l.isEmpty()) mixPack = l.get(0); }
        computeProvided();
    }

    private void computeProvided() {
        if (mixPack == null) { mixProvided = List.of(); return; }
        List<Slot> r = new ArrayList<>();
        for (Slot s : Slots.ALL) if (Packs.provides(mixPack, s)) r.add(s);
        mixProvided = r;
    }

    private void pageMixer(DrawContext c, int x, int y, int w, int h) {
        int listW = Math.max(150, (int) (w * 0.36));
        List<String> packs = Packs.list();
        begin(c, "packs", x, y, listW, h);
        hdr(c, "Installierte Packs");
        if (packs.isEmpty()) info(c, "Keine Resourcepacks gefunden. Lege Packs in den resourcepacks-Ordner oder ziehe eine .zip auf dieses Fenster.", DIM);
        for (String p : packs) {
            int ry = ly; boolean sel = p.equals(mixPack);
            boolean hov = hover(lx, ry, listW - 8, 22);
            float a = an("mp" + p, hov || sel);
            rr(c, lx, ry, listW - 8, 22, 5, sel ? Gfx.mix(CARD, 0xFF2A2A55, 0.8f) : Gfx.mix(CARD, CARD2, a));
            String n = p;
            while (textRenderer.getWidth(n) > listW - 28 && n.length() > 3) n = n.substring(0, n.length() - 2);
            text(c, n, lx + 8, ry + 7, sel ? 0xFFFFFFFF : TEXT);
            hit(lx, ry, listW - 8, 22, () -> { mixPack = p; computeProvided(); });
            ly += 25;
        }
        end(c);

        begin(c, "mix", x + listW + 10, y, w - listW - 10, h);
        if (mixPack == null) { info(c, "Wähle links ein Pack. Dann kannst du einzelne Texturen daraus übernehmen und mit anderen Packs mischen.", DIM); end(c); return; }
        hdr(c, "Aus \"" + mixPack + "\" übernehmen");
        info(c, mixProvided.size() + " passende Texturen gefunden. Jede Auswahl wird im aktiven Profil gespeichert.", DIM);
        btnRow(c, "mall", "Alle übernehmen", () -> { for (Slot s : mixProvided) { SlotState st = prof().state(s.id()); st.source = Model.Source.PACK; st.pack = mixPack; st.enabled = true; } markDirty(); });
        btnRow(c, "mnone", "Alle von diesem Pack entfernen", () -> { for (SlotState st : prof().slots.values()) if (st.source == Model.Source.PACK && mixPack.equals(st.pack)) { st.source = Model.Source.VANILLA; } markDirty(); });
        btnRow(c, "mexp", "Mix als Resourcepack exportieren (.zip)", () -> {
            try { Path z = PackBuilder.exportZip(prof()); notify("Exportiert: " + z.getFileName()); openFolder(Store.EXPORTS); }
            catch (IOException e) { notify("Export fehlgeschlagen: " + e.getMessage()); }
        });
        ly += 4;
        for (Slot s : mixProvided) {
            SlotState st = prof().state(s.id());
            boolean on = st.enabled && st.source == Model.Source.PACK && mixPack.equals(st.pack);
            int ry = ly;
            if (ry + 26 >= lTop && ry <= lBot) {
                boolean hov = hover(lx, ry, lw - 8, 24);
                float a = an("mx" + s.id(), hov);
                rr(c, lx, ry, lw - 8, 24, 5, Gfx.mix(CARD, CARD2, a));
                Gfx.drawGrid(c, Imgs.previewPack(s, mixPack, 8), lx + 3, ry + 2, 20, 20, fade);
                String n = s.name();
                while (textRenderer.getWidth(n) > lw - 80 && n.length() > 3) n = n.substring(0, n.length() - 2);
                text(c, n, lx + 29, ry + 8, TEXT);
                rr(c, lx + lw - 34, ry + 6, 14, 14, 4, on ? ac(0) : 0xFF2A2A3A);
                if (on) ctext(c, "✓", lx + lw - 27, ry + 9, 0xFFFFFFFF);
                hit(lx, ry, lw - 8, 24, () -> {
                    if (on) { st.source = Model.Source.VANILLA; }
                    else { st.source = Model.Source.PACK; st.pack = mixPack; st.enabled = true; }
                    markDirty();
                });
            }
            ly += 27;
        }
        end(c);
    }

    // ---------- Seite: Profile ----------
    private void pageProfiles(DrawContext c, int x, int y, int w, int h) {
        int listW = Math.max(150, (int) (w * 0.42));
        if (selProfile == null || !Store.profiles.containsKey(selProfile)) selProfile = Store.settings.activeProfile;
        begin(c, "plist", x, y, listW, h);
        hdr(c, "Deine Profile");
        for (Profile p : new ArrayList<>(Store.profiles.values())) {
            int ry = ly; boolean sel = p.name.equals(selProfile), act = p.name.equals(Store.settings.activeProfile);
            boolean hov = hover(lx, ry, listW - 8, 26);
            float a = an("pf" + p.name, hov || sel);
            rr(c, lx, ry, listW - 8, 26, 6, sel ? Gfx.mix(CARD, 0xFF2A2A55, 0.8f) : Gfx.mix(CARD, CARD2, a));
            String n = p.name;
            while (textRenderer.getWidth(n) > listW - 80 && n.length() > 3) n = n.substring(0, n.length() - 2);
            text(c, n, lx + 8, ry + 9, TEXT);
            if (act) { rr(c, lx + listW - 62, ry + 6, 46, 14, 7, ac(0)); ctext(c, "AKTIV", lx + listW - 39, ry + 9, 0xFFFFFFFF); }
            hit(lx, ry, listW - 8, 26, () -> { selProfile = p.name; confirmDelete = ""; });
            ly += 29;
        }
        end(c);

        begin(c, "pact", x + listW + 10, y, w - listW - 10, h);
        Profile sp = Store.profiles.get(selProfile);
        if (editing == Edit.NEW_PROFILE || editing == Edit.RENAME_PROFILE) {
            hdr(c, editing == Edit.NEW_PROFILE ? "Neues Profil" : "Profil umbenennen");
            rr(c, lx + 2, ly, lw - 12, 22, 5, ac(1));
            rr(c, lx + 3, ly + 1, lw - 14, 20, 4, 0xFF0E0E15);
            text(c, editBuf + ((System.currentTimeMillis() / 500) % 2 == 0 ? "|" : ""), lx + 8, ly + 7, TEXT);
            ly += 28;
            info(c, "Enter = bestätigen, ESC = abbrechen", DIM);
        }
        hdr(c, "Aktionen für \"" + selProfile + "\"");
        btnRow(c, "pa", "Aktivieren & anwenden", () -> { Store.settings.activeProfile = sp.name; Store.saveSettings(); dirty = false; notify(PackBuilder.apply(sp)); });
        btnRow(c, "pn", "Neues Profil", () -> startEdit(Edit.NEW_PROFILE, ""));
        btnRow(c, "pr", "Umbenennen", () -> startEdit(Edit.RENAME_PROFILE, sp.name));
        btnRow(c, "pd", "Duplizieren", () -> { Profile d = Store.duplicate(sp); selProfile = d.name; notify("Dupliziert: " + d.name); });
        btnRow(c, "pe", "Exportieren (.json)", () -> {
            try { Path f = Store.export(sp); notify("Exportiert: " + f.getFileName()); } catch (IOException e) { notify("Fehler: " + e.getMessage()); }
        });
        boolean conf = sp.name.equals(confirmDelete);
        btnRow(c, "pdel", conf ? "Wirklich löschen? Nochmal klicken" : "Löschen", () -> {
            if (!conf) { confirmDelete = sp.name; return; }
            if (Store.delete(sp)) { selProfile = Store.settings.activeProfile; confirmDelete = ""; notify("Gelöscht"); }
            else notify("Das letzte Profil kann nicht gelöscht werden");
        });
        ly += 6;
        hdr(c, "Import");
        List<Path> files = Store.exportFiles();
        if (files.isEmpty()) info(c, "Keine Dateien im Export-Ordner. Ziehe eine Profil-.json auf dieses Fenster oder lege sie in den Ordner.", DIM);
        for (Path f : files) btnRow(c, "pi" + f.getFileName(), "Importieren: " + f.getFileName(), () -> {
            try { Profile ip = Store.importFile(f); selProfile = ip.name; notify("Importiert: " + ip.name); } catch (Exception e) { notify("Import fehlgeschlagen"); }
        });
        btnRow(c, "pof", "Export-Ordner öffnen", () -> openFolder(Store.EXPORTS));
        end(c);
    }

    // ---------- Seite: Einstellungen ----------
    private String keyName(KeyBinding kb) { return kb.getBoundKeyLocalizedText().getString(); }

    private void pageSettings(DrawContext c, int x, int y, int w, int h) {
        begin(c, "settings", x, y, w, h);
        Settings s = cfg();
        hdr(c, "Menü");
        btnRow(c, "skey", "Menü-Taste: " + keyName(ObvVisuals.MENU), () -> capturing = 1);
        slider(c, "str", "Transparenz", s.transparency, 0.3f, 1f, Math.round(s.transparency * 100) + "%", v -> s.transparency = v, Store::saveSettings);
        slider(c, "ssz", "Menügröße", s.menuSize, 0.7f, 1.3f, Math.round(s.menuSize * 100) + "%", v -> s.menuSize = Math.round(v * 20) / 20f, Store::saveSettings);
        cycle(c, "sth", "Farbschema", THEME_NAMES[s.theme % THEMES.length],
                () -> { s.theme = (s.theme + THEMES.length - 1) % THEMES.length; Store.saveSettings(); },
                () -> { s.theme = (s.theme + 1) % THEMES.length; Store.saveSettings(); });
        toggle(c, "san", "Animationen", s.animations, v -> { s.animations = v; Store.saveSettings(); });
        toggle(c, "saa", "Beim Schließen automatisch anwenden", s.autoApplyOnClose, v -> { s.autoApplyOnClose = v; Store.saveSettings(); });
        ly += 8;
        hdr(c, "Ordner");
        btnRow(c, "sf1", "Custom-Texturen-Ordner öffnen", () -> openFolder(Store.CUSTOM));
        btnRow(c, "sf2", "Konfigurationsordner öffnen", () -> openFolder(Store.DIR));
        btnRow(c, "sf3", "Resourcepack-Ordner öffnen", () -> openFolder(Packs.dir()));
        ly += 8;
        hdr(c, "Hinweis");
        info(c, "Texturänderungen erzeugen ein echtes Resourcepack (resourcepacks/OBV_VISUALS) und laden die Ressourcen neu. Das dauert je nach Pack ein paar Sekunden.", DIM);
        end(c);
    }

    // ---------- Aktionen ----------
    private void applyNow() {
        Store.saveProfile(prof());
        notify(PackBuilder.apply(prof()));
        dirty = false;
    }

    private void openFolder(Path p) {
        try { Files.createDirectories(p); Util.getOperatingSystem().open(p.toUri()); }
        catch (Exception e) { notify("Ordner: " + p); }
    }

    private void startEdit(Edit e, String initial) { editing = e; editBuf = initial; }

    private void commitEdit() {
        Edit e = editing;
        editing = Edit.NONE;
        switch (e) {
            case SEARCH -> { search = editBuf.trim(); scrolls.remove("list"); selected = null; }
            case NEW_PROFILE -> { if (!editBuf.isBlank()) { Profile p = Store.create(editBuf); selProfile = p.name; notify("Erstellt: " + p.name); } }
            case RENAME_PROFILE -> {
                Profile p = Store.profiles.get(selProfile);
                if (p != null && !editBuf.isBlank()) { if (Store.rename(p, editBuf)) selProfile = p.name; else notify("Name nicht möglich"); }
            }
            default -> {}
        }
    }

    // ---------- Eingaben (Minecraft 1.21.9+ Signaturen) ----------
    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (capturing > 0) return true;
        if (click.button() != 0) return false;
        int mxp = (int) click.x(), myp = (int) click.y();
        if (editing == Edit.SEARCH) commitEdit();
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (mxp >= h.x && mxp < h.x + h.w && myp >= h.y && myp < h.y + h.h && myp >= h.cy1 && myp < h.cy2) {
                active = h;
                if (h.press != null) h.press.accept(mxp);
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        if (active != null && active.drag != null) { active.drag.accept((int) click.x()); return true; }
        return false;
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (active != null) { Runnable r = active.release; active = null; if (r != null) r.run(); return true; }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        for (int i = areas.size() - 1; i >= 0; i--) {
            Area a = areas.get(i);
            if (mouseX >= a.x() && mouseX < a.x() + a.w() && mouseY >= a.y() && mouseY < a.y() + a.h()) {
                a.v()[0] = Math.max(0, Math.min(a.max(), a.v()[0] - (float) vertical * 24f));
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if (capturing > 0) {
            KeyBinding kb = capturing == 1 ? ObvVisuals.MENU : capturing == 2 ? ObvVisuals.ZOOM : ObvVisuals.NEXT_PROFILE;
            kb.setBoundKey(key == GLFW.GLFW_KEY_ESCAPE ? InputUtil.UNKNOWN_KEY : InputUtil.Type.KEYSYM.createFromCode(key));
            KeyBinding.updateKeysByCode();
            client.options.write();
            capturing = 0;
            return true;
        }
        if (editing != Edit.NONE) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) commitEdit();
            else if (key == GLFW.GLFW_KEY_ESCAPE) { if (editing == Edit.SEARCH) search = ""; editing = Edit.NONE; }
            else if (key == GLFW.GLFW_KEY_BACKSPACE && !editBuf.isEmpty()) editBuf = editBuf.substring(0, editBuf.length() - 1);
            if (editing == Edit.SEARCH) { search = editBuf.trim(); selected = null; }
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE || key == KeyBindingHelper.getBoundKeyOf(ObvVisuals.MENU).getCode()) { close(); return true; }
        return true;
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (editing == Edit.NONE) return false;
        String s = Character.toString(input.codepoint());
        if (editBuf.length() < 32 && !Character.isISOControl(input.codepoint())) editBuf += s;
        if (editing == Edit.SEARCH) { search = editBuf.trim(); selected = null; scrolls.remove("list"); }
        return true;
    }

    @Override
    public void onFilesDropped(List<Path> paths) {
        int n = 0;
        for (Path p : paths) {
            String name = p.getFileName().toString();
            String low = name.toLowerCase(Locale.ROOT);
            try {
                if (low.endsWith(".png") && selected != null) {
                    Path dest = Store.customDir(selected.cat()).resolve(name);
                    Files.copy(p, dest, StandardCopyOption.REPLACE_EXISTING);
                    Path meta = p.resolveSibling(name + ".mcmeta");
                    if (Files.exists(meta)) Files.copy(meta, Store.customDir(selected.cat()).resolve(name + ".mcmeta"), StandardCopyOption.REPLACE_EXISTING);
                    SlotState st = prof().state(selected.id());
                    st.source = Model.Source.CUSTOM; st.custom = name; st.enabled = true;
                    Imgs.clearCaches(); markDirty(); n++;
                    notify("Importiert für " + selected.name() + ": " + name);
                } else if (low.endsWith(".json")) {
                    Profile ip = Store.importFile(p); selProfile = ip.name; n++; notify("Profil importiert: " + ip.name);
                } else if (low.endsWith(".zip")) {
                    Files.copy(p, Packs.dir().resolve(name), StandardCopyOption.REPLACE_EXISTING); n++;
                    notify("Pack hinzugefügt: " + name);
                    if (tab == Tab.MIXER) refreshMixer();
                }
            } catch (Exception e) { notify("Import fehlgeschlagen: " + name); }
        }
        if (n == 0) notify("PNG nur mit ausgewähltem Slot, sonst .json/.zip");
    }

    @Override
    public void removed() {
        if (dirty && cfg().autoApplyOnClose) applyNow();
        Store.saveSettings();
        Store.saveProfile(prof());
        Imgs.clearCaches();
    }
}
