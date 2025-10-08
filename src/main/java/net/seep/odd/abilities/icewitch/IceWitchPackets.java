// FILE: net/seep/odd/abilities/icewitch/IceWitchPackets.java
package net.seep.odd.abilities.icewitch;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.seep.odd.abilities.PowerAPI;
import net.seep.odd.abilities.power.IceWitchPower;

public final class IceWitchPackets {
    private IceWitchPackets(){}

    private static final String MODID = "odd";
    public static final Identifier C2S_INPUT  = new Identifier(MODID, "ice_c2s_input");     // mask, strafe, forward
    public static final Identifier S2C_MANA   = new Identifier(MODID, "ice_s2c_mana");      // cur, max

    // input mask bits (client -> server)
    public static final int MASK_JUMP_HELD = 1;

    // client HUD cache (updated by S2C_MANA)
    public static float CLIENT_MANA = IceWitchPower.MAX_MANA;
    public static float CLIENT_MAX  = IceWitchPower.MAX_MANA;

    // visibility gating: consider HUD "active" if a mana packet was received in last 40 ticks
    private static int lastSyncAge = -10_000;
    public static boolean hudActive(MinecraftClient mc){
        if (mc == null || mc.player == null) return false;
        return (mc.player.age - lastSyncAge) <= 40;
    }

    /* =================== SERVER REG =================== */
    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(C2S_INPUT, (server, player, handler, buf, responseSender) -> {
            int mask = buf.readVarInt();
            float strafe = buf.readFloat();
            float forward= buf.readFloat();
            server.execute(() -> IceWitchPower.onClientInput(player, mask, strafe, forward));
        });
    }

    /* =================== CLIENT REG =================== */
    public static void registerClient() {
        // HUD sync
        ClientPlayNetworking.registerGlobalReceiver(S2C_MANA, (client, handler, buf, responseSender) -> {
            float m = buf.readFloat();
            float mx= buf.readFloat();
            client.execute(() -> {
                CLIENT_MANA = m;
                CLIENT_MAX = mx;
                if (client.player != null) lastSyncAge = client.player.age;
            });
        });

        // Every client tick, ship inputs (server will ignore if you’re not Ice Witch)
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.player == null) return;

            // movement intent from the client
            float strafe  = mc.player.input.movementSideways;
            float forward = mc.player.input.movementForward;
            int mask = 0;
            if (mc.player.input.jumping) mask |= MASK_JUMP_HELD;

            PacketByteBuf out = PacketByteBufs.create();
            out.writeVarInt(mask);
            out.writeFloat(strafe);
            out.writeFloat(forward);
            ClientPlayNetworking.send(C2S_INPUT, out);
        });
    }

    /* =================== UTIL (server -> client) =================== */
    public static void syncManaToClient(ServerPlayerEntity p, float mana, float max) {
        PacketByteBuf out = PacketByteBufs.create();
        out.writeFloat(mana);
        out.writeFloat(max);
        ServerPlayNetworking.send(p, S2C_MANA, out);
    }
}
