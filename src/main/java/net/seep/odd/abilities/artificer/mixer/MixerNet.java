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

/** C2S for the Potion Mixer only (lives under artificer.mixer). */
public final class MixerNet {
    private MixerNet() {}

    public static final Identifier MIXER_BREW = new Identifier("odd", "mixer_brew");

    /** Call this once during common init (server receivers). */
    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(MIXER_BREW, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            int n = buf.readVarInt();
            Set<EssenceType> picked = EnumSet.noneOf(EssenceType.class);
            for (int i = 0; i < n; i++) picked.add(buf.readEnumConstant(EssenceType.class));

            server.execute(() -> {
                if (player.getWorld().getBlockEntity(pos) instanceof PotionMixerBlockEntity be) {
                    be.craft(picked);
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
