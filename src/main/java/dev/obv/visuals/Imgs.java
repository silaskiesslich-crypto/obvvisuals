package dev.obv.visuals;

import dev.obv.visuals.Model.*;
import dev.obv.visuals.Slots.Slot;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/** Bild-Hilfen: Laden aus Vanilla / Mod / Custom / Packs, Einfärben, Vorschau-Raster. */
public final class Imgs {
    private Imgs() {}

    public static final class Grid { public int w, h; public int[] px; }

    private static final Map<String, Boolean> AVAIL = new HashMap<>();
    private static final Map<String, Grid> GRIDS = new LinkedHashMap<>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Grid> e) { return size() > 160; }
    };
    private static final Map<String, List<String>> FILE_CACHE = new HashMap<>();
    private static long fileCacheTime = 0;

    public static void clearCaches() { AVAIL.clear(); GRIDS.clear(); FILE_CACHE.clear(); }

    // ---------- Vanilla ----------
    public static boolean vanillaExists(String path) {
        return AVAIL.computeIfAbsent(path, p -> {
            try { return MinecraftClient.getInstance().getResourceManager().getResource(Identifier.ofVanilla(p)).isPresent(); }
            catch (Exception e) { return false; }
        });
    }

    /** Liest die ORIGINALE Vanilla-Datei (unterstes Pack), nicht unser eigenes Pack. */
    public static byte[] vanilla(String path) {
        try {
            List<Resource> list = MinecraftClient.getInstance().getResourceManager().getAllResources(Identifier.ofVanilla(path));
            if (list.isEmpty()) return null;
            try (InputStream in = list.get(0).getInputStream()) { return in.readAllBytes(); }
        } catch (Exception e) { return null; }
    }

    public static List<String> available(Slot s) {
        List<String> r = new ArrayList<>();
        for (String t : s.targets()) if (vanillaExists(t)) r.add(t);
        return r;
    }

    // ---------- Bundled ----------
    public static boolean hasBundled(String slotId) {
        try (InputStream in = Imgs.class.getResourceAsStream("/assets/obvvisuals/obv/" + slotId + ".png")) { return in != null; }
        catch (IOException e) { return false; }
    }

    public static BufferedImage bundled(String slotId) {
        try (InputStream in = Imgs.class.getResourceAsStream("/assets/obvvisuals/obv/" + slotId + ".png")) {
            return in == null ? null : ImageIO.read(in);
        } catch (IOException e) { return null; }
    }

    // ---------- Custom ----------
    public static List<String> customFiles(Slots.Cat cat) {
        long now = System.currentTimeMillis();
        if (now - fileCacheTime > 1500) { FILE_CACHE.clear(); fileCacheTime = now; }
        return FILE_CACHE.computeIfAbsent(cat.name(), k -> {
            try (Stream<Path> st = Files.list(Store.customDir(cat))) {
                return st.map(p -> p.getFileName().toString()).filter(n -> n.toLowerCase(Locale.ROOT).endsWith(".png")).sorted().toList();
            } catch (IOException e) { return List.of(); }
        });
    }

    public static BufferedImage read(byte[] data) {
        try { return data == null ? null : ImageIO.read(new ByteArrayInputStream(data)); } catch (IOException e) { return null; }
    }

    public static byte[] png(BufferedImage img) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        ImageIO.write(img, "png", o);
        return o.toByteArray();
    }

    // ---------- Einfärben ----------
    public static BufferedImage tint(BufferedImage src, int rgb, float strength) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        float tr = (rgb >> 16) & 255, tg = (rgb >> 8) & 255, tb = rgb & 255;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int p = src.getRGB(x, y);
            int a = p >>> 24;
            float r = (p >> 16) & 255, g = (p >> 8) & 255, b = p & 255;
            float lum = (0.299f * r + 0.587f * g + 0.114f * b) / 255f;
            float k = 0.30f + lum * 1.15f;
            float nr = Math.min(255f, tr * k), ng = Math.min(255f, tg * k), nb = Math.min(255f, tb * k);
            int or = Math.round(r + (nr - r) * strength), og = Math.round(g + (ng - g) * strength), ob = Math.round(b + (nb - b) * strength);
            out.setRGB(x, y, (a << 24) | (or << 16) | (og << 8) | ob);
        }
        return out;
    }

    // ---------- Auflösen einer Textur anhand des Slot-Zustands ----------
    public record Out(BufferedImage img, byte[] raw, byte[] meta) {}

    public static Out resolve(Slot s, SlotState st, String target) {
        BufferedImage img = null; byte[] raw = null, meta = null;
        try {
            switch (st.source) {
                case OBV -> img = bundled(s.id());
                case CUSTOM -> {
                    if (!st.custom.isBlank()) {
                        Path p = Store.customDir(s.cat()).resolve(st.custom);
                        if (Files.exists(p)) {
                            raw = Files.readAllBytes(p);
                            Path m = p.resolveSibling(st.custom + ".mcmeta");
                            if (Files.exists(m)) meta = Files.readAllBytes(m);
                        }
                    }
                }
                case PACK -> {
                    if (!st.pack.isBlank()) { raw = Packs.read(st.pack, target); meta = Packs.read(st.pack, target + ".mcmeta"); }
                }
                case VANILLA -> { raw = vanilla(target); meta = vanilla(target + ".mcmeta"); }
            }
            if (img == null && raw != null) img = read(raw);
            if (img == null) return null;
            if (st.tintStrength > 0.001f) { img = tint(img, st.tint, st.tintStrength); raw = null; }
            if (raw == null || st.source == Source.OBV) raw = png(img);
            if (meta == null && (st.source == Source.OBV || st.source == Source.CUSTOM) && img.getHeight() > img.getWidth())
                meta = vanilla(target + ".mcmeta"); // animierte Streifen behalten ihre Animation
            return new Out(img, raw, meta);
        } catch (Exception e) {
            ObvVisuals.LOG.warn("resolve {} fehlgeschlagen: {}", target, e.toString());
            return null;
        }
    }

    // ---------- Vorschau ----------
    public static Grid grid(BufferedImage img, int maxCells) {
        int iw = img.getWidth(), ih = img.getHeight();
        float sc = Math.max(1f, Math.max(iw, ih) / (float) maxCells);
        int gw = Math.max(1, (int) Math.ceil(iw / sc)), gh = Math.max(1, (int) Math.ceil(ih / sc));
        Grid g = new Grid(); g.w = gw; g.h = gh; g.px = new int[gw * gh];
        for (int gy = 0; gy < gh; gy++) for (int gx = 0; gx < gw; gx++) {
            int x0 = (int) (gx * sc), x1 = Math.min(iw, Math.max(x0 + 1, (int) ((gx + 1) * sc)));
            int y0 = (int) (gy * sc), y1 = Math.min(ih, Math.max(y0 + 1, (int) ((gy + 1) * sc)));
            long A = 0, R = 0, G = 0, Bc = 0; int n = 0;
            for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) {
                int p = img.getRGB(x, y); int a = p >>> 24;
                A += a; R += ((p >> 16) & 255) * a; G += ((p >> 8) & 255) * a; Bc += (p & 255) * a; n++;
            }
            if (n == 0 || A == 0) { g.px[gy * gw + gx] = 0; continue; }
            int a = (int) (A / n);
            g.px[gy * gw + gx] = (a << 24) | ((int) (R / A) << 16) | ((int) (G / A) << 8) | (int) (Bc / A);
        }
        return g;
    }

    /** Vorschau eines Slots. vanillaOnly=true zeigt das Original. */
    public static Grid preview(Slot s, SlotState st, boolean vanillaOnly, int cells) {
        List<String> av = available(s);
        if (av.isEmpty()) return null;
        String t = av.get(0);
        String key = s.id() + "|" + (vanillaOnly ? "V" : st.source + "|" + st.custom + "|" + st.pack + "|" + st.tint + "|" + st.tintStrength) + "|" + cells;
        Grid cached = GRIDS.get(key);
        if (cached != null) return cached;
        SlotState use = st;
        if (vanillaOnly) { use = new SlotState(); }
        Out o = resolve(s, use, t);
        if (o == null && !vanillaOnly) { o = resolve(s, new SlotState(), t); }
        if (o == null) return null;
        Grid g = grid(o.img(), cells);
        GRIDS.put(key, g);
        return g;
    }

    public static Grid previewPack(Slot s, String pack, int cells) {
        List<String> av = available(s);
        if (av.isEmpty()) return null;
        String key = "PK|" + s.id() + "|" + pack + "|" + cells;
        Grid cached = GRIDS.get(key);
        if (cached != null) return cached;
        SlotState st = new SlotState(); st.source = Source.PACK; st.pack = pack;
        for (String t : av) {
            Out o = resolve(s, st, t);
            if (o != null) { Grid g = grid(o.img(), cells); GRIDS.put(key, g); return g; }
        }
        return null;
    }
}
