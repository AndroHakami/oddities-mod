package net.seep.odd.abilities.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.seep.odd.abilities.net.PossessionControlPacket;

public final class PossessionClientController {
    private static boolean possessing = false;
    private static boolean actionLatch = false;

    private PossessionClientController(){}

    public static void setPossessing(boolean v) {
        possessing = v;
        actionLatch = false;
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!possessing) return;
            var o = client.options;
            var p = client.player;
            if (p == null) return;

            boolean action = false;
            // use left click as "action" while possessed (simple & always available)
            if (o.attackKey.isPressed()) {
                if (!actionLatch) action = true;
                actionLatch = true;
            } else {
                actionLatch = false;
            }

            PossessionControlPacket.send(new PossessionControlPacket.State(
                    o.forwardKey.isPressed(), o.backKey.isPressed(),
                    o.leftKey.isPressed(), o.rightKey.isPressed(),
                    o.jumpKey.isPressed(), o.sprintKey.isPressed(),
                    p.getYaw(), p.getPitch(), action
            ));
        });
    }
}
