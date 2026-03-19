package net.seep.odd.entity.bosswitch.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.seep.odd.entity.bosswitch.BossGolemEntity;

@Environment(EnvType.CLIENT)
public final class BossGolemCarryCameraClient {
    private BossGolemCarryCameraClient() {}

    private static boolean forced = false;
    private static Perspective previous = Perspective.FIRST_PERSON;

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(BossGolemCarryCameraClient::tick);
    }

    private static void tick(MinecraftClient client) {
        if (client.world == null || client.player == null) {
            restore(client);
            return;
        }

        boolean carried = client.player.getVehicle() instanceof BossGolemEntity;

        if (carried) {
            if (!forced) {
                previous = client.options.getPerspective();
                forced = true;
            }

            if (client.options.getPerspective() != Perspective.THIRD_PERSON_BACK) {
                client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            }
        } else {
            restore(client);
        }
    }

    private static void restore(MinecraftClient client) {
        if (!forced || client == null) return;
        client.options.setPerspective(previous);
        forced = false;
    }
}