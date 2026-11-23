package dev.obv.visuals;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ObvVisuals implements ClientModInitializer {
    public static final String ID = "obvvisuals";
    public static final Logger LOG = LoggerFactory.getLogger("OBV VISUALS");

    public static KeyBinding MENU, ZOOM, NEXT_PROFILE;
    private static boolean zooming;
    private static int savedFov;

    @Override
    public void onInitializeClient() {
        Store.init();
        KeyBinding.Category cat = KeyBinding.Category.create(Identifier.of(ID, "main"));
        MENU = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.obvvisuals.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, cat));
        ZOOM = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.obvvisuals.zoom", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_C, cat));
        NEXT_PROFILE = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.obvvisuals.next_profile", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, cat));

        HudElementRegistry.addLast(Identifier.of(ID, "hud"), (ctx, tick) -> Hud.render(ctx));
        ClientTickEvents.END_CLIENT_TICK.register(ObvVisuals::tick);
        ClientLifecycleEvents.CLIENT_STARTED.register(PackBuilder::startup);
        ClientLifecycleEvents.CLIENT_STOPPING.register(ObvVisuals::stopZoom);
        LOG.info("OBV VISUALS geladen, Profil: {}", Store.settings.activeProfile);
    }

    private static void tick(MinecraftClient mc) {
        while (MENU.wasPressed()) if (mc.currentScreen == null) mc.setScreen(new ObvScreen());
        while (NEXT_PROFILE.wasPressed()) cycleProfile(mc);

        boolean want = ZOOM.isPressed() && mc.currentScreen == null && mc.player != null;
        if (want && !zooming) {
            savedFov = mc.options.getFov().getValue();
            mc.options.getFov().setValue(Math.max(30, Math.min(110, Store.active().zoomFov)));
            zooming = true;
        } else if (!want && zooming) {
            stopZoom(mc);
        }
    }

    private static void stopZoom(MinecraftClient mc) {
        if (zooming) { mc.options.getFov().setValue(savedFov); zooming = false; }
    }

    public static void cycleProfile(MinecraftClient mc) {
        List<String> names = new ArrayList<>(Store.profiles.keySet());
        int i = names.indexOf(Store.settings.activeProfile);
        String next = names.get((i + 1) % names.size());
        Store.settings.activeProfile = next;
        Store.saveSettings();
        String r = PackBuilder.apply(Store.active());
        if (mc.player != null) mc.player.sendMessage(Text.literal("OBV Profil: " + next + " (" + r + ")"), true);
    }
}
