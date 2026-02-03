package net.seep.odd.abilities.vampire.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.seep.odd.abilities.power.VampirePower;
import net.seep.odd.abilities.vampire.VampireClientState;

@Environment(EnvType.CLIENT)
public final class VampireClientNet {
    private VampireClientNet() {}

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(VampirePower.S2C_VAMPIRE_FLAG, (client, handler, buf, responseSender) -> {
            boolean has = buf.readBoolean();
            client.execute(() -> VampireClientState.setClientVampire(has));
        });
    }
}
