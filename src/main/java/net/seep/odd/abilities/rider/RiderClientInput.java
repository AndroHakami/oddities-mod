package net.seep.odd.abilities.rider;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.seep.odd.entity.car.RiderCarEntity;
import org.lwjgl.glfw.GLFW;

import static net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.*;
import static net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create;

/**
 * Client-only input for Rider radio controls (no vehicle movement here).
 *
 * Default keys (change to your liking / expose in Controls):
 *  B   -> toggle on/off
 *  N   -> next track
 *  V   -> previous track
 *  ]   -> volume up
 *  [   -> volume down
 */
@Environment(EnvType.CLIENT)
public final class RiderClientInput {
    private RiderClientInput() {}

    // Reuse RiderNet's packet/channel + enum
    private static final Identifier RADIO = RiderNet.RADIO;

    private static KeyBinding KB_RADIO_TOGGLE;
    private static KeyBinding KB_RADIO_NEXT;
    private static KeyBinding KB_RADIO_PREV;
    private static KeyBinding KB_VOL_UP;
    private static KeyBinding KB_VOL_DN;

    public static void init() {
        KB_RADIO_TOGGLE = register("key.odd.radio_toggle", GLFW.GLFW_KEY_B);
        KB_RADIO_NEXT   = register("key.odd.radio_next",   GLFW.GLFW_KEY_N);
        KB_RADIO_PREV   = register("key.odd.radio_prev",   GLFW.GLFW_KEY_V);
        KB_VOL_UP       = register("key.odd.radio_vol_up", GLFW.GLFW_KEY_RIGHT_BRACKET);
        KB_VOL_DN       = register("key.odd.radio_vol_dn", GLFW.GLFW_KEY_LEFT_BRACKET);

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.player == null) return;

            // Only send when actually in the car (prevents spam while on foot)
            if (!(mc.player.getVehicle() instanceof RiderCarEntity car)) return;

            if (KB_RADIO_TOGGLE.wasPressed()) send(RiderNet.RadioCmd.TOGGLE, 0f);

            if (KB_RADIO_NEXT.wasPressed())   send(RiderNet.RadioCmd.NEXT,   0f);
            if (KB_RADIO_PREV.wasPressed())   send(RiderNet.RadioCmd.PREV,   0f);

            // Volume is absolute (0..1). Compute target client-side so server just sets it.
            if (KB_VOL_UP.wasPressed()) {
                float cur = car.getRadioVolumeClient();
                float tgt = MathHelper.clamp(cur + 0.10f, 0f, 1f);
                send(RiderNet.RadioCmd.VOLUME_SET, tgt);
            }
            if (KB_VOL_DN.wasPressed()) {
                float cur = car.getRadioVolumeClient();
                float tgt = MathHelper.clamp(cur - 0.10f, 0f, 1f);
                send(RiderNet.RadioCmd.VOLUME_SET, tgt);
            }
        });
    }

    /* ---------- helpers ---------- */

    private static KeyBinding register(String key, int glfw) {
        return KeyBindingHelper.registerKeyBinding(
                new KeyBinding(key, glfw, "key.categories.odd")
        );
    }

    private static void send(RiderNet.RadioCmd cmd, float value) {
        PacketByteBuf buf = create();
        buf.writeEnumConstant(cmd);
        if (cmd == RiderNet.RadioCmd.VOLUME_SET) buf.writeFloat(value);
        ClientPlayNetworking.send(RADIO, buf);
    }
}
