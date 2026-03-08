// FILE: src/main/java/net/seep/odd/abilities/owl/net/OwlNetworking.java
package net.seep.odd.abilities.owl.net;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.seep.odd.abilities.power.OwlPower;

import java.util.UUID;

public final class OwlNetworking {
    private OwlNetworking() {}

    /* =========================
       Shared client mirrors
       ========================= */

    private static final ObjectOpenHashSet<UUID> OWL_PLAYERS = new ObjectOpenHashSet<>();

    private static float meter01 = 0f;
    private static boolean flying = false;
    private static boolean flightEnabled = true;

    public static boolean hasOwl(UUID id) { return OWL_PLAYERS.contains(id); }
    public static float meter01() { return meter01; }
    public static boolean isFlying() { return flying; }
    public static boolean isFlightEnabled() { return flightEnabled; }

    /* =========================
       SERVER registration (common init)
       ========================= */

    public static void registerServer() {
        // Jump -> start/cancel owl flight
        ServerPlayNetworking.registerGlobalReceiver(OwlPower.OWL_TOGGLE_FLIGHT_C2S, (server, player, handler, buf, responseSender) -> {
            server.execute(() -> OwlPower.onClientToggleFlightRequest(player));
        });

        // Join: sync “who has owl”
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity joiner = handler.getPlayer();

            // send known owl states to joiner
            for (ServerPlayerEntity other : server.getPlayerManager().getPlayerList()) {
                sendOwlStateTo(joiner, other.getUuid(), OwlPower.hasOwl(other));
            }
            // broadcast joiner to everyone (including self)
            broadcastOwlState(server, joiner.getUuid(), OwlPower.hasOwl(joiner));

            // ensure server-side meter/toggles exist
            OwlPower.ensureInit(joiner);

            // sonar vision hard-sync on join (prevents stuck shader)
            // OwlPower will send proper state via its activate(), but we always ensure OFF by default
            // (if you later want persistence, add a packet for it).
            PacketByteBuf out = PacketByteBufs.create();
            out.writeBoolean(false);
            ServerPlayNetworking.send(joiner, OwlPower.OWL_SONAR_VISION_S2C, out);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.player.getUuid();
            OwlPower.onDisconnect(handler.player);

            // broadcast “not owl” so wings/any-side state clears
            broadcastOwlState(server, id, false);
        });

        // Server tick: run owl logic for all players
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                OwlPower.serverTick(p);
            }
        });
    }

    private static void sendOwlStateTo(ServerPlayerEntity to, UUID who, boolean owl) {
        PacketByteBuf out = PacketByteBufs.create();
        out.writeUuid(who);
        out.writeBoolean(owl);
        ServerPlayNetworking.send(to, OwlPower.OWL_STATE_S2C, out);
    }

    private static void broadcastOwlState(MinecraftServer server, UUID who, boolean owl) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            sendOwlStateTo(p, who, owl);
        }
    }

    /* =========================
       CLIENT registration (client init)
       ========================= */

    @Environment(EnvType.CLIENT)
    public static void registerClient() {
        // Who has Owl (for wings + any-side checks)
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                OwlPower.OWL_STATE_S2C, (client, handler, buf, responseSender) -> {
                    UUID who = buf.readUuid();
                    boolean owl = buf.readBoolean();
                    client.execute(() -> {
                        if (owl) OWL_PLAYERS.add(who);
                        else OWL_PLAYERS.remove(who);

                        // If WE just lost owl, hard-disable sonar vision so shader can't stick
                        if (client.player != null && who.equals(client.player.getUuid()) && !owl) {
                            net.seep.odd.abilities.owl.client.OwlSonarClient.setVisionActive(false);
                        }
                    });
                });

        // Meter sync (for HUD) + flightEnabled
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                OwlPower.OWL_METER_S2C, (client, handler, buf, responseSender) -> {
                    float m = buf.readFloat();
                    boolean f = buf.readBoolean();
                    boolean fe = true;
                    if (buf.readableBytes() >= 1) fe = buf.readBoolean();
                    final boolean fEnabled = fe;

                    client.execute(() -> {
                        meter01 = MathHelper.clamp(m, 0f, 1f);
                        flying = f;
                        flightEnabled = fEnabled;
                    });
                });

        // Sonar wave packet (for wave visuals, etc.)
        net.seep.odd.abilities.owl.client.OwlSonarClient.registerClient();
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                OwlPower.OWL_SONAR_S2C, (client, handler, buf, responseSender) -> {
                    net.seep.odd.abilities.owl.client.OwlSonarClient.handleSonarPacket(client, buf);
                });

        // Sonar VISION toggle (shader on/off)
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                OwlPower.OWL_SONAR_VISION_S2C, (client, handler, buf, responseSender) -> {
                    boolean active = buf.readBoolean();
                    client.execute(() -> net.seep.odd.abilities.owl.client.OwlSonarClient.setVisionActive(active));
                });

        // Elytra-like jump trigger + HUD (NO danger-sense stuff)
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(OwlNetworking::clientTick);
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register(OwlNetworking::renderHud);
    }

    @Environment(EnvType.CLIENT)
    private static void clientTick(net.minecraft.client.MinecraftClient client) {
        if (client.player == null || client.world == null) return;

        boolean iAmOwl = hasOwl(client.player.getUuid());
        if (!iAmOwl) {
            net.seep.odd.abilities.owl.client.OwlSonarClient.setVisionActive(false);
            return;
        }

        // Jump behavior:
        // - if already owl-flying: pressing jump cancels flight
        // - else: if flight is ENABLED and player is falling, pressing jump requests start
        if (client.currentScreen == null) {
            while (client.options.jumpKey.wasPressed()) {

                // cancel while flying
                if (isFlying() || client.player.isFallFlying()) {
                    net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                            OwlPower.OWL_TOGGLE_FLIGHT_C2S,
                            net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create()
                    );
                    continue;
                }

                // if flight toggle is OFF, do nothing
                if (!isFlightEnabled()) continue;

                // start: must be airborne & falling
                if (!client.player.isOnGround()
                        && !client.player.isTouchingWater()
                        && !client.player.isInLava()
                        && !client.player.isFallFlying()
                        && client.player.getVelocity().y < -0.05) {

                    net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                            OwlPower.OWL_TOGGLE_FLIGHT_C2S,
                            net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create()
                    );
                }
            }
        }

        net.seep.odd.abilities.owl.client.OwlSonarClient.clientTick(client);
    }

    @Environment(EnvType.CLIENT)
    private static void renderHud(net.minecraft.client.gui.DrawContext ctx, float tickDelta) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client.player == null) return;
        if (!hasOwl(client.player.getUuid())) return;

        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();

        int barW = 110;
        int barH = 8;

        int x = (w / 2) - (barW / 2);
        int y = h - 58;

        // background
        ctx.fill(x, y, x + barW, y + barH, 0x80000000);

        int fillW = MathHelper.clamp((int)(barW * meter01), 0, barW);

        // if flight is disabled, make it look “muted”
        int color;
        if (!flightEnabled) color = 0xFF3A3A3A;
        else color = flying ? 0xFFB19CFF : 0xFF6FA8FF;

        ctx.fill(x, y, x + fillW, y + barH, color);

        // border
        ctx.fill(x, y, x + barW, y + 1, 0x80FFFFFF);
        ctx.fill(x, y + barH - 1, x + barW, y + barH, 0x80FFFFFF);
        ctx.fill(x, y, x + 1, y + barH, 0x80FFFFFF);
        ctx.fill(x + barW - 1, y, x + barW, y + barH, 0x80FFFFFF);
    }
}