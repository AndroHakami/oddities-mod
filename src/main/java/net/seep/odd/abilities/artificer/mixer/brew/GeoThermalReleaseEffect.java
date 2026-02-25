package net.seep.odd.abilities.artificer.mixer.brew;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * Geo-thermal Release (DRINK):
 * - 30s buff (server authoritative)
 * - Visible to OTHER players:
 *   server broadcasts active state to PlayerLookup.tracking(player) + self
 * - If player dies while active: BIG explosion
 */
public final class GeoThermalReleaseEffect {
    private GeoThermalReleaseEffect() {}

    public static final int DEFAULT_DURATION_TICKS = 20 * 30;

    // 🔥 tune
    public static final float EXPLOSION_POWER = 6.5f;
    public static final boolean EXPLOSION_DESTROYS_BLOCKS = true;

    private static final Identifier S2C_GEOTHERMAL = new Identifier("odd", "geothermal_release");

    // store end tick in SERVER ticks
    private static final Object2LongOpenHashMap<UUID> END_TICK = new Object2LongOpenHashMap<>();
    private static boolean inited = false;

    /** Call from common init if you want; start() also calls it. */
    public static void bootstrapCommon() { initCommon(); }

    private static void initCommon() {
        if (inited) return;
        inited = true;

        ServerTickEvents.START_SERVER_TICK.register(GeoThermalReleaseEffect::serverTick);

        // ✅ explode on death if active
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof ServerPlayerEntity sp)) return;

            if (isActive(sp)) {
                END_TICK.removeLong(sp.getUuid());
                explode(sp);

                // tell everyone watching them to stop rendering it
                broadcast(sp, false, 0);
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.player.getUuid();
            END_TICK.removeLong(id);

            // make trackers drop the aura immediately
            broadcast(handler.player, false, 0);
        });
    }

    /** Used by mixins/HUD etc. Safe on both sides. */
    public static boolean isActive(PlayerEntity player) {
        if (player == null) return false;

        // client: use local map (aura visible to you)
        if (player.getWorld() != null && player.getWorld().isClient) {
            return net.seep.odd.abilities.artificer.mixer.brew.client.GeoThermalWorldFx.getTicksLeft(player.getUuid()) > 0;
        }

        if (player instanceof ServerPlayerEntity sp) {
            long end = END_TICK.getOrDefault(sp.getUuid(), Long.MIN_VALUE);
            if (end == Long.MIN_VALUE) return false;
            long now = sp.getServer().getTicks();
            return now < end;
        }

        return false;
    }

    public static void start(ServerPlayerEntity player, int durationTicks) {
        if (player == null || player.getServer() == null) return;
        initCommon();

        MinecraftServer server = player.getServer();
        long end = (long) server.getTicks() + (long) durationTicks;
        END_TICK.put(player.getUuid(), end);

        // start feedback
        player.getServerWorld().spawnParticles(ParticleTypes.FLAME,
                player.getX(), player.getY() + 0.9, player.getZ(),
                30, 0.35, 0.35, 0.35, 0.01);
        player.getServerWorld().playSound(null, player.getBlockPos(),
                SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.55f, 1.25f);

        // ✅ broadcast to watchers + self (so others see aura)
        broadcast(player, true, durationTicks);
    }

    private static void serverTick(MinecraftServer server) {
        long now = server.getTicks();

        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            long end = END_TICK.getOrDefault(p.getUuid(), Long.MIN_VALUE);
            if (end == Long.MIN_VALUE) continue;

            if (p.isSpectator()) {
                END_TICK.removeLong(p.getUuid());
                broadcast(p, false, 0);
                continue;
            }

            if (now >= end) {
                END_TICK.removeLong(p.getUuid());
                broadcast(p, false, 0);
                continue;
            }

            // ✅ re-sync every second so NEW nearby players also see it
            if ((now % 20L) == 0L) {
                int remaining = (int) Math.max(0L, end - now);
                broadcast(p, true, remaining);
            }
        }
    }

    private static void explode(ServerPlayerEntity sp) {
        var sw = sp.getServerWorld();

        sw.spawnParticles(ParticleTypes.LAVA,
                sp.getX(), sp.getY() + 0.6, sp.getZ(),
                28, 0.45, 0.30, 0.45, 0.02);
        sw.spawnParticles(ParticleTypes.FLAME,
                sp.getX(), sp.getY() + 0.6, sp.getZ(),
                120, 0.75, 0.50, 0.75, 0.02);

        sw.playSound(null, sp.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 1.2f, 0.95f);

        if (EXPLOSION_DESTROYS_BLOCKS) {
            sw.createExplosion(sp, sp.getX(), sp.getY(), sp.getZ(), EXPLOSION_POWER, World.ExplosionSourceType.TNT);
        } else {
            sw.createExplosion(sp, sp.getX(), sp.getY(), sp.getZ(), EXPLOSION_POWER, World.ExplosionSourceType.NONE);
        }
    }

    /** ✅ Send effect state about "subject" to a specific "target" client. */
    private static void send(ServerPlayerEntity target, UUID subject, boolean active, int durationTicks) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(subject);
        buf.writeBoolean(active);
        buf.writeVarInt(Math.max(0, durationTicks));
        ServerPlayNetworking.send(target, S2C_GEOTHERMAL, buf);
    }

    /** ✅ Broadcast to tracking players + the subject themselves. */
    private static void broadcast(ServerPlayerEntity subject, boolean active, int durationTicks) {
        UUID id = subject.getUuid();

        // everyone tracking this entity (nearby players)
        for (ServerPlayerEntity watcher : PlayerLookup.tracking(subject)) {
            send(watcher, id, active, durationTicks);
        }

        // and self
        send(subject, id, active, durationTicks);
    }

    /* ================= CLIENT ================= */

    @Environment(EnvType.CLIENT)
    public static final class Client {
        private static boolean initedClient = false;

        public static void init() {
            if (initedClient) return;
            initedClient = true;

            net.seep.odd.abilities.artificer.mixer.brew.client.GeoThermalOverlayFx.init();
            net.seep.odd.abilities.artificer.mixer.brew.client.GeoThermalWorldFx.init();

            ClientPlayNetworking.registerGlobalReceiver(S2C_GEOTHERMAL, (client, handler, buf, rs) -> {
                UUID subject = buf.readUuid();
                boolean active = buf.readBoolean();
                int dur = buf.readVarInt();

                client.execute(() -> {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    if (mc == null || mc.world == null) return;

                    // ✅ world aura for ANY subject (self + others)
                    if (active) {
                        net.seep.odd.abilities.artificer.mixer.brew.client.GeoThermalWorldFx.setActive(subject, dur);
                    } else {
                        net.seep.odd.abilities.artificer.mixer.brew.client.GeoThermalWorldFx.clear(subject);
                    }

                    // ✅ overlay ONLY for the local player
                    if (mc.player != null && subject.equals(mc.player.getUuid())) {
                        if (active) {
                            net.seep.odd.abilities.artificer.mixer.brew.client.GeoThermalOverlayFx.start(dur);
                            mc.player.playSound(SoundEvents.BLOCK_LAVA_AMBIENT, 0.55f, 1.1f);
                        } else {
                            net.seep.odd.abilities.artificer.mixer.brew.client.GeoThermalOverlayFx.stop();
                        }
                    }
                });
            });

            ClientTickEvents.END_CLIENT_TICK.register(mc -> {
                if (mc == null) return;

                net.seep.odd.abilities.artificer.mixer.brew.client.GeoThermalOverlayFx.tickClient();
                net.seep.odd.abilities.artificer.mixer.brew.client.GeoThermalWorldFx.tickClient();
            });
        }
    }
}
