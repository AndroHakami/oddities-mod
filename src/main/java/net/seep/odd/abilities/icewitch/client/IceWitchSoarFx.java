// FILE: src/main/java/net/seep/odd/abilities/icewitch/client/IceWitchSoarFx.java
package net.seep.odd.abilities.icewitch.client;

import ladysnake.satin.api.event.ShaderEffectRenderCallback;
import ladysnake.satin.api.managed.ManagedShaderEffect;
import ladysnake.satin.api.managed.ShaderEffectManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.abilities.client.ClientPowerHolder;

@Environment(EnvType.CLIENT)
public final class IceWitchSoarFx {
    private IceWitchSoarFx() {}

    private static boolean inited = false;
    private static ManagedShaderEffect effect;

    // S2C from server: is soar toggle enabled?
    private static final Identifier S2C_SOAR_TOGGLE = new Identifier(Oddities.MOD_ID, "icewitch_soar_toggle");
    private static boolean soarToggleOn = false;

    // smooth fade
    private static float strength = 0f;
    private static float target = 0f;

    // tuning
    private static final float SUBTLE_ON  = 0.52f; // when toggle ON but not flying
    private static final float STRONG_ON  = 1.00f; // when actually flying

    public static void init() {
        if (inited) return;
        inited = true;

        // receive soar toggle state from server
        ClientPlayNetworking.registerGlobalReceiver(S2C_SOAR_TOGGLE, (client, handler, buf, responder) -> {
            boolean on = buf.readBoolean();
            client.execute(() -> soarToggleOn = on);
        });

        effect = ShaderEffectManager.getInstance()
                .manage(new Identifier(Oddities.MOD_ID, "shaders/post/ice_soar_edge.json"));

        ShaderEffectRenderCallback.EVENT.register(tickDelta -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.world == null || mc.player == null) {
                strength = 0f;
                target = 0f;
                soarToggleOn = false;
                return;
            }

            // ✅ HARD GATE: only allow this shader while Ice Witch is the active power
            if (!isIceWitchSelected()) {
                soarToggleOn = false; // prevent stale toggle from keeping subtle overlay
                target = 0f;
                strength += (target - strength) * 0.20f;
                return;
            }

            boolean strong = shouldShowStrong(mc);
            boolean subtle = shouldShowSubtle(mc);

            target = strong ? STRONG_ON : (subtle ? SUBTLE_ON : 0f);

            // ease in/out
            float speed = (target > strength) ? 0.16f : 0.12f;
            strength += (target - strength) * speed;

            if (strength < 0.001f) return;

            effect.setUniformValue("Intensity", strength);
            effect.setUniformValue("Time", (float)(mc.world.getTime() + tickDelta) / 20f);

            // keep OutSize valid
            effect.setUniformValue("OutSize",
                    (float) mc.getWindow().getFramebufferWidth(),
                    (float) mc.getWindow().getFramebufferHeight());

            effect.render(tickDelta);
        });
    }

    /** ✅ Client-side active-power check */
    private static boolean isIceWitchSelected() {
        String id = ClientPowerHolder.get();
        return "ice_witch".equals(id);
    }

    /**
     * Strong only while actually soaring:
     * fall-flying WITHOUT Elytra equipped AND Ice Witch toggle is ON.
     *
     * ✅ The toggle requirement prevents other powers (like Owl) from triggering this shader.
     */
    private static boolean shouldShowStrong(MinecraftClient mc) {
        if (!soarToggleOn) return false;
        if (!mc.player.isFallFlying()) return false;

        ItemStack chest = mc.player.getInventory().getArmorStack(2);
        if (!chest.isEmpty() && chest.isOf(Items.ELYTRA)) return false;

        return true;
    }

    /** Subtle while toggle is ON (and not wearing Elytra). */
    private static boolean shouldShowSubtle(MinecraftClient mc) {
        if (!soarToggleOn) return false;

        ItemStack chest = mc.player.getInventory().getArmorStack(2);
        if (!chest.isEmpty() && chest.isOf(Items.ELYTRA)) return false;

        return true;
    }
}