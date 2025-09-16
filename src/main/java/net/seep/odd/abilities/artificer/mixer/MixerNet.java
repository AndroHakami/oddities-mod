package net.seep.odd.abilities.artificer.mixer;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.seep.odd.abilities.artificer.EssenceType;

import java.util.EnumSet;
import java.util.Set;

/** Networking for the Potion Mixer (C2S brew request). */
public final class MixerNet {
    private MixerNet() {}

    /** C2S: client requests a brew using a chosen set of essences (size must be 3). */
    public static final Identifier MIXER_BREW = new Identifier("odd", "mixer_brew");

    /** Call this once during common init (e.g., in ArtificerMixerRegistry.registerAll()). */
    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(MIXER_BREW, (server, player, handler, buf, responseSender) -> {
            final BlockPos pos = buf.readBlockPos();
            final int count = buf.readVarInt();
            final Set<EssenceType> picked = EnumSet.noneOf(EssenceType.class);
            for (int i = 0; i < count; i++) {
                picked.add(buf.readEnumConstant(EssenceType.class));
            }

            server.execute(() -> {
                if (player.getWorld().getBlockEntity(pos) instanceof PotionMixerBlockEntity be) {
                    be.tryBrew(picked);
                }
            });
        });
    }

    /** Client-side send from the screen. */
    public static void sendBrew(BlockPos pos, Set<EssenceType> picked) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeBlockPos(pos);
        buf.writeVarInt(picked.size());
        for (EssenceType t : picked) buf.writeEnumConstant(t);
        ClientPlayNetworking.send(MIXER_BREW, buf);
    }
}
