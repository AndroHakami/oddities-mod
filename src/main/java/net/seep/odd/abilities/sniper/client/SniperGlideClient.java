// FILE: src/main/java/net/seep/odd/abilities/sniper/client/SniperGlideClient.java
package net.seep.odd.abilities.sniper.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.network.PacketByteBuf;

import net.seep.odd.abilities.sniper.net.SniperGlideServer;

@Environment(EnvType.CLIENT)
public final class SniperGlideClient {
    private SniperGlideClient() {}

    private static boolean inited = false;

    private static byte lastSentFlags = (byte)0x7F; // force first send
    private static float energy = 0f; // 0..1
    private static boolean active = false;

    public static void initOnce() {
        if (inited) return;
        inited = true;

        // Receive energy + active state from server (authoritative)
        ClientPlayNetworking.registerGlobalReceiver(SniperGlideServer.GLIDE_STATE_S2C, (client, handler, buf, response) -> {
            final float e = buf.readFloat();
            final boolean a = buf.readBoolean();
            client.execute(() -> {
                energy = e;
                active = a;
            });
        });

        // Send SPACE hold state to server (even if not holding the sniper item).
        // Server will ignore if you're not on sniper power.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client == null || client.player == null) return;

            byte flags = 0;
            if (client.currentScreen == null && client.options.jumpKey.isPressed()) {
                flags |= SniperGlideServer.IN_JUMP;
            }

            if (flags == lastSentFlags) return;
            lastSentFlags = flags;

            PacketByteBuf out = PacketByteBufs.create();
            out.writeByte(flags);
            ClientPlayNetworking.send(SniperGlideServer.GLIDE_CTRL_C2S, out);
        });

        // HUD only while gliding (server says active=true)
        Hud.initOnce();
    }

    public static float energy01() { return energy; }
    public static boolean isActive() { return active; }

    private static final class Hud {
        private static boolean registered = false;

        static void initOnce() {
            if (registered) return;
            registered = true;

            HudRenderCallback.EVENT.register((DrawContext ctx, float tickDelta) -> {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc == null || mc.player == null) return;

                if (!SniperGlideClient.isActive()) return; // only show while using glider

                int sw = mc.getWindow().getScaledWidth();
                int sh = mc.getWindow().getScaledHeight();

                int w = 120;
                int h = 8;

                int x = (sw - w) / 2;
                int y = sh - 55; // just above hotbar

                float e = SniperGlideClient.energy01();
                int fillW = (int)(w * e);

                ctx.getMatrices().push();
                ctx.getMatrices().translate(0, 0, 1000);

                // outline / bg
                ctx.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xAA000000);
                ctx.fill(x, y, x + w, y + h, 0x55000000);

                // fill (neutral grey/white)
                if (fillW > 0) {
                    ctx.fill(x, y, x + fillW, y + h, 0xCCFFFFFF);
                }

                ctx.getMatrices().pop();
            });
        }
    }
}
