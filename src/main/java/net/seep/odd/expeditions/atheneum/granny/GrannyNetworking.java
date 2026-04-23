package net.seep.odd.expeditions.atheneum.granny;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public final class GrannyNetworking {
    public static final Identifier EVENT_STATE = new Identifier("odd", "granny_event_state");
    public static final Identifier SPAWN_CUE = new Identifier("odd", "granny_spawn_cue");

    private GrannyNetworking() {}

    public static void initClient() {
        ClientPlayNetworking.registerGlobalReceiver(EVENT_STATE, (client, handler, buf, responseSender) -> {
            boolean active = buf.readBoolean();
            client.execute(() -> GrannyClientState.setActive(active));
        });

        ClientPlayNetworking.registerGlobalReceiver(SPAWN_CUE, (client, handler, buf, responseSender) ->
                client.execute(GrannyClientState::triggerSpawnCue));
    }
}