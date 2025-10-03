package net.seep.odd.abilities.cosmic;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import net.seep.odd.abilities.cosmic.client.CosmicCpmBridge;
import net.seep.odd.abilities.power.CosmicPower;

public final class CosmicNet {
    private CosmicNet() {}

    private static final Identifier S2C_SLASH_PING   = new Identifier("odd", "cosmic_slash_ping");   // existing ping (optional)
    private static final Identifier S2C_RIFT         = new Identifier("odd", "cosmic_rift_line");     // existing rift (optional)

    // NEW: CPM stance + slash triggers
    private static final Identifier S2C_CPM_STANCE   = new Identifier("odd", "cosmic_cpm_stance");
    private static final Identifier S2C_CPM_SLASH    = new Identifier("odd", "cosmic_cpm_slash");

    public static void register() {
        // -------- client receivers --------
        ClientPlayNetworking.registerGlobalReceiver(S2C_CPM_STANCE, (client, handler, buf, sender) -> {
            boolean on = buf.readBoolean();
            client.execute(() -> {
                if (on) CosmicCpmBridge.stanceStart();
                else    CosmicCpmBridge.stanceStop();
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(S2C_CPM_SLASH, (client, handler, buf, sender) -> {
            int holdTicks = buf.readVarInt();
            client.execute(() -> CosmicCpmBridge.slash(holdTicks));
        });

        // existing receivers (keep if you already use them)
        ClientPlayNetworking.registerGlobalReceiver(S2C_SLASH_PING, (c,h,b,s) ->
                c.execute(net.seep.odd.abilities.power.CosmicPower.Client::pingSlash)
        );
        ClientPlayNetworking.registerGlobalReceiver(S2C_RIFT, (client, h, buf, s) -> {
            double ax = buf.readDouble(), ay = buf.readDouble(), az = buf.readDouble();
            double bx = buf.readDouble(), by = buf.readDouble(), bz = buf.readDouble();
            client.execute(() -> spawnClientRift(new Vec3d(ax, ay, az), new Vec3d(bx, by, bz)));
        });

        // -------- per-world server tick (keeps existing power tick) --------
        ServerTickEvents.END_WORLD_TICK.register((ServerWorld w) -> {
            for (ServerPlayerEntity sp : w.getPlayers()) {
                CosmicPower.serverTick(sp);
            }
        });
    }

    // -------- server send helpers (to the local player only) --------
    public static void sendCpmStance(ServerPlayerEntity to, boolean on) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(on);
        ServerPlayNetworking.send(to, S2C_CPM_STANCE, buf);
    }
    public static void sendCpmSlash(ServerPlayerEntity to, int holdTicks) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(Math.max(1, holdTicks));
        ServerPlayNetworking.send(to, S2C_CPM_SLASH, buf);
    }

    public static void sendSlashPing(ServerPlayerEntity to) {
        ServerPlayNetworking.send(to, S2C_SLASH_PING, PacketByteBufs.create());
    }

    public static void sendRift(ServerWorld world, Vec3d a, Vec3d b, int i) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeDouble(a.x); buf.writeDouble(a.y); buf.writeDouble(a.z);
        buf.writeDouble(b.x); buf.writeDouble(b.y); buf.writeDouble(b.z);
        for (ServerPlayerEntity sp : world.getPlayers()) {
            ServerPlayNetworking.send(sp, S2C_RIFT, buf);
        }
    }

    @Environment(EnvType.CLIENT)
    private static void spawnClientRift(Vec3d a, Vec3d b) {
        ClientWorld w = MinecraftClient.getInstance().world;
        if (w == null) return;
        int steps = Math.max(16, (int)a.distanceTo(b) * 4);
        Vec3d d = b.subtract(a);
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            Vec3d p = a.add(d.multiply(t));
            w.addParticle(ParticleTypes.REVERSE_PORTAL, p.x, p.y + 0.05, p.z, 0, 0, 0);
        }
    }
}
