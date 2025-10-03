package net.seep.odd.abilities.ghostlings;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.seep.odd.abilities.ghostlings.entity.GhostlingEntity;
import net.seep.odd.abilities.ghostlings.screen.client.GhostDashboardScreen;

import java.util.List;
import java.util.UUID;

public final class GhostPackets {
    private GhostPackets() {}

    private static final String MODID = "odd";

    public static final Identifier C2S_OPEN_MANAGE       = new Identifier(MODID, "c2s_open_manage");
    public static final Identifier C2S_OPEN_DASHBOARD    = new Identifier(MODID, "c2s_open_dashboard");
    public static final Identifier C2S_SET_COURIER_POS   = new Identifier(MODID, "c2s_set_courier_pos");
    public static final Identifier C2S_SET_WORK_ORIGIN   = new Identifier(MODID, "c2s_set_work_origin");
    public static final Identifier C2S_TOGGLE_STAY_RANGE = new Identifier(MODID, "c2s_toggle_stay_range");

    public static final Identifier S2C_OPEN_DASHBOARD    = new Identifier(MODID, "s2c_open_dashboard");

    public static void registerC2S() {
        ServerPlayNetworking.registerGlobalReceiver(C2S_OPEN_MANAGE, (server, player, handler, buf, response) -> {
            int entityId = buf.readVarInt();
            server.execute(() -> {
                var e = player.getWorld().getEntityById(entityId);
                if (e instanceof GhostlingEntity g && g.isOwner(player.getUuid())) {
                    player.openHandledScreen(g.getManageFactory());
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(C2S_OPEN_DASHBOARD, (server, player, handler, buf, response) ->
                server.execute(() -> openDashboardServer(player)));

        ServerPlayNetworking.registerGlobalReceiver(C2S_SET_COURIER_POS, (server, player, handler, buf, response) -> {
            int id = buf.readVarInt();
            var pos = buf.readBlockPos();
            server.execute(() -> {
                var e = player.getWorld().getEntityById(id);
                if (e instanceof GhostlingEntity g && g.isOwner(player.getUuid())) {
                    g.setCourierTarget(pos);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(C2S_SET_WORK_ORIGIN, (server, player, handler, buf, response) -> {
            int id = buf.readVarInt();
            var pos = buf.readBlockPos();
            server.execute(() -> {
                var e = player.getWorld().getEntityById(id);
                if (e instanceof GhostlingEntity g && g.isOwner(player.getUuid())) {
                    g.setWorkOrigin(pos);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(C2S_TOGGLE_STAY_RANGE, (server, player, handler, buf, response) -> {
            int id = buf.readVarInt();
            server.execute(() -> {
                var e = player.getWorld().getEntityById(id);
                if (e instanceof GhostlingEntity g && g.isOwner(player.getUuid())) {
                    g.toggleStayWithinRange();
                }
            });
        });
    }

    public static void openManageServer(ServerPlayerEntity player, GhostlingEntity g) {
        player.openHandledScreen(g.getManageFactory());
    }

    public static void openDashboardServer(ServerPlayerEntity player) {
        List<GhostlingEntity> mine = player.getWorld().getEntitiesByClass(
                GhostlingEntity.class, player.getBoundingBox().expand(256.0),
                g -> g.isOwner(player.getUuid())
        );

        PacketByteBuf out = PacketByteBufs.create();
        out.writeVarInt(mine.size());
        for (GhostlingEntity g : mine) {
            out.writeUuid(g.getUuid());
            out.writeVarInt(g.getId());
            out.writeString(g.getName().getString());
            out.writeEnumConstant(g.getJob());
            out.writeBlockPos(g.getBlockPos());
            out.writeFloat((float) g.getHealth());
            out.writeFloat(g.getMaxHealth());
            out.writeBoolean(g.isWorking());
        }
        ServerPlayNetworking.send(player, S2C_OPEN_DASHBOARD, out);
    }

    // call from client init
    public static void registerS2CClient() {
        ClientPlayNetworking.registerGlobalReceiver(S2C_OPEN_DASHBOARD, (client, handler, buf, response) -> {
            int n = buf.readVarInt();
            GhostDashboardScreen.GhostSummary[] arr = new GhostDashboardScreen.GhostSummary[n];
            for (int i=0;i<n;i++) {
                UUID uuid = buf.readUuid();
                int entityId = buf.readVarInt();
                String name = buf.readString();
                GhostlingEntity.Job job = buf.readEnumConstant(GhostlingEntity.Job.class);
                var pos = buf.readBlockPos();
                float hp = buf.readFloat();
                float max = buf.readFloat();
                boolean working = buf.readBoolean();
                arr[i] = new GhostDashboardScreen.GhostSummary(uuid, entityId, name, job, pos, hp, max, working);
            }
            client.execute(() -> client.setScreen(new GhostDashboardScreen(arr)));
        });
    }
}
