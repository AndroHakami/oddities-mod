package net.seep.odd.abilities.zerosuit;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import net.seep.odd.Oddities;
import net.seep.odd.abilities.PowerAPI;
import net.seep.odd.abilities.power.Powers;
import net.seep.odd.abilities.power.ZeroSuitPower;
import net.seep.odd.abilities.zerosuit.client.ZeroSuitCpmBridge;
import net.seep.odd.entity.zerosuit.client.AnnihilationFx;

public final class ZeroSuitNet {
    private ZeroSuitNet() {}

    public static final Identifier S2C_HUD  = new Identifier(Oddities.MOD_ID, "zero_suit_hud");
    public static final Identifier S2C_ANIM = new Identifier(Oddities.MOD_ID, "zero_suit_anim");
    public static final Identifier C2S_FIRE = new Identifier(Oddities.MOD_ID, "zero_suit_fire");

    public static final Identifier S2C_BLAST_FIRE_SHAKE = new Identifier(Oddities.MOD_ID, "zero_blast_fire_shake");
    public static final Identifier S2C_ANNIHILATION_FX  = new Identifier(Oddities.MOD_ID, "annihilation_fx");

    // Jetpack
    public static final Identifier C2S_JETPACK_THRUST = new Identifier(Oddities.MOD_ID, "zero_jetpack_thrust");
    public static final Identifier S2C_JETPACK_HUD    = new Identifier(Oddities.MOD_ID, "zero_jetpack_hud");

    private static boolean INIT_COMMON = false;
    private static boolean INIT_CLIENT = false;

    public static void initCommon() {
        if (INIT_COMMON) return;
        INIT_COMMON = true;

        ServerTickEvents.END_WORLD_TICK.register((ServerWorld world) -> {
            for (ServerPlayerEntity p : world.getPlayers()) {
                var pow = Powers.get(PowerAPI.get(p));
                if (pow instanceof ZeroSuitPower) ZeroSuitPower.serverTick(p);
            }
        });


        ServerPlayNetworking.registerGlobalReceiver(C2S_FIRE, (server, player, handler, buf, responseSender) ->
                server.execute(() -> ZeroSuitPower.onClientRequestedFire(player)));

        ServerPlayNetworking.registerGlobalReceiver(C2S_JETPACK_THRUST, (server, player, handler, buf, responseSender) -> {
            final float dx = buf.readFloat();
            final float dy = buf.readFloat();
            final float dz = buf.readFloat();
            server.execute(() -> ZeroSuitPower.onClientJetpackThrust(player, dx, dy, dz));
        });

        ServerPlayConnectionEvents.DISCONNECT.register((ServerPlayNetworkHandler handler, MinecraftServer server) -> {});
    }

    public static void sendHud(ServerPlayerEntity to, boolean active, int charge, int max) {
        PacketByteBuf out = PacketByteBufs.create();
        out.writeBoolean(active);
        out.writeInt(charge);
        out.writeInt(max);
        ServerPlayNetworking.send(to, S2C_HUD, out);
    }

    public static void sendBlastFireShake(ServerPlayerEntity to, float ratio) {
        PacketByteBuf out = PacketByteBufs.create();
        out.writeFloat(ratio);
        ServerPlayNetworking.send(to, S2C_BLAST_FIRE_SHAKE, out);
    }

    public static void sendAnnihilationFx(ServerPlayerEntity to, int durationTicks) {
        PacketByteBuf out = PacketByteBufs.create();
        out.writeVarInt(durationTicks);
        ServerPlayNetworking.send(to, S2C_ANNIHILATION_FX, out);
    }

    public static void sendJetpackHud(ServerPlayerEntity to, boolean enabled, int fuel, int max, boolean thrusting) {
        PacketByteBuf out = PacketByteBufs.create();
        out.writeBoolean(enabled);
        out.writeInt(fuel);
        out.writeInt(max);
        out.writeBoolean(thrusting);
        ServerPlayNetworking.send(to, S2C_JETPACK_HUD, out);
    }

    /** Kept: UUID + key (client MUST read UUID first). */
    public static void broadcastAnim(ServerPlayerEntity src, String key) {
        PacketByteBuf out = PacketByteBufs.create();
        out.writeUuid(src.getUuid());
        out.writeString(key, 32);
        for (ServerPlayerEntity p : PlayerLookup.tracking(src)) {
            ServerPlayNetworking.send(p, S2C_ANIM, out);
        }
        ServerPlayNetworking.send(src, S2C_ANIM, out);
    }

