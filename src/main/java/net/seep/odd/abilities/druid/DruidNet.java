package net.seep.odd.abilities.druid;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

import net.seep.odd.Oddities;
import net.seep.odd.abilities.power.DruidPower;

public final class DruidNet {
    private DruidNet() {}

    public static final Identifier S2C_OPEN_WHEEL  = new Identifier(Oddities.MOD_ID, "druid_open_wheel");
    public static final Identifier C2S_SELECT_FORM = new Identifier(Oddities.MOD_ID, "druid_select_form");

    // NEW: tell client which form they are in (for local heart swap)
    public static final Identifier S2C_FORM = new Identifier(Oddities.MOD_ID, "druid_form");

    public static void init() {
        ServerPlayNetworking.registerGlobalReceiver(C2S_SELECT_FORM, (server, player, handler, buf, responseSender) -> {
            final String key = buf.readString(64);

            server.execute(() -> {
                ServerWorld sw = player.getServerWorld();
                DruidData data = DruidData.get(sw);

                long now = sw.getTime();
                if (data.getDeathCooldownUntil(player.getUuid()) > now) return;

                DruidForm chosen = DruidForm.byKey(key);
                DruidPower.serverSetForm(player, chosen);
            });
        });

        // Send current form on join (prevents desync)
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity p = handler.getPlayer();
            server.execute(() -> {
                ServerWorld sw = p.getServerWorld();
                DruidForm f = DruidData.get(sw).getCurrentForm(p.getUuid());
                s2cForm(p, f);
            });
        });
    }

    public static void s2cOpenWheel(ServerPlayerEntity p) {
        ServerWorld sw = p.getServerWorld();
        DruidData data = DruidData.get(sw);

        PacketByteBuf buf = PacketByteBufs.create();

        long now = sw.getTime();
        long cd = Math.max(0L, data.getDeathCooldownUntil(p.getUuid()) - now);
        buf.writeVarLong(cd);

        DruidForm[] forms = DruidForm.values();
        buf.writeVarInt(forms.length);

        for (DruidForm f : forms) {
            buf.writeString(f.key());
            buf.writeString(f.displayName().getString());
            buf.writeString(f.iconTexture().toString());
        }

        ServerPlayNetworking.send(p, S2C_OPEN_WHEEL, buf);
    }

    public static void s2cForm(ServerPlayerEntity p, DruidForm form) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(form == null ? "human" : form.key());
        ServerPlayNetworking.send(p, S2C_FORM, buf);
    }
}
