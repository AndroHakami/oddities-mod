// FILE: src/main/java/net/seep/odd/abilities/owl/net/OwlNetworking.java
package net.seep.odd.abilities.owl.net;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;
import net.seep.odd.abilities.owl.client.OwlDangerSenseOverlay;
import net.seep.odd.abilities.owl.client.OwlSonarClient;
import net.seep.odd.abilities.power.OwlPower;

import java.util.UUID;

@Environment(EnvType.CLIENT)
public final class OwlNetworking {
    private OwlNetworking() {}

    private static final ObjectOpenHashSet<UUID> OWL_PLAYERS = new ObjectOpenHashSet<>();

    private static float meter01 = 0f;
    private static boolean flying = false;

    public static void registerClient() {
        // Who has Owl (for wings + any-side checks)
        ClientPlayNetworking.registerGlobalReceiver(OwlPower.OWL_STATE_S2C, (client, handler, buf, responseSender) -> {
            UUID who = buf.readUuid();
            boolean owl = buf.readBoolean();
            client.execute(() -> {
                if (owl) OWL_PLAYERS.add(who);
                else OWL_PLAYERS.remove(who);

                // If WE just lost owl, hard-disable sonar vision so shader can't stick
                if (client.player != null && who.equals(client.player.getUuid()) && !owl) {
                    OwlSonarClient.setVisionActive(false);
                }
            });
        });

        // Meter sync (for HUD)
        ClientPlayNetworking.registerGlobalReceiver(OwlPower.OWL_METER_S2C, (client, handler, buf, responseSender) -> {
            float m = buf.readFloat();
            boolean f = buf.readBoolean();
            client.execute(() -> {
                meter01 = MathHelper.clamp(m, 0f, 1f);
                flying = f;
            });
        });

        // Sonar wave packet (for wave visuals, etc.)
        OwlSonarClient.registerClient();
        ClientPlayNetworking.registerGlobalReceiver(OwlPower.OWL_SONAR_S2C, (client, handler, buf, responseSender) -> {
            OwlSonarClient.handleSonarPacket(client, buf);
        });

        // ✅ Sonar VISION toggle (shader on/off)
        ClientPlayNetworking.registerGlobalReceiver(OwlPower.OWL_SONAR_VISION_S2C, (client, handler, buf, responseSender) -> {
            boolean active = buf.readBoolean();
            client.execute(() -> OwlSonarClient.setVisionActive(active));
        });

        // Danger sense overlay
        OwlDangerSenseOverlay.registerClient();

        // Elytra-like jump trigger + HUD
        ClientTickEvents.END_CLIENT_TICK.register(OwlNetworking::clientTick);
        HudRenderCallback.EVENT.register(OwlNetworking::renderHud);
    }

    public static boolean hasOwl(UUID id) {
        return OWL_PLAYERS.contains(id);
    }

    public static float meter01() { return meter01; }
    public static boolean isFlying() { return flying; }

    private static void clientTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;

        boolean iAmOwl = hasOwl(client.player.getUuid());
        if (!iAmOwl) {
            OwlDangerSenseOverlay.setStrength(0f);
            OwlSonarClient.setVisionActive(false);
            return;
        }

        // Jump behavior:
        // - if already owl-flying: pressing jump cancels flight
        // - else: if falling and not gliding, pressing jump starts flight (elytra-like)
        if (client.currentScreen == null) {
            while (client.options.jumpKey.wasPressed()) {

                // cancel while flying
                if (isFlying() || client.player.isFallFlying()) {
                    ClientPlayNetworking.send(
                            OwlPower.OWL_TOGGLE_FLIGHT_C2S,
                            net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create()
                    );
                    continue;
                }

                // start: must be airborne & falling, and not already gliding
                if (!client.player.isOnGround()
                        && !client.player.isTouchingWater()
                        && !client.player.isInLava()
                        && !client.player.isFallFlying()
                        && client.player.getVelocity().y < -0.05) {

                    ClientPlayNetworking.send(
                            OwlPower.OWL_TOGGLE_FLIGHT_C2S,
                            net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create()
                    );
                }
            }
        }

        float s = OwlDangerSenseOverlay.computeThreatStrength(client);
        OwlDangerSenseOverlay.setStrength(s);

        OwlSonarClient.clientTick(client);
    }

    private static void renderHud(DrawContext ctx, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        if (!hasOwl(client.player.getUuid())) return;

        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();

        int barW = 110;
        int barH = 8;

        int x = (w / 2) - (barW / 2);
        int y = h - 58;

        ctx.fill(x, y, x + barW, y + barH, 0x80000000);

        int fillW = MathHelper.clamp((int)(barW * meter01), 0, barW);
        int color = flying ? 0xFFB19CFF : 0xFF6FA8FF;
        ctx.fill(x, y, x + fillW, y + barH, color);

        // border
        ctx.fill(x, y, x + barW, y + 1, 0x80FFFFFF);
        ctx.fill(x, y + barH - 1, x + barW, y + barH, 0x80FFFFFF);
        ctx.fill(x, y, x + 1, y + barH, 0x80FFFFFF);
        ctx.fill(x + barW - 1, y, x + barW, y + barH, 0x80FFFFFF);
    }
}