    /* ============================ CLIENT ============================ */
    @Environment(EnvType.CLIENT)
    public static void initClient() {
        if (INIT_CLIENT) return;
        INIT_CLIENT = true;

        AnnihilationFx.init();

        // jetpack overlay + HUD + loop sound
        net.seep.odd.abilities.zerosuit.client.ZeroJetpackFx.init();
        net.seep.odd.abilities.zerosuit.client.ZeroJetpackHud.init();

        // blast overlay
        net.seep.odd.abilities.power.ZeroSuitPower.ClientHud.init();

        // blast charge HUD updates
        ClientPlayNetworking.registerGlobalReceiver(S2C_HUD, (client, handler, buf, sender) -> {
            final boolean active = buf.readBoolean();
            final int charge = buf.readInt();
            final int max = buf.readInt();
            client.execute(() -> net.seep.odd.abilities.power.ZeroSuitPower.ClientHud.onHud(active, charge, max));
        });

        // blast fire shake
        ClientPlayNetworking.registerGlobalReceiver(S2C_BLAST_FIRE_SHAKE, (client, handler, buf, sender) -> {
            final float ratio = buf.readFloat();
            client.execute(() -> net.seep.odd.abilities.zerosuit.client.ZeroBlastChargeFx.kickFireShake(ratio));
        });

        // annihilation
        ClientPlayNetworking.registerGlobalReceiver(S2C_ANNIHILATION_FX, (client, handler, buf, sender) -> {
            final int dur = buf.readVarInt();
            client.execute(() -> AnnihilationFx.trigger(dur));
        });

        // ✅ CPM anims packet layout is (UUID + String)
        ClientPlayNetworking.registerGlobalReceiver(S2C_ANIM, (client, handler, buf, sender) -> {
            final java.util.UUID who = buf.readUuid(); // read (layout correctness)
            final String key = buf.readString(32);
            client.execute(() -> {
                switch (key) {
                    case "stance_on"   -> ZeroSuitCpmBridge.playStance();
                    case "stance_off"  -> ZeroSuitCpmBridge.stopStance();
                    case "charge_on"   -> ZeroSuitCpmBridge.playBlastCharge();
                    case "charge_off"  -> ZeroSuitCpmBridge.stopBlastCharge();
                    case "force_push"  -> ZeroSuitCpmBridge.playForcePush();
                    case "force_pull"  -> ZeroSuitCpmBridge.playForcePull();
                    case "blast_fire"  -> ZeroSuitCpmBridge.playBlastFire();
                    default -> {}
                }
            });
        });

        // jetpack HUD sync (this also drives jetpack loop sound inside ZeroJetpackHud)
        ClientPlayNetworking.registerGlobalReceiver(S2C_JETPACK_HUD, (client, handler, buf, sender) -> {
            final boolean enabled = buf.readBoolean();
            final int fuel = buf.readInt();
            final int max = buf.readInt();
            final boolean thrusting = buf.readBoolean();
            client.execute(() -> net.seep.odd.abilities.zerosuit.client.ZeroJetpackHud.onHud(enabled, fuel, max, thrusting));
        });

        // ✅ RESET on BOTH disconnect + join (Splash-style)
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> hardResetClientState());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> hardResetClientState());

        // input: fire blast + jetpack thrust
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null || client.player == null) return;

            // blast firing while charging
            if (net.seep.odd.abilities.power.ZeroSuitPower.ClientHud.isCharging()) {
                boolean edge = net.seep.odd.abilities.power.ZeroSuitPower.ClientHud.consumeAttackEdge();
                if (edge) ClientPlayNetworking.send(C2S_FIRE, PacketByteBufs.create());
            }

            // jetpack thrust while holding space (when enabled per S2C)
            if (net.seep.odd.abilities.zerosuit.client.ZeroJetpackHud.isEnabled()) {
                if (client.options.jumpKey.isPressed()) {
                    Vec3d dir = computeMoveDirection(client);
                    PacketByteBuf out = PacketByteBufs.create();
                    out.writeFloat((float) dir.x);
                    out.writeFloat((float) dir.y);
                    out.writeFloat((float) dir.z);
                    ClientPlayNetworking.send(C2S_JETPACK_THRUST, out);
                }
            }
        });
    }

    @Environment(EnvType.CLIENT)
    private static void hardResetClientState() {
        net.seep.odd.abilities.power.ZeroSuitPower.ClientHud.onHud(false, 0, 1);
        net.seep.odd.abilities.zerosuit.client.ZeroBlastChargeFx.stop();

        // This call MUST happen so jetpack loop sound + overlay hard-stop.
        net.seep.odd.abilities.zerosuit.client.ZeroJetpackHud.onHud(false, 0, 100, false);

        AnnihilationFx.stop();
    }

    @Environment(EnvType.CLIENT)
    private static Vec3d computeMoveDirection(MinecraftClient mc) {
        if (mc == null || mc.player == null) return new Vec3d(0, 1, 0);

        float fwdIn = 0f;
        float strIn = 0f;
        try {
            fwdIn = mc.player.input.movementForward;
            strIn = mc.player.input.movementSideways;
        } catch (Throwable ignored) {}

        float yaw = mc.player.getYaw();
        float yawRad = yaw * ((float)Math.PI / 180f);

        Vec3d fwd = new Vec3d(-MathHelper.sin(yawRad), 0, MathHelper.cos(yawRad));
        Vec3d right = new Vec3d(MathHelper.cos(yawRad), 0, MathHelper.sin(yawRad));

        Vec3d dir;
        if (Math.abs(fwdIn) > 1e-3 || Math.abs(strIn) > 1e-3) {
            dir = fwd.multiply(fwdIn).add(right.multiply(strIn));
        } else {
            dir = new Vec3d(0, 1, 0);
        }

        // bias upward (jetpack feel)
        dir = dir.add(0, 0.85, 0);

        if (dir.lengthSquared() < 1e-6) return new Vec3d(0, 1, 0);
        return dir.normalize();
    }
}