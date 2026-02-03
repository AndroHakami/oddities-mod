package net.seep.odd.abilities.chef.net;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import net.seep.odd.Oddities;
import net.seep.odd.block.supercooker.SuperCookerBlockEntity;

public final class ChefNet {
    private ChefNet() {}

    public static final Identifier S2C_COOKER_ANIM =
            new Identifier(Oddities.MOD_ID, "s2c_super_cooker_anim");

    public static void initClient() {
        ClientPlayNetworking.registerGlobalReceiver(S2C_COOKER_ANIM, (client, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            String anim = buf.readString(32);

            client.execute(() -> {
                if (MinecraftClient.getInstance().world == null) return;
                if (MinecraftClient.getInstance().world.getBlockEntity(pos) instanceof SuperCookerBlockEntity be) {
                    if ("stir".equals(anim)) {
                        be.triggerAnim("main", "stir");
                    }
                }
            });
        });
    }

    public static void s2cCookerAnim(ServerWorld sw, BlockPos pos, String anim) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        buf.writeString(anim);

        for (var p : PlayerLookup.tracking(sw, pos)) {
            ServerPlayNetworking.send(p, S2C_COOKER_ANIM, buf);
        }
    }
}
