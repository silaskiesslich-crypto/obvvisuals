package dev.obv.visuals;

import java.util.ArrayList;
import java.util.List;

/** Alle austauschbaren Texturen. Ziele werden zur Laufzeit gegen die echten Vanilla-Ressourcen geprüft. */
public final class Slots {
    private Slots() {}

    public enum Cat { CRYSTALS, ANCHORS, ITEMS, ARMOR, PARTICLES, SKY }

    public record Slot(String id, Cat cat, String name, String[] targets) {}

    public static final List<Slot> ALL = new ArrayList<>();

    private static void add(Cat c, String id, String name, String... t) { ALL.add(new Slot(id, c, name, t)); }

    private static String[] seq(String prefix, int n) {
        String[] a = new String[n];
        for (int i = 0; i < n; i++) a[i] = prefix + i + ".png";
        return a;
    }

    private static String[] alpha(String prefix) {
        String[] a = new String[26];
        for (int i = 0; i < 26; i++) a[i] = prefix + (char) ('a' + i) + ".png";
        return a;
    }

    private static final String I = "textures/item/";
    private static final String B = "textures/block/";
    private static final String P = "textures/particle/";
    private static final String E = "textures/environment/";
    private static final String A = "textures/entity/equipment/humanoid/";
    private static final String AL = "textures/entity/equipment/humanoid_leggings/";

