package dev.obv.visuals;

import dev.obv.visuals.Model.*;
import dev.obv.visuals.Slots.Slot;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourcePackManager;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Erzeugt aus dem aktiven Profil ein echtes Resourcepack (resourcepacks/OBV_VISUALS),
 * aktiviert es und lädt die Ressourcen neu - ohne Minecraft-Neustart.
 */
public final class PackBuilder {
    private PackBuilder() {}

    public static final String PACK_DIR = "OBV_VISUALS";
    public static final String PACK_ID = "file/" + PACK_DIR;

    private static Path root() { return MinecraftClient.getInstance().getResourcePackDir().resolve(PACK_DIR); }

    private static void deleteTree(Path p) throws IOException {
        if (!Files.exists(p)) return;
        try (Stream<Path> w = Files.walk(p)) {
            for (Path f : w.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(f);
        }
    }

    /** Schreibt das Pack. Gibt die Anzahl geschriebener Texturdateien zurück. */
    public static int build(Profile prof) throws IOException {
        Path r = root();
        deleteTree(r);
        Files.createDirectories(r);
        Files.writeString(r.resolve("pack.mcmeta"),
                "{\"pack\":{\"pack_format\":75,\"description\":\"OBV VISUALS - " + prof.name.replace("\"", "'") + "\"}}", StandardCharsets.UTF_8);
        try (var in = PackBuilder.class.getResourceAsStream("/assets/obvvisuals/pack.png")) {
            if (in != null) Files.copy(in, r.resolve("pack.png"));
        }
        int n = 0;
        for (Slot s : Slots.ALL) {
            SlotState st = prof.slots.get(s.id());
            if (st == null || !st.isActive()) continue;
            for (String t : s.targets()) {
                if (!Imgs.vanillaExists(t)) continue;
                Imgs.Out o = Imgs.resolve(s, st, t);
                if (o == null) continue;
                Path f = r.resolve("assets/minecraft/" + t);
                Files.createDirectories(f.getParent());
                Files.write(f, o.raw());
                if (o.meta() != null) Files.write(f.resolveSibling(f.getFileName() + ".mcmeta"), o.meta());
                n++;
            }
        }
        return n;
    }

    /** Pack bauen, aktivieren/deaktivieren, neu laden. */
    public static String apply(Profile prof) {
        MinecraftClient mc = MinecraftClient.getInstance();
        try {
            int n = build(prof);
            ResourcePackManager m = mc.getResourcePackManager();
            m.scanPacks();
            boolean enabled = m.getEnabledIds().contains(PACK_ID);
            if (n > 0 && !enabled) m.enable(PACK_ID);
            else if (n == 0 && enabled) m.disable(PACK_ID);
            mc.options.refreshResourcePacks(m);
            mc.reloadResources();
            Imgs.clearCaches();
            return n + " Texturen angewendet";
        } catch (Exception e) {
            ObvVisuals.LOG.error("apply fehlgeschlagen", e);
            return "Fehler: " + e.getMessage();
        }
    }

    /** Beim Spielstart: Pack schreiben und nur neu laden, wenn es noch nicht aktiv ist. */
    public static void startup(MinecraftClient mc) {
        try {
            int n = build(Store.active());
            ResourcePackManager m = mc.getResourcePackManager();
            m.scanPacks();
            if (n > 0 && !m.getEnabledIds().contains(PACK_ID)) {
                m.enable(PACK_ID);
                mc.options.refreshResourcePacks(m);
                mc.reloadResources();
            }
        } catch (Exception e) { ObvVisuals.LOG.error("startup", e); }
    }

    /** Aktuelles Pack als zip exportieren (zum Teilen / Hochladen). */
    public static Path exportZip(Profile prof) throws IOException {
        build(prof);
        Path r = root();
        Path out = Store.EXPORTS.resolve("OBV_Pack_" + prof.name.replaceAll("[^A-Za-z0-9_.-]", "_") + ".zip");
        try (OutputStream os = Files.newOutputStream(out); ZipOutputStream z = new ZipOutputStream(os); Stream<Path> w = Files.walk(r)) {
            for (Path f : w.filter(Files::isRegularFile).toList()) {
                z.putNextEntry(new ZipEntry(r.relativize(f).toString().replace('\\', '/')));
                Files.copy(f, z);
                z.closeEntry();
            }
        }
        return out;
    }
}
