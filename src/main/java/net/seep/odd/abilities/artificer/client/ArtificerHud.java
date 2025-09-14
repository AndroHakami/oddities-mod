package net.seep.odd.abilities.artificer.client;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;
import net.seep.odd.abilities.artificer.EssenceStorage;
import net.seep.odd.abilities.artificer.EssenceType;

public final class ArtificerHud implements HudRenderCallback {
    public static void register() { HudRenderCallback.EVENT.register(new ArtificerHud()); }

    @Override
    public void onHudRender(DrawContext ctx, float tickDelta) {
        var mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return;

        ItemStack held = mc.player.getMainHandStack().isEmpty() ? mc.player.getOffHandStack() : mc.player.getMainHandStack();
        if (held.isEmpty()) return;

        if (!(held.getItem() instanceof net.seep.odd.abilities.artificer.item.ArtificerVacuumItem)) return;

        int cap = EssenceStorage.getCapacity(held);
        int x = 10, y = mc.getWindow().getScaledHeight() - 40 - 6*10 - 6;

        for (var e : EssenceType.values()) {
            int have = EssenceStorage.get(held, e);
            float f = cap == 0 ? 0f : Math.min(1f, have / (float) cap);

            int w = 120, h = 9;
            ctx.fill(x, y, x + w, y + h, 0x66000000);          // bg
            int fillW = (int) Math.round(w * f);
            ctx.fill(x + 1, y + 1, x + 1 + fillW, y + h - 1, e.argb); // fill
            String label = e.key.toUpperCase() + "  " + have + "/" + cap;
            ctx.drawText(mc.textRenderer, label, x + 4, y - 9, 0xFFFFFFFF, true);

            y += h + 10;
        }
    }
}
