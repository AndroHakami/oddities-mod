package net.seep.odd.client.device.guild;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.seep.odd.device.guild.GuildNetworking;
import net.seep.odd.device.guild.GuildTeam;

@Environment(EnvType.CLIENT)
public final class GuildClientCache {
    private GuildClientCache() {}

    private static GuildTeam team;
    private static boolean inited = false;

    public static void initClient() {
        if (inited) return;
        inited = true;

        ClientPlayNetworking.registerGlobalReceiver(GuildNetworking.S2C_SYNC, (client, handler, buf, sender) -> {
            GuildTeam incoming = null;
            if (buf.readBoolean()) {
                incoming = GuildTeam.read(buf);
            }

            GuildTeam finalIncoming = incoming;
            client.execute(() -> team = finalIncoming);
        });
    }

    public static GuildTeam team() {
        return team;
    }

    public static boolean hasTeam() {
        return team != null;
    }
}
