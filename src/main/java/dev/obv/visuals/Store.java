package dev.obv.visuals;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.obv.visuals.Model.*;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/** Laden/Speichern von Einstellungen, Profilen und Custom-Texturen. */
public final class Store {
    private Store() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("obvvisuals");
    public static final Path PROFILES = DIR.resolve("profiles");
    public static final Path CUSTOM = DIR.resolve("textures");
    public static final Path EXPORTS = DIR.resolve("exports");

    public static Settings settings = new Settings();
    public static final Map<String, Profile> profiles = new LinkedHashMap<>();

    public static void init() {
        try {
            Files.createDirectories(PROFILES);
            Files.createDirectories(EXPORTS);
            for (Slots.Cat c : Slots.Cat.values()) Files.createDirectories(CUSTOM.resolve(c.name().toLowerCase(Locale.ROOT)));
        } catch (IOException e) {
            ObvVisuals.LOG.error("Ordner konnten nicht erstellt werden", e);
        }
        Path sp = DIR.resolve("settings.json");
        if (Files.exists(sp)) {
            try {
                Settings s = GSON.fromJson(Files.readString(sp, StandardCharsets.UTF_8), Settings.class);
                if (s != null) settings = s;
            } catch (Exception e) { ObvVisuals.LOG.warn("settings.json defekt, nutze Standard", e); }
        }
        profiles.clear();
        try (Stream<Path> st = Files.list(PROFILES)) {
            st.filter(p -> p.toString().endsWith(".json")).sorted().forEach(p -> {
                try {
                    Profile pr = GSON.fromJson(Files.readString(p, StandardCharsets.UTF_8), Profile.class);
                    if (pr != null && pr.name != null && !pr.name.isBlank()) profiles.put(pr.name, pr);
                } catch (Exception e) { ObvVisuals.LOG.warn("Profil defekt: {}", p, e); }
            });
        } catch (IOException ignored) {}
        if (profiles.isEmpty()) createDefaults();
        if (!profiles.containsKey(settings.activeProfile)) settings.activeProfile = profiles.keySet().iterator().next();
        saveSettings();
    }

    private static void createDefaults() {
        Profile obv = new Profile();
        obv.name = "OBV Standard";
        for (String id : new String[]{"crystal_body", "crystal_beam", "obsidian"}) {
            SlotState s = obv.state(id);
            s.enabled = true;
            s.source = Source.OBV;
        }
        profiles.put(obv.name, obv);
        saveProfile(obv);
        Profile van = new Profile();
        van.name = "Vanilla";
        profiles.put(van.name, van);
        saveProfile(van);
    }

    public static Profile active() {
        Profile p = profiles.get(settings.activeProfile);
        if (p == null) { p = profiles.values().iterator().next(); settings.activeProfile = p.name; }
        return p;
    }

    public static void saveSettings() {
        try { Files.writeString(DIR.resolve("settings.json"), GSON.toJson(settings), StandardCharsets.UTF_8); }
        catch (IOException e) { ObvVisuals.LOG.error("settings speichern", e); }
    }

    private static String fileName(String name) { return name.replaceAll("[^A-Za-z0-9 _.-]", "_") + ".json"; }

    public static void saveProfile(Profile p) {
        try { Files.writeString(PROFILES.resolve(fileName(p.name)), GSON.toJson(p), StandardCharsets.UTF_8); }
        catch (IOException e) { ObvVisuals.LOG.error("Profil speichern", e); }
    }

    public static String uniqueName(String wanted) {
        String base = wanted.isBlank() ? "Profil" : wanted.trim();
        String n = base; int i = 2;
        while (profiles.containsKey(n)) n = base + " " + i++;
        return n;
    }

    public static Profile create(String name) {
        Profile p = new Profile();
        p.name = uniqueName(name);
        profiles.put(p.name, p);
        saveProfile(p);
        return p;
    }

    public static Profile duplicate(Profile src) {
        Profile p = GSON.fromJson(GSON.toJson(src), Profile.class);
        p.name = uniqueName(src.name + " Kopie");
        profiles.put(p.name, p);
        saveProfile(p);
        return p;
    }

    public static boolean rename(Profile p, String newName) {
        String n = newName.trim();
        if (n.isEmpty() || (profiles.containsKey(n) && !n.equals(p.name))) return false;
        try { Files.deleteIfExists(PROFILES.resolve(fileName(p.name))); } catch (IOException ignored) {}
        profiles.remove(p.name);
        boolean wasActive = p.name.equals(settings.activeProfile);
        p.name = n;
        profiles.put(n, p);
        if (wasActive) { settings.activeProfile = n; saveSettings(); }
        saveProfile(p);
        return true;
    }

    public static boolean delete(Profile p) {
        if (profiles.size() <= 1) return false;
        try { Files.deleteIfExists(PROFILES.resolve(fileName(p.name))); } catch (IOException ignored) {}
        profiles.remove(p.name);
        if (p.name.equals(settings.activeProfile)) { settings.activeProfile = profiles.keySet().iterator().next(); saveSettings(); }
        return true;
    }

    public static Path export(Profile p) throws IOException {
        Path f = EXPORTS.resolve(fileName(p.name));
        Files.writeString(f, GSON.toJson(p), StandardCharsets.UTF_8);
        return f;
    }

    public static Profile importFile(Path f) throws IOException {
        Profile p = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), Profile.class);
        if (p == null) throw new IOException("Leere Datei");
        if (p.name == null) p.name = f.getFileName().toString().replace(".json", "");
        p.name = uniqueName(p.name);
        if (p.hud == null) p.hud = new Model.Hud();
        if (p.slots == null) p.slots = new LinkedHashMap<>();
        profiles.put(p.name, p);
        saveProfile(p);
        return p;
    }

    public static List<Path> exportFiles() {
        try (Stream<Path> st = Files.list(EXPORTS)) {
            return st.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        } catch (IOException e) { return List.of(); }
    }

    public static Path customDir(Slots.Cat c) { return CUSTOM.resolve(c.name().toLowerCase(Locale.ROOT)); }
}
