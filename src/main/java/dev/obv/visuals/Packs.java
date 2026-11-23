package dev.obv.visuals;

import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Zugriff auf installierte Resourcepacks (Ordner oder .zip) für den Pack Mixer. */
public final class Packs {
    private Packs() {}

    public static Path dir() { return MinecraftClient.getInstance().getResourcePackDir(); }

    public static List<String> list() {
        List<String> r = new ArrayList<>();
        try (Stream<Path> st = Files.list(dir())) {
            st.forEach(p -> {
                String n = p.getFileName().toString();
                if (n.equals(PackBuilder.PACK_DIR) || n.startsWith("OBV_Export")) return;
                if (Files.isDirectory(p) && Files.exists(p.resolve("pack.mcmeta"))) r.add(n);
                else if (n.toLowerCase(Locale.ROOT).endsWith(".zip")) r.add(n);
            });
        } catch (IOException ignored) {}
        Collections.sort(r, String.CASE_INSENSITIVE_ORDER);
        return r;
    }

    /** Liest assets/minecraft/<target> aus dem Pack, oder null. */
    public static byte[] read(String pack, String target) {
        Path p = dir().resolve(pack);
        String entry = "assets/minecraft/" + target;
        try {
            if (Files.isDirectory(p)) {
                Path f = p.resolve(entry);
                return Files.exists(f) ? Files.readAllBytes(f) : null;
            }
            if (Files.isRegularFile(p)) {
                try (ZipFile z = new ZipFile(p.toFile())) {
                    ZipEntry e = z.getEntry(entry);
                    if (e == null) return null;
                    try (InputStream in = z.getInputStream(e)) { return in.readAllBytes(); }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    public static boolean provides(String pack, Slots.Slot s) {
        for (String t : s.targets()) if (Imgs.vanillaExists(t) && read(pack, t) != null) return true;
        return false;
    }
}
