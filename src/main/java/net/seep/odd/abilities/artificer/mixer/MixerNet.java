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

public final class MixerNet {
    private MixerNet() {}
    public static final Identifier MIXER_BREW = new Identifier("odd","mixer_brew");

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(MIXER_BREW, (server, player, handler, buf, rs) -> {
            BlockPos pos = buf.readBlockPos();
            int n = buf.readVarInt();
            Set<EssenceType> set = EnumSet.noneOf(EssenceType.class);
            for (int i=0;i<n;i++) set.add(buf.readEnumConstant(EssenceType.class));
            server.execute(() -> {
                if (player.getWorld().getBlockEntity(pos) instanceof PotionMixerBlockEntity be) {
                    be.craft(set);
                }
            });
        });
    }

    public static void sendBrew(BlockPos pos, Set<EssenceType> picked) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeBlockPos(pos);
        buf.writeVarInt(picked.size());
        for (EssenceType t : picked) buf.writeEnumConstant(t);
        ClientPlayNetworking.send(MIXER_BREW, buf);
    }
}
