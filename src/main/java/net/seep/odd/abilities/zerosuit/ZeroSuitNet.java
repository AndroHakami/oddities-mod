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
import net.minecraft.client.option.Perspective;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.Entity.RemovalReason;
import net.minecraft.entity.decoration.ArmorStandEntity;
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

    // === Missile control ===
    public static final Identifier S2C_MISSILE_BEGIN    = new Identifier(Oddities.MOD_ID, "zero_suit_missile_begin");
    public static final Identifier S2C_MISSILE_END      = new Identifier(Oddities.MOD_ID, "zero_suit_missile_end");
    public static final Identifier C2S_MISSILE_DETONATE = new Identifier(Oddities.MOD_ID, "zero_suit_missile_detonate");
    public static final Identifier C2S_MISSILE_STEER    = new Identifier(Oddities.MOD_ID, "zero_suit_missile_steer");
    public static final Identifier S2C_MISSILE_STEER    = new Identifier(Oddities.MOD_ID, "zero_suit_missile_steer_s2c");

    // === Annihilation FX ===
    public static final Identifier S2C_ANNIHILATION_FX   = new Identifier(Oddities.MOD_ID, "annihilation_fx");

    private static boolean INIT_COMMON = false;
    private static boolean INIT_CLIENT = false;

    /* ============================ COMMON ============================ */
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

        ServerPlayNetworking.registerGlobalReceiver(C2S_MISSILE_DETONATE, (server, player, handler, buf, responseSender) ->
                server.execute(() -> ZeroSuitPower.onClientRequestedMissileDetonate(player)));

        ServerPlayNetworking.registerGlobalReceiver(C2S_MISSILE_STEER, (server, player, handler, buf, responseSender) -> {
            final int entityId = buf.readVarInt();
            final float yaw = buf.readFloat();
            final float pitch = buf.readFloat();
            final float roll = buf.readFloat();
            server.execute(() -> ZeroSuitPower.onClientRequestedMissileSteer(player, entityId, yaw, pitch, roll));
        });

        ServerPlayConnectionEvents.DISCONNECT.register((ServerPlayNetworkHandler handler, MinecraftServer server) -> {
            // optional cleanup
        });
    }

    public static void sendHud(ServerPlayerEntity to, boolean active, int charge, int max) {
        PacketByteBuf out = PacketByteBufs.create();
        out.writeBoolean(active);
        out.writeInt(charge);
        out.writeInt(max);
        ServerPlayNetworking.send(to, S2C_HUD, out);
    }

    public static void sendMissileBegin(ServerPlayerEntity to, int missileEntityId, float yaw, float pitch) {
        PacketByteBuf out = PacketByteBufs.create();
        out.writeVarInt(missileEntityId);
        out.writeFloat(yaw);
        out.writeFloat(pitch);
        ServerPlayNetworking.send(to, S2C_MISSILE_BEGIN, out);
    }

    public static void sendMissileEnd(ServerPlayerEntity to) {
        ServerPlayNetworking.send(to, S2C_MISSILE_END, PacketByteBufs.empty());
    }

    public static void sendAnnihilationFx(ServerPlayerEntity to, int durationTicks) {
        PacketByteBuf out = PacketByteBufs.create();
        out.writeVarInt(durationTicks);
        ServerPlayNetworking.send(to, S2C_ANNIHILATION_FX, out);
    }

    public static void broadcastAnim(ServerPlayerEntity src, String key) {
        PacketByteBuf out = PacketByteBufs.create();
        out.writeUuid(src.getUuid());
        out.writeString(key, 32);
        for (ServerPlayerEntity p : PlayerLookup.tracking(src)) {
            ServerPlayNetworking.send(p, S2C_ANIM, out);
        }
        ServerPlayNetworking.send(src, S2C_ANIM, out);
    }

    public static void broadcastMissileSteer(Entity missile, float yaw, float pitch, float roll) {
        if (missile == null || missile.getWorld() == null || missile.getWorld().isClient) return;

        PacketByteBuf out = PacketByteBufs.create();
        out.writeVarInt(missile.getId());
        out.writeFloat(yaw);
        out.writeFloat(pitch);
        out.writeFloat(roll);

        for (ServerPlayerEntity p : PlayerLookup.tracking(missile)) {
            ServerPlayNetworking.send(p, S2C_MISSILE_STEER, out);
        }
    }

    /* ============================ CLIENT ============================ */
    @Environment(EnvType.CLIENT)
    public static void initClient() {
        if (INIT_CLIENT) return;
        INIT_CLIENT = true;

        // IMPORTANT: this is your "shader loader" init point
        // (AnnihilationFx should register Satin callbacks internally)
        AnnihilationFx.init();

        // HUD
        ClientPlayNetworking.registerGlobalReceiver(S2C_HUD, (client, handler, buf, sender) -> {
            boolean active = buf.readBoolean();
            int charge = buf.readInt();
            int max = buf.readInt();
            client.execute(() -> net.seep.odd.abilities.power.ZeroSuitPower.ClientHud.onHud(active, charge, max));
        });

        // Annihilation trigger
        ClientPlayNetworking.registerGlobalReceiver(S2C_ANNIHILATION_FX, (client, handler, buf, sender) -> {
            final int dur = buf.readVarInt();
            client.execute(() -> AnnihilationFx.trigger(dur));
        });

        // Disconnect cleanup
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            net.seep.odd.abilities.power.ZeroSuitPower.ClientHud.onHud(false, 0, 1);
            ClientMissileControl.forceEnd();
            AnnihilationFx.stop();
        });

        // CPM anims
        ClientPlayNetworking.registerGlobalReceiver(S2C_ANIM, (client, handler, buf, sender) -> {
            final java.util.UUID who = buf.readUuid();
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

        // Missile camera begin/end
        ClientPlayNetworking.registerGlobalReceiver(S2C_MISSILE_BEGIN, (client, handler, buf, sender) -> {
            final int id = buf.readVarInt();
            final float yaw = buf.readFloat();
            final float pitch = buf.readFloat();
            client.execute(() -> ClientMissileControl.begin(id, yaw, pitch));
        });

        ClientPlayNetworking.registerGlobalReceiver(S2C_MISSILE_END, (client, handler, buf, sender) ->
                client.execute(ClientMissileControl::end));

        // Optional: server forwarded steering (helps non-owner predict too)
        ClientPlayNetworking.registerGlobalReceiver(S2C_MISSILE_STEER, (client, handler, buf, sender) -> {
            final int entityId = buf.readVarInt();
            final float yaw = buf.readFloat();
            final float pitch = buf.readFloat();
            final float roll = buf.readFloat();
            client.execute(() -> {
                if (client.world == null) return;
                Entity e = client.world.getEntityById(entityId);
                if (e instanceof net.seep.odd.entity.zerosuit.ZeroSuitMissileEntity zm) {
                    zm.clientSetDesiredRotation(yaw, pitch, roll);
                    zm.clientSetVisualRoll(roll);
                }
            });
        });

        // INPUT: left click edge + missile steering, plus "death/respawn camera reset"
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null) return;

            // hard safety: if player died / swapped player entity while controlling missile, reset camera
            if (ClientMissileControl.active()) {
                if (client.player == null || !client.player.isAlive()) {
                    ClientMissileControl.forceEnd();
                    return;
                }

                boolean edge = net.seep.odd.abilities.power.ZeroSuitPower.ClientHud.consumeAttackEdge();
                if (edge) ClientPlayNetworking.send(C2S_MISSILE_DETONATE, PacketByteBufs.create());
                ClientMissileControl.tickControlAndSend();
                return;
            }

            if (net.seep.odd.abilities.power.ZeroSuitPower.ClientHud.isCharging()) {
                boolean edge = net.seep.odd.abilities.power.ZeroSuitPower.ClientHud.consumeAttackEdge();
                if (edge) ClientPlayNetworking.send(C2S_FIRE, PacketByteBufs.create());
            }
        });
    }

    /**
     * Missile POV + raw mouse steering:
     * - You "become the missile" camera-wise (FIRST_PERSON on a nose camera rig)
     * - Your real body stays put (server freezes you)
     * - Mouse controls steer targets even though camera isn't the player
     */
    @Environment(EnvType.CLIENT)
    private static final class ClientMissileControl {
        private static int missileId = -1;

        private static Entity prevCamera = null;
        private static Perspective prevPerspective = null;
        private static boolean prevSmoothCam = false;

        private static ArmorStandEntity camRig = null;
        private static int camRigId = 0;

        private static double rigX, rigY, rigZ;
        private static boolean rigInit = false;

        // Control targets (what we send to the server)
        private static float ctrlYaw = 0f;
        private static float ctrlPitch = 0f;
        private static float ctrlRoll = 0f;

        // Raw mouse tracking
        private static double lastMouseX = 0.0;
        private static double lastMouseY = 0.0;
        private static boolean mouseInit = false;

        // Restore player look when exiting control
        private static float savedPlayerYaw = 0f;
        private static float savedPlayerPitch = 0f;

        private static final float RIG_LERP = 0.35f;
        private static final float CAM_ROT_LERP = 0.35f;

        private static final float ROLL_STEP = 4.5f;
        private static final float ROLL_RETURN = 0.82f;

        // Nose camera offsets (first-person)
        private static final double CAM_FWD = 0.25;
        private static final double CAM_UP = 0.10;

        static boolean active() { return missileId != -1; }

        static void begin(int entityId, float initialYaw, float initialPitch) {
            MinecraftClient mc = MinecraftClient.getInstance();
            ClientWorld cw = mc.world;
            if (cw == null || mc.player == null) return;

            missileId = entityId;
            rigInit = false;

            savedPlayerYaw = mc.player.getYaw();
            savedPlayerPitch = mc.player.getPitch();

            ctrlYaw = MathHelper.wrapDegrees(initialYaw);
            ctrlPitch = MathHelper.clamp(initialPitch, -80f, 80f);
            ctrlRoll = 0f;

            lastMouseX = mc.mouse.getX();
            lastMouseY = mc.mouse.getY();
            mouseInit = true;

            prevCamera = mc.getCameraEntity();
            prevPerspective = mc.options.getPerspective();
            prevSmoothCam = mc.options.smoothCameraEnabled;

            mc.options.setPerspective(Perspective.FIRST_PERSON);
            mc.options.smoothCameraEnabled = true;

            Entity missile = cw.getEntityById(entityId);
            if (missile != null) {
                createRig(cw, missile);
                setRigRotation(camRig, ctrlYaw, ctrlPitch);
                mc.setCameraEntity(camRig);

                if (missile instanceof net.seep.odd.entity.zerosuit.ZeroSuitMissileEntity zm) {
                    zm.clientSetDesiredRotation(ctrlYaw, ctrlPitch, ctrlRoll);
                    zm.clientSetVisualRoll(ctrlRoll);
                }
            }
        }

        private static void setRigRotation(ArmorStandEntity rig, float yaw, float pitch) {
            if (rig == null) return;
            rig.refreshPositionAndAngles(rig.getX(), rig.getY(), rig.getZ(), yaw, pitch);
            rig.setHeadYaw(yaw);
            rig.setBodyYaw(yaw);
        }

        private static void createRig(ClientWorld cw, Entity missile) {
            if (camRig != null) {
                try { cw.removeEntity(camRigId, RemovalReason.DISCARDED); } catch (Throwable ignored) {}
                camRig = null;
            }

            camRigId = -2000000000 + (missileId & 0x0FFFFFFF);

            camRig = new ArmorStandEntity(cw, missile.getX(), missile.getY(), missile.getZ());
            camRig.setInvisible(true);
            camRig.setNoGravity(true);
            camRig.setSilent(true);
            camRig.noClip = true;

            cw.addEntity(camRigId, camRig);

            rigX = missile.getX();
            rigY = missile.getY();
            rigZ = missile.getZ();
            rigInit = true;
        }

        static void end() {
            MinecraftClient mc = MinecraftClient.getInstance();
            ClientWorld cw = mc.world;
            if (missileId == -1) return;

            if (prevCamera != null) mc.setCameraEntity(prevCamera);
            else if (mc.player != null) mc.setCameraEntity(mc.player);

            if (prevPerspective != null) mc.options.setPerspective(prevPerspective);
            mc.options.smoothCameraEnabled = prevSmoothCam;

            if (mc.player != null) {
                mc.player.setYaw(savedPlayerYaw);
                mc.player.setPitch(savedPlayerPitch);
            }

            if (cw != null && camRig != null) {
                try { cw.removeEntity(camRigId, RemovalReason.DISCARDED); } catch (Throwable ignored) {}
            }

            camRig = null;
            prevCamera = null;
            prevPerspective = null;

            missileId = -1;
            rigInit = false;
            ctrlRoll = 0f;
            mouseInit = false;
        }

        static void forceEnd() {
            MinecraftClient mc = MinecraftClient.getInstance();
            ClientWorld cw = mc != null ? mc.world : null;

            if (mc != null) {
                mc.options.smoothCameraEnabled = prevSmoothCam;
                if (prevPerspective != null) mc.options.setPerspective(prevPerspective);

                if (mc.player != null) {
                    mc.player.setYaw(savedPlayerYaw);
                    mc.player.setPitch(savedPlayerPitch);
                }

                if (prevCamera != null) mc.setCameraEntity(prevCamera);
                else if (mc.player != null) mc.setCameraEntity(mc.player);
            }

            if (cw != null && camRig != null) {
                try { cw.removeEntity(camRigId, RemovalReason.DISCARDED); } catch (Throwable ignored) {}
            }

            missileId = -1;
            camRig = null;
            prevCamera = null;
            prevPerspective = null;
            rigInit = false;
            ctrlRoll = 0f;
            mouseInit = false;
        }

        private static double mouseFactor(MinecraftClient mc) {
            double sens = mc.options.getMouseSensitivity().getValue();
            double f = sens * 0.6 + 0.2;
            return f * f * f * 8.0;
        }

        static void tickControlAndSend() {
            MinecraftClient mc = MinecraftClient.getInstance();
            ClientWorld cw = mc.world;
            if (cw == null || mc.player == null) return;
            if (missileId == -1) return;

            Entity missile = cw.getEntityById(missileId);

            // IMPORTANT: don't end() just because entity isn't present yet.
            if (missile == null) return;

            double mx = mc.mouse.getX();
            double my = mc.mouse.getY();

            if (!mouseInit) {
                lastMouseX = mx;
                lastMouseY = my;
                mouseInit = true;
            }

            double dx = mx - lastMouseX;
            double dy = my - lastMouseY;

            lastMouseX = mx;
            lastMouseY = my;

            double g = mouseFactor(mc);

            ctrlYaw += (float)(dx * g);
            ctrlPitch += (float)(dy * g);

            try {
                if (mc.options.getInvertYMouse().getValue()) {
                    ctrlPitch -= (float)(2.0 * dy * g);
                }
            } catch (Throwable ignored) {}

            ctrlPitch = MathHelper.clamp(ctrlPitch, -80f, 80f);

            boolean w = mc.options.forwardKey.isPressed();
            boolean s = mc.options.backKey.isPressed();
            if (w) ctrlPitch = MathHelper.clamp(ctrlPitch - 2.5f, -80f, 80f);
            if (s) ctrlPitch = MathHelper.clamp(ctrlPitch + 2.5f, -80f, 80f);

            boolean a = mc.options.leftKey.isPressed();
            boolean d = mc.options.rightKey.isPressed();
            if (a) ctrlRoll -= ROLL_STEP;
            if (d) ctrlRoll += ROLL_STEP;
            if (!a && !d) ctrlRoll *= ROLL_RETURN;
            ctrlRoll = MathHelper.clamp(ctrlRoll, -55f, 55f);

            if (missile instanceof net.seep.odd.entity.zerosuit.ZeroSuitMissileEntity zm) {
                zm.clientSetDesiredRotation(ctrlYaw, ctrlPitch, ctrlRoll);
                zm.clientSetVisualRoll(ctrlRoll);
            }

            if (camRig == null || camRig.isRemoved()) {
                createRig(cw, missile);
                mc.setCameraEntity(camRig);
            }

            if (!rigInit) {
                rigX = missile.getX();
                rigY = missile.getY();
                rigZ = missile.getZ();
                rigInit = true;
            } else {
                rigX = MathHelper.lerp(RIG_LERP, rigX, missile.getX());
                rigY = MathHelper.lerp(RIG_LERP, rigY, missile.getY());
                rigZ = MathHelper.lerp(RIG_LERP, rigZ, missile.getZ());
            }

            Vec3d fwd = Vec3d.fromPolar(missile.getPitch(), missile.getYaw()).normalize();
            double cx = rigX + fwd.x * CAM_FWD;
            double cy = rigY + fwd.y * CAM_FWD + CAM_UP;
            double cz = rigZ + fwd.z * CAM_FWD;

            camRig.setPos(cx, cy, cz);

            float myaw = missile.getYaw();
            float mpitch = missile.getPitch();
            float ry = MathHelper.lerpAngleDegrees(CAM_ROT_LERP, camRig.getYaw(), myaw);
            float rp = MathHelper.lerp(CAM_ROT_LERP, camRig.getPitch(), mpitch);
            setRigRotation(camRig, ry, rp);

            PacketByteBuf out = PacketByteBufs.create();
            out.writeVarInt(missileId);
            out.writeFloat(ctrlYaw);
            out.writeFloat(ctrlPitch);
            out.writeFloat(ctrlRoll);
            ClientPlayNetworking.send(C2S_MISSILE_STEER, out);
        }
    }
}
