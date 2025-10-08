package net.seep.odd.abilities.icewitch.client;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import net.seep.odd.abilities.PowerAPI;
import net.seep.odd.abilities.client.ClientPowerHolder;
import net.seep.odd.abilities.power.IceWitchPower;
import net.seep.odd.abilities.power.Powers;
import net.seep.odd.abilities.icewitch.IceWitchPackets;

public final class IceWitchHud {
    private IceWitchHud(){}

    private static final Identifier MANA_BG = new Identifier("odd", "textures/gui/abilities/mana_bg.png");
    // tune to match your texture’s real size
    private static final int TEX_W = 92;
    private static final int TEX_H = 12;

    public static void register() {
        HudRenderCallback.EVENT.register((DrawContext draw, float tickDelta) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.player == null) return;
            String id = ClientPowerHolder.get();
            if (!"ice_witch".equals(id)) return;

            int sw = mc.getWindow().getScaledWidth();
            int sh = mc.getWindow().getScaledHeight();

            // Position: bumped up so it doesn't overlap hunger/hearts
            int x = (sw - TEX_W) / 2;
            int y = sh - 78;

            // background texture
            draw.drawTexture(MANA_BG, x, y, 0, 0, TEX_W, TEX_H, TEX_W, TEX_H);

            // fill foreground (simple colored bar inside the bg frame)
            float cur = Math.max(0f, Math.min(IceWitchPackets.CLIENT_MANA, IceWitchPackets.CLIENT_MAX));
            float pct = IceWitchPackets.CLIENT_MAX <= 0 ? 0f : (cur / IceWitchPackets.CLIENT_MAX);

            int pad = 2; // inner padding of the bg art
            int innerW = TEX_W - pad * 2;
            int innerH = TEX_H - pad * 2;
            int filled = Math.round(innerW * pct);

            // icy blue fill
            draw.fill(x + pad, y + pad, x + pad + filled, y + pad + innerH, 0xFF55CFFF);
        });
    }
}