    static {
        // CRYSTALS
        add(Cat.CRYSTALS, "crystal_body", "End Crystal", "textures/entity/end_crystal/end_crystal.png");
        add(Cat.CRYSTALS, "crystal_beam", "Crystal Beam", "textures/entity/end_crystal/end_crystal_beam.png");
        add(Cat.CRYSTALS, "obsidian", "Obsidian", B + "obsidian.png");
        add(Cat.CRYSTALS, "crying_obsidian", "Crying Obsidian", B + "crying_obsidian.png");
        add(Cat.CRYSTALS, "bedrock", "Bedrock", B + "bedrock.png");
        // ANCHORS
        add(Cat.ANCHORS, "anchor_top", "Anchor Oberseite (aktiv)", B + "respawn_anchor_top.png");
        add(Cat.ANCHORS, "anchor_top_off", "Anchor Oberseite (inaktiv)", B + "respawn_anchor_top_off.png");
        add(Cat.ANCHORS, "anchor_side", "Anchor Seiten (alle Ladungen)", seq(B + "respawn_anchor_side", 5));
        add(Cat.ANCHORS, "anchor_bottom", "Anchor Unterseite", B + "respawn_anchor_bottom.png");
        add(Cat.ANCHORS, "glowstone", "Glowstone (Anchor-Ladung)", B + "glowstone.png");
        // ITEMS
        add(Cat.ITEMS, "sword_netherite", "Netherite-Schwert", I + "netherite_sword.png");
        add(Cat.ITEMS, "sword_diamond", "Diamant-Schwert", I + "diamond_sword.png");
        add(Cat.ITEMS, "sword_iron", "Eisen-Schwert", I + "iron_sword.png");
        add(Cat.ITEMS, "sword_golden", "Gold-Schwert", I + "golden_sword.png");
        add(Cat.ITEMS, "sword_stone", "Stein-Schwert", I + "stone_sword.png");
        add(Cat.ITEMS, "sword_wooden", "Holz-Schwert", I + "wooden_sword.png");
        add(Cat.ITEMS, "apple_golden", "Goldener Apfel", I + "golden_apple.png");
        add(Cat.ITEMS, "apple_ench", "Verzauberter Goldapfel", I + "enchanted_golden_apple.png");
        add(Cat.ITEMS, "apple", "Apfel", I + "apple.png");
        add(Cat.ITEMS, "pearl", "Enderperle", I + "ender_pearl.png");
        add(Cat.ITEMS, "totem", "Totem der Unsterblichkeit", I + "totem_of_undying.png");
        add(Cat.ITEMS, "bow", "Bogen (alle Zugstufen)", I + "bow.png", I + "bow_pulling_0.png", I + "bow_pulling_1.png", I + "bow_pulling_2.png");
        add(Cat.ITEMS, "crossbow", "Armbrust (alle Zustände)", I + "crossbow_standard.png", I + "crossbow_pulling_0.png",
                I + "crossbow_pulling_1.png", I + "crossbow_pulling_2.png", I + "crossbow_arrow.png", I + "crossbow_firework.png");
        add(Cat.ITEMS, "tools_netherite", "Netherite-Werkzeuge", I + "netherite_pickaxe.png", I + "netherite_axe.png",
                I + "netherite_shovel.png", I + "netherite_hoe.png");
        add(Cat.ITEMS, "tools_diamond", "Diamant-Werkzeuge", I + "diamond_pickaxe.png", I + "diamond_axe.png",
                I + "diamond_shovel.png", I + "diamond_hoe.png");
        add(Cat.ITEMS, "shield", "Schild", "textures/entity/shield/shield_base_nopattern.png", "textures/entity/shield/shield_base.png",
                "textures/entity/shield_base_nopattern.png", "textures/entity/shield_base.png");
        // ARMOR
        add(Cat.ARMOR, "armor_netherite", "Netherite-Rüstung", A + "netherite.png", AL + "netherite.png");
        add(Cat.ARMOR, "armor_diamond", "Diamant-Rüstung", A + "diamond.png", AL + "diamond.png");
        add(Cat.ARMOR, "armor_iron", "Eisen-Rüstung", A + "iron.png", AL + "iron.png");
        add(Cat.ARMOR, "armor_gold", "Gold-Rüstung", A + "gold.png", AL + "gold.png");
        add(Cat.ARMOR, "armor_chain", "Kettenrüstung", A + "chainmail.png", AL + "chainmail.png");
        add(Cat.ARMOR, "armor_leather", "Lederrüstung", A + "leather.png", AL + "leather.png",
                A + "leather_overlay.png", AL + "leather_overlay.png");
        // PARTICLES
        add(Cat.PARTICLES, "p_crit", "Kritischer Treffer", P + "critical_hit.png");
        add(Cat.PARTICLES, "p_damage", "Schadens-Partikel", P + "damage.png");
        add(Cat.PARTICLES, "p_sweep", "Schwung-Angriff", seq(P + "sweep_", 8));
        add(Cat.PARTICLES, "p_explosion", "Explosion", seq(P + "explosion_", 16));
        add(Cat.PARTICLES, "p_enchant", "Verzauberungs-Glyphen", alpha(P + "sga_"));
        add(Cat.PARTICLES, "p_generic", "Rauch / Totem / Tränke (generic)", seq(P + "generic_", 8));
        add(Cat.PARTICLES, "p_heart", "Herzen", P + "heart.png");
        // SKY
        add(Cat.SKY, "sky_sun", "Sonne", E + "sun.png", E + "celestial/sun.png");
        add(Cat.SKY, "sky_moon", "Mond (alle Phasen)",
                E + "moon_phases.png",
                E + "moon/full_moon.png", E + "moon/waning_gibbous.png", E + "moon/third_quarter.png", E + "moon/waning_crescent.png",
                E + "moon/new_moon.png", E + "moon/waxing_crescent.png", E + "moon/first_quarter.png", E + "moon/waxing_gibbous.png",
                E + "celestial/moon/full_moon.png", E + "celestial/moon/waning_gibbous.png", E + "celestial/moon/third_quarter.png",
                E + "celestial/moon/waning_crescent.png", E + "celestial/moon/new_moon.png", E + "celestial/moon/waxing_crescent.png",
                E + "celestial/moon/first_quarter.png", E + "celestial/moon/waxing_gibbous.png");
        add(Cat.SKY, "sky_clouds", "Wolken", E + "clouds.png");
        add(Cat.SKY, "sky_end", "End-Himmel", E + "end_sky.png");
        add(Cat.SKY, "sky_rain", "Regen", E + "rain.png");
        add(Cat.SKY, "sky_snow", "Schnee", E + "snow.png");
    }

    public static Slot byId(String id) {
        for (Slot s : ALL) if (s.id().equals(id)) return s;
        return null;
    }

    public static List<Slot> of(Cat c) {
        List<Slot> l = new ArrayList<>();
        for (Slot s : ALL) if (s.cat() == c) l.add(s);
        return l;
    }
}
