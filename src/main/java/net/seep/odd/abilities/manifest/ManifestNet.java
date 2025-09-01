package net.seep.odd.abilities.manifest;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.seep.odd.abilities.PowerAPI;

public final class ManifestNet {
    private ManifestNet() {}

    public static final Identifier MANIFEST_CMD = new Identifier("odd", "manifest_cmd");

    /** Simple enum of actions Manifest can do. Extend at will. */
    public static final class Cmd {
        public static final int JUMP = 1;
        // add more: public static final int DASH = 2; etc.
    }

    /* ---------------- Client ---------------- */
    public static void initClient() {
        // nothing to receive yet
    }

    /** Send a Manifest command to server. */
    public static void sendCmd(int cmd) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(cmd);
        ClientPlayNetworking.send(MANIFEST_CMD, buf);
    }

    /* ---------------- Server ---------------- */
    public static void initServer() {
        ServerPlayNetworking.registerGlobalReceiver(MANIFEST_CMD, (server, player, handler, buf, rs) -> {
            int cmd = buf.readVarInt();
            server.execute(() -> handle(player, cmd));
        });
    }

    private static void handle(ServerPlayerEntity player, int cmd) {
        // Only when the player actually has the "manifest" power
        if (!"manifest".equals(PowerAPI.get(player))) return;

        switch (cmd) {
            case Cmd.JUMP -> {
                // light cooldown via attribute on player (optional)
                if (ManifestServerCooldowns.onCooldown(player)) return;

                // Upward impulse + tiny forward nudge
                var look = player.getRotationVec(1.0f);
                player.addVelocity(look.x * 0.15, 0.95, look.z * 0.15);
                player.velocityModified = true;

                // feedback
                player.world.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.8f, 1.3f);

                ManifestServerCooldowns.arm(player, 14); // ~0.7s
            }
            // add more cases for other actions
        }
    }
}
