package net.seep.odd.abilities.buddymorph.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.text.Text;
import net.seep.odd.abilities.buddymorph.BuddymorphCPM;
import net.seep.odd.abilities.buddymorph.BuddymorphNet;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public final class BuddymorphClient {
    private BuddymorphClient(){}

    private static int forceThirdPersonTicks = 0;
    private static Perspective prevPerspective = null;

    public static void init() {
        // open picker
        ClientPlayNetworking.registerGlobalReceiver(BuddymorphNet.S2C_OPEN, (client, handler, buf, response) -> {
            final int n = buf.readVarInt();
            List<String> ids = new ArrayList<>(n);
            for (int i = 0; i < n; i++) ids.add(buf.readString(256));
            client.execute(() -> {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc != null && mc.player != null) {
                    mc.player.sendMessage(Text.literal("[BM client] OPEN " + ids), true);
                }
                mc.setScreen(new SimpleBuddymorphScreen(ids));
            });
        });

        // live update
        ClientPlayNetworking.registerGlobalReceiver(BuddymorphNet.S2C_UPDATE, (client, handler, buf, response) -> {
            final int n = buf.readVarInt();
            List<String> ids = new ArrayList<>(n);
            for (int i = 0; i < n; i++) ids.add(buf.readString(256));
            client.execute(() -> {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc != null && mc.player != null) {
                    mc.player.sendMessage(Text.literal("[BM client] UPDATE " + ids), true);
                }
                if (mc != null && mc.currentScreen instanceof SimpleBuddymorphScreen scr) {
                    scr.updateIds(ids);
                }
            });
        });

        // melody + CPM
        ClientPlayNetworking.registerGlobalReceiver(BuddymorphNet.S2C_MELODY, (client, handler, buf, response) -> {
            final int ticks = buf.readVarInt();
            client.execute(() -> {
                var mc = MinecraftClient.getInstance();
                if (mc != null) {
                    if (prevPerspective == null) prevPerspective = mc.options.getPerspective();
                    mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
                    forceThirdPersonTicks = Math.max(forceThirdPersonTicks, ticks);
                    if (mc.player != null) BuddymorphCPM.playMelody(mc.player);
                }
            });
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (forceThirdPersonTicks > 0) {
                forceThirdPersonTicks--;
                if (forceThirdPersonTicks == 0 && prevPerspective != null) {
                    MinecraftClient.getInstance().options.setPerspective(prevPerspective);
                    prevPerspective = null;
                }
            }
        });
    }
}
