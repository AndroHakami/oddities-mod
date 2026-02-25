package net.seep.odd.abilities.artificer.mixer.brew;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import net.seep.odd.abilities.artificer.mixer.brew.client.BrambleFx;
import net.seep.odd.abilities.artificer.mixer.brew.client.BrambleTetherRenderer;

import java.util.UUID;

public final class RadiantBrambleEffect {
    private RadiantBrambleEffect() {}

    // S2C packets
    private static final Identifier S2C_STATE   = new Identifier("odd", "radiant_bramble_state");
    private static final Identifier S2C_TRIGGER = new Identifier("odd", "radiant_bramble_trigger");

    // Server runtime
    private static final Object2LongOpenHashMap<UUID> END_TICK = new Object2LongOpenHashMap<>();
    private static final Object2LongOpenHashMap<UUID> LAST_TRIGGER_TICK = new Object2LongOpenHashMap<>();
    private static boolean inited = false;

    // Tunables
    public static final int DEFAULT_DURATION_TICKS = 20 * 12; // 12s buff duration
    private static final int GLOW_TICKS = 20 * 5;
    private static final int FIRE_SECONDS = 5;
    private static final int TRIGGER_COOLDOWN_TICKS = 4;      // throttle per victim
    private static final int TETHER_LIFE_TICKS = 10;          // visuals per hit

    /** Call from common init if you want; start() also calls it. */
    public static void bootstrapCommon() {
        initCommon();
    }

