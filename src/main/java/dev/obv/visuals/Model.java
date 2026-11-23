package dev.obv.visuals;

import java.util.LinkedHashMap;
import java.util.Map;

/** Datenklassen (werden per Gson als JSON gespeichert). */
public final class Model {
    private Model() {}

    public enum Source { VANILLA, OBV, CUSTOM, PACK }

    public static class SlotState {
        public boolean enabled = false;
        public Source source = Source.VANILLA;
        public String custom = "";
        public String pack = "";
        public int tint = 0x8B5CF6;
        public float tintStrength = 0f;

        public boolean isActive() {
            return enabled && (source != Source.VANILLA || tintStrength > 0.001f);
        }
    }

    public static class HudElem {
        public boolean enabled = false;
        public float x = 0.01f, y = 0.01f, scale = 1.0f;
        public int color = 0xFFFFFF;
        public HudElem() {}
        public HudElem(boolean en, float x, float y, int color) { this.enabled = en; this.x = x; this.y = y; this.color = color; }
    }

    public static class Hud {
        public HudElem fps = new HudElem(true, 0.01f, 0.01f, 0xFFFFFF);
        public HudElem cps = new HudElem(true, 0.01f, 0.06f, 0x8B5CF6);
        public HudElem coords = new HudElem(true, 0.01f, 0.11f, 0x22D3EE);
        public HudElem armor = new HudElem(true, 0.93f, 0.35f, 0xFFFFFF);
        public float bgAlpha = 0.45f;
        public boolean shadow = true;
    }

    public static class Profile {
        public String name = "Profil";
        public Map<String, SlotState> slots = new LinkedHashMap<>();
        public Hud hud = new Hud();
        public int zoomFov = 30;

        public SlotState state(String slotId) {
            return slots.computeIfAbsent(slotId, k -> new SlotState());
        }
    }

    public static class Settings {
        public String activeProfile = "OBV Standard";
        public float transparency = 0.94f;
        public float menuSize = 1.0f;
        public int theme = 0;
        public boolean animations = true;
        public boolean autoApplyOnClose = true;
        public boolean firstRun = true;
    }
}
