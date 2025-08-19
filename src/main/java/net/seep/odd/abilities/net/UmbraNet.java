package net.seep.odd.abilities.net;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.seep.odd.abilities.client.ClientPowerHolder;
import net.seep.odd.abilities.power.UmbraSoulPower;

public final class UmbraNet {
    private UmbraNet(){}

    /* ============== SHADOW HUD SYNC ============== */

    public static final Identifier SYNC_SHADOW = new Identifier("odd", "umbra_sync");

    public static void syncShadowHud(ServerPlayerEntity player, int energy, int max, boolean active) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(energy);
        buf.writeVarInt(max);
        buf.writeBoolean(active);
        ServerPlayNetworking.send(player, SYNC_SHADOW, buf);
    }

    private static int  cEnergy = 0, cMax = 1;
    private static boolean cActive = false;

    public static void registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(SYNC_SHADOW, (client, h, buf, rs) -> {
            int e = buf.readVarInt();
            int m = buf.readVarInt();
            boolean a = buf.readBoolean();
            client.execute(() -> {
                cEnergy = e;
                cMax    = Math.max(1, m);
                cActive = a;
            });
        });

        // (If you still use the built-in shadow bar, keep this; otherwise remove)
        HudRenderCallback.EVENT.register(UmbraNet::renderShadowBar);
    }

    public static boolean isClientActive() { return cActive; }
    public static int getClientEnergy() { return cEnergy; }
    public static int getClientMax() { return cMax; }

    private static void renderShadowBar(DrawContext ctx, float tickDelta) {
        if (!"umbra_soul".equals(ClientPowerHolder.get())) return;
        if (!cActive && cEnergy <= 0) return;
        var mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.isPaused()) return;

        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();

        int barW = 140, barH = 10, gap = 8;
        int y = h - 40 - barH;
        int x = (w - barW) / 2;

        ctx.fill(x - 2, y - 2, x + barW + 2, y + barH + 2, 0x66000000);
        ctx.fill(x, y, x + barW, y + barH, 0xAA000000);

        float r = Math.min(1f, cEnergy / (float) cMax);
        int fill = (int) (barW * r);
        ctx.fill(x, y, x + fill, y + barH, 0xFF4A0D0D);

        String label = "Shadow Meter";
        int tw = mc.textRenderer.getWidth(label);
        ctx.drawTextWithShadow(mc.textRenderer, label, x + (barW - tw) / 2, y - (gap + 9), 0xFFC0B8B8);
    }

    /* ============== ASTRAL INPUT (C2S) ============== */

    public static final Identifier ASTRAL_INPUT = new Identifier("odd", "astral_input");

    public static void clientSendAstralInput(int mask) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(mask);
        ClientPlayNetworking.send(ASTRAL_INPUT, buf);
    }

    /** Call once from common init. */
    public static void registerServerAstral() {
        ServerPlayNetworking.registerGlobalReceiver(ASTRAL_INPUT, (server, player, handler, buf, rs) -> {
            final int mask = buf.readVarInt();
            server.execute(() -> UmbraSoulPower.onAstralInput(player, mask));
        });
    }
}