    private static void initCommon() {
        if (inited) return;
        inited = true;

        // Tick to expire buff and notify client to stop visuals
        ServerTickEvents.END_SERVER_TICK.register(RadiantBrambleEffect::serverTick);

        // prevent “stuck forever” in dev/integrated server restarts
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> resetRuntime());
        ServerLifecycleEvents.SERVER_STARTING.register(server -> resetRuntime());
    }

    private static void resetRuntime() {
        END_TICK.clear();
        LAST_TRIGGER_TICK.clear();
    }

    /** Server authoritative active check (this is what the mixin uses). */
    public static boolean isActive(ServerPlayerEntity p) {
        if (p == null || p.getServer() == null) return false;
        long end = END_TICK.getOrDefault(p.getUuid(), Long.MIN_VALUE);
        if (end == Long.MIN_VALUE) return false;
        return p.getServer().getTicks() < end;
    }

    /** Apply the drinkable buff. */
    public static void start(ServerPlayerEntity player, int durationTicks) {
        if (player == null || player.getServer() == null) return;
        initCommon();

        MinecraftServer server = player.getServer();
        int dur = Math.max(1, durationTicks);
        long end = (long) server.getTicks() + (long) dur;

        END_TICK.put(player.getUuid(), end);
        sendState(player, true, dur);
    }

    /** Called by the mixin ONLY when damage actually happens. */
    public static void onVictimDamaged(ServerPlayerEntity victim, DamageSource source) {
        if (victim == null || victim.getServer() == null) return;
        if (!isActive(victim)) return;

        long now = victim.getServer().getTicks();
        long last = LAST_TRIGGER_TICK.getOrDefault(victim.getUuid(), Long.MIN_VALUE);
        if (last != Long.MIN_VALUE && (now - last) < TRIGGER_COOLDOWN_TICKS) return;
        LAST_TRIGGER_TICK.put(victim.getUuid(), now);

        LivingEntity attacker = resolveAttacker(source);
        if (attacker == null) return;
        if (attacker == victim) return;

        // gameplay effects
        attacker.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, GLOW_TICKS, 0, false, true, true));
        attacker.setOnFireFor(FIRE_SECONDS);

        // visuals for nearby clients
        broadcastTrigger(victim, attacker, TETHER_LIFE_TICKS);
    }

    private static LivingEntity resolveAttacker(DamageSource src) {
        if (src == null) return null;

        Entity a = src.getAttacker();
        if (a instanceof LivingEntity le) return le;

        Entity s = src.getSource();
        if (s instanceof LivingEntity le) return le;

        if (s instanceof ProjectileEntity proj) {
            Entity owner = proj.getOwner();
            if (owner instanceof LivingEntity le2) return le2;
        }

        return null;
    }

    private static void serverTick(MinecraftServer server) {
        long now = server.getTicks();

        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            long end = END_TICK.getOrDefault(p.getUuid(), Long.MIN_VALUE);
            if (end == Long.MIN_VALUE) continue;

            if (p.isSpectator()) {
                END_TICK.removeLong(p.getUuid());
                sendState(p, false, 0);
                continue;
            }

            if (now >= end) {
                END_TICK.removeLong(p.getUuid());
                sendState(p, false, 0);
            }
        }
    }

    private static void sendState(ServerPlayerEntity p, boolean active, int durationTicks) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(active);
        buf.writeVarInt(Math.max(0, durationTicks));
        ServerPlayNetworking.send(p, S2C_STATE, buf);
    }

    private static void broadcastTrigger(ServerPlayerEntity victim, LivingEntity attacker, int lifeTicks) {
        if (victim.getServer() == null) return;

        // IMPORTANT: new buffer per send (don’t reuse the same buf instance in a loop)
        for (ServerPlayerEntity sp : victim.getServer().getPlayerManager().getPlayerList()) {
            if (sp.getWorld() != victim.getWorld()) continue;
            if (sp.squaredDistanceTo(victim) > (96 * 96)) continue;

            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeVarInt(victim.getId());
            buf.writeVarInt(attacker.getId());
            buf.writeVarInt(Math.max(1, lifeTicks));
            ServerPlayNetworking.send(sp, S2C_TRIGGER, buf);
        }
    }

    /* ================= CLIENT ================= */

    @Environment(EnvType.CLIENT)
    public static final class Client {
        private static boolean clientInited = false;

        private static int ticksLeft = 0;
        private static int ticksMax  = 1;

        public static void init() {
            if (clientInited) return;
            clientInited = true;

            BrambleFx.init();
            BrambleTetherRenderer.init();

            ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
                ticksLeft = 0;
                ticksMax = 1;
                BrambleFx.stop();
                BrambleTetherRenderer.clearAll();
            });

            ClientPlayNetworking.registerGlobalReceiver(S2C_STATE, (client, handler, buf, rs) -> {
                boolean active = buf.readBoolean();
                int dur = buf.readVarInt();
                client.execute(() -> {
                    if (active) {
                        ticksMax = Math.max(ticksMax, Math.max(1, dur));
                        ticksLeft = Math.max(ticksLeft, Math.max(0, dur));
                        BrambleFx.start(dur);
                    } else {
                        ticksLeft = 0;
                        BrambleFx.stop();
                        // don’t wipe STRIKES immediately; just stop aura
                        BrambleTetherRenderer.setAuraActiveTicks(0, 1);
                    }
                });
            });

            ClientPlayNetworking.registerGlobalReceiver(S2C_TRIGGER, (client, handler, buf, rs) -> {
                int victimId = buf.readVarInt();
                int attackerId = buf.readVarInt();
                int life = buf.readVarInt();
                client.execute(() -> {
                    BrambleFx.pulse();
                    BrambleTetherRenderer.trigger(victimId, attackerId, life);
                });
            });

            ClientTickEvents.END_CLIENT_TICK.register(mc -> {
                if (mc == null || mc.player == null) return;

                if (ticksLeft > 0) {
                    ticksLeft--;
                    BrambleFx.tickClient();

                    // ✅ drive aura timing from THIS timer (no “stuck forever”)
                    BrambleTetherRenderer.setAuraActiveTicks(ticksLeft, ticksMax);

                    if (ticksLeft <= 0) {
                        BrambleFx.stop();
                        BrambleTetherRenderer.setAuraActiveTicks(0, 1);
                    }
                } else {
                    BrambleFx.tickClient();
                }
            });
        }
    }
}
