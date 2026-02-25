// src/main/java/net/seep/odd/abilities/cultist/client/CultistCpmBridge.java
package net.seep.odd.abilities.cultist.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;

import net.seep.odd.abilities.overdrive.client.CpmHooks;

@Environment(EnvType.CLIENT)
public final class CultistCpmBridge {
    private CultistCpmBridge() {}

    private static boolean active = false;
    private static Perspective prev = null;

    /** Called from S2C packet: start CPM "touch" + force third person. */
    public static void touchStart() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.options == null) return;

        CultistFx.init();              // ensure shader is registered
        CultistFx.setActive(true);     // ✅ enable overlay

        if (!active) {
            active = true;
            prev = mc.options.getPerspective();
        }

        mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);

        CpmHooks.stop("touch");
        CpmHooks.play("touch");
    }

    public static void touchStop() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.options == null) return;

        CultistFx.init();
        CultistFx.setActive(false);    // ✅ disable overlay

        CpmHooks.stop("touch");

        if (active) {
            active = false;
            mc.options.setPerspective(prev != null ? prev : Perspective.FIRST_PERSON);
            prev = null;
        }
    }
}