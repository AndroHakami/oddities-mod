// FILE: src/main/java/net/seep/odd/abilities/net/MistyNet.java
package net.seep.odd.abilities.net;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.seep.odd.abilities.PowerAPI;
import net.seep.odd.abilities.misty.client.MistyHoverFx;
import net.seep.odd.abilities.power.MistyVeilPower;
import net.seep.odd.abilities.power.Powers;

public final class MistyNet {
    private MistyNet() {}

    public static final Identifier MISTY_INPUT      = new Identifier("odd", "misty_input");
    public static final Identifier S2C_HOVER_STATE  = new Identifier("odd", "misty_hover_state");

    /** Call from common init (server+client), e.g. Oddities#onInitialize. */
    public static void init() {
        // C2S input (jump + intent)
        ServerPlayNetworking.registerGlobalReceiver(MISTY_INPUT, (server, player, handler, buf, responseSender) -> {
            final int mask = buf.readVarInt();

            // Back-compat: read optional movement intent (2 floats)
            float strafe = 0f, forward = 0f;
            if (buf.readableBytes() >= 8) {
                strafe = buf.readFloat();
                forward = buf.readFloat();
            }
            final float fStrafe = strafe;
            final float fForward = forward;

            server.execute(() -> {
                var power = Powers.get(PowerAPI.get(player));
                if (power instanceof MistyVeilPower) {
                    MistyVeilPower.onClientInput(player, mask, fStrafe, fForward);
                }
            });
        });
    }

    /** Server -> client: tells client whether hover toggle is ON, and whether player is actively hovering. */
    public static void sendHoverState(ServerPlayerEntity to, boolean enabled, boolean active) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(enabled);
        buf.writeBoolean(active);
        ServerPlayNetworking.send(to, S2C_HOVER_STATE, buf);
    }

    /** Call from client init, e.g. OdditiesClient#onInitializeClient. */
    @Environment(EnvType.CLIENT)
    public static void initClient() {
        // S2C hover overlay state
        ClientPlayNetworking.registerGlobalReceiver(S2C_HOVER_STATE, (client, handler, buf, responseSender) -> {
            final boolean enabled = buf.readBoolean();
            final boolean active  = buf.readBoolean();

            client.execute(() -> MistyHoverFx.setState(enabled, active));
        });
    }

    // ---------- client send helpers ----------

    @Environment(EnvType.CLIENT)
    public static void sendInput(int mask, float strafe, float forward) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(mask);
        buf.writeFloat(strafe);
        buf.writeFloat(forward);
        ClientPlayNetworking.send(MISTY_INPUT, buf);
    }

    /** Back-compat helper: sends only the mask. */
    @Environment(EnvType.CLIENT)
    public static void sendInput(int mask) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(mask);
        ClientPlayNetworking.send(MISTY_INPUT, buf);
    }
}