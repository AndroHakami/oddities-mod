
package net.seep.odd.item.outerblaster.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

import net.seep.odd.item.outerblaster.OuterBlasterItem;

@Environment(EnvType.CLIENT)
public final class OuterBlasterClient {
    private static boolean inited = false;

    private OuterBlasterClient() {}

    public static void init() {
        if (inited) return;
        inited = true;

        OuterBlasterHudFx.init();
        OuterBlasterImpactFx.init();

        ClientTickEvents.END_CLIENT_TICK.register(OuterBlasterClient::onClientTick);
    }

    private static void onClientTick(MinecraftClient client) {
        OuterBlasterItem.clientTickDownAnimCounters();

        if (client == null || client.player == null) {
            OuterBlasterHudFx.onHud(false, 0.0f, 1.0f, false);
            return;
        }

        PlayerEntity player = client.player;
        ItemStack main = player.getMainHandStack();
        ItemStack off = player.getOffHandStack();

        ItemStack held = ItemStack.EMPTY;
        if (main.getItem() instanceof OuterBlasterItem) held = main;
        else if (off.getItem() instanceof OuterBlasterItem) held = off;

        if (!held.isEmpty()) {
            float heat = OuterBlasterItem.getHeat(held);
            boolean overheated = OuterBlasterItem.isOverheated(held);
            boolean show = overheated || heat > 0.1f;
            OuterBlasterHudFx.onHud(show, heat, OuterBlasterItem.MAX_HEAT, overheated);
        } else {
            OuterBlasterHudFx.onHud(false, 0.0f, 1.0f, false);
        }
    }
}
