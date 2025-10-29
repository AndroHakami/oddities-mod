package net.seep.odd.abilities.buddymorph;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;

import java.util.List;
import java.util.Optional;

public final class BuddymorphNet {
    private BuddymorphNet(){}

    public static final Identifier S2C_OPEN   = new Identifier(Oddities.MOD_ID, "buddymorph/open");
    public static final Identifier S2C_UPDATE = new Identifier(Oddities.MOD_ID, "buddymorph/update");
    public static final Identifier S2C_MELODY = new Identifier(Oddities.MOD_ID, "buddymorph/melody");
    public static final Identifier C2S_PICK   = new Identifier(Oddities.MOD_ID, "buddymorph/pick");

    /** Call once during common init (server available). */
    public static void init() {
        ServerPlayNetworking.registerGlobalReceiver(C2S_PICK, (server, player, handler, buf, response) -> {
            final boolean revert = buf.readBoolean();
            final String id = revert ? "" : buf.readString(256);
            server.execute(() -> {
                if (revert) {
                    net.seep.odd.abilities.power.BuddymorphPower.serverMorphTo(player, Optional.empty());
                } else {
                    net.seep.odd.abilities.power.BuddymorphPower.serverMorphTo(player, Optional.of(new Identifier(id)));
                }
            });
        });
    }

    public static void s2cOpenMenu(ServerPlayerEntity sp, List<Identifier> ids) {
        PacketByteBuf out = PacketByteBufs.create();
        out.writeVarInt(ids.size());
        for (var id : ids) out.writeString(id.toString());
        ServerPlayNetworking.send(sp, S2C_OPEN, out);
    }

    public static void s2cUpdateMenu(ServerPlayerEntity sp, List<Identifier> ids) {
        PacketByteBuf out = PacketByteBufs.create();
        out.writeVarInt(ids.size());
        for (var id : ids) out.writeString(id.toString());
        ServerPlayNetworking.send(sp, S2C_UPDATE, out);
    }

    public static void s2cMelody(ServerPlayerEntity sp, int ticks) {
        PacketByteBuf out = PacketByteBufs.create();
        out.writeVarInt(ticks);
        ServerPlayNetworking.send(sp, S2C_MELODY, out);
    }
}
