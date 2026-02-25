// FILE: src/main/java/net/seep/odd/abilities/artificer/mixer/brew/FrostyStepsEffect.java
package net.seep.odd.abilities.artificer.mixer.brew;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

public final class FrostyStepsEffect {
    private FrostyStepsEffect() {}

    private static final Identifier S2C = new Identifier("odd", "frosty_steps");

    private static final Object2LongOpenHashMap<UUID> END_TICK = new Object2LongOpenHashMap<>();
    private static boolean inited = false;

    private static void initCommon() {
        if (inited) return;
        inited = true;

        ServerTickEvents.START_SERVER_TICK.register(FrostyStepsEffect::serverTick);

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.player.getUuid();
            END_TICK.removeLong(id);
            send(handler.player, false, 0);
        });
    }

    public static void start(ServerPlayerEntity player, int durationTicks) {
        if (player == null || player.getServer() == null) return;
        initCommon();

        long end = player.getServer().getTicks() + durationTicks;
        END_TICK.put(player.getUuid(), end);

        send(player, true, durationTicks);

        player.getWorld().playSound(null, player.getBlockPos(),
                SoundEvents.BLOCK_GLASS_HIT, SoundCategory.PLAYERS, 0.65f, 1.8f);
    }

    public static boolean isActive(PlayerEntity p) {
        if (p == null) return false;

        if (p.getWorld() != null && p.getWorld().isClient) {
            return ClientState.ticksLeft > 0;
        }
        if (p instanceof ServerPlayerEntity sp) {
            long end = END_TICK.getOrDefault(sp.getUuid(), Long.MIN_VALUE);
            if (end == Long.MIN_VALUE) return false;
            return sp.getServer().getTicks() < end;
        }
        return false;
    }

    private static void serverTick(MinecraftServer server) {
        long now = server.getTicks();

        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            long end = END_TICK.getOrDefault(p.getUuid(), Long.MIN_VALUE);
            if (end == Long.MIN_VALUE) continue;

            if (now >= end) {
                END_TICK.removeLong(p.getUuid());
                send(p, false, 0);
                continue;
            }

            // buffs that help the “cold runner” vibe
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 10, 0, false, false, true));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 10, 0, false, false, true));

            ServerWorld sw = (ServerWorld) p.getWorld();
            BlockPos feet = p.getBlockPos();

            // freeze water under/near feet + icefy nearby ground (small radius)
            if ((now % 2) == 0) {
                freezeNearby(sw, feet, 2);
            }

            // freezing damage + slow to nearby entities
            if ((now % 10) == 0) {
                Vec3d c = p.getPos();
                Box box = new Box(
                        c.x - 2.5, c.y - 1.5, c.z - 2.5,
                        c.x + 2.5, c.y + 1.5, c.z + 2.5
                );

                for (LivingEntity e : sw.getEntitiesByClass(LivingEntity.class, box, ent -> ent != p)) {
                    e.setFrozenTicks(Math.max(e.getFrozenTicks(), 120));
                    e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 2, false, true, true));

                    // light DOT (freeze damage)
                    e.damage(sw.getDamageSources().freeze(), 1.0f);
                }
            }
        }
    }

    private static void freezeNearby(ServerWorld sw, BlockPos center, int r) {
        BlockPos.Mutable m = new BlockPos.Mutable();

        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx*dx + dz*dz > r*r) continue;

                // check ground and “water layer”
                for (int dy = -1; dy <= 0; dy++) {
                    m.set(center.getX()+dx, center.getY()+dy, center.getZ()+dz);
                    BlockState st = sw.getBlockState(m);

                    if (st.isOf(Blocks.WATER)) {
                        sw.setBlockState(m, Blocks.ICE.getDefaultState(), Block.NOTIFY_ALL);
                        sw.spawnParticles(net.minecraft.particle.ParticleTypes.SNOWFLAKE,
                                m.getX()+0.5, m.getY()+0.8, m.getZ()+0.5,
                                2, 0.12, 0.12, 0.12, 0.02);
                        continue;
                    }

                    // light “icey” conversion only for common surface blocks
                    Block b = st.getBlock();
                    if (b == Blocks.GRASS_BLOCK || b == Blocks.DIRT || b == Blocks.COARSE_DIRT
                            || b == Blocks.SAND || b == Blocks.GRAVEL || b == Blocks.STONE) {
                        sw.setBlockState(m, Blocks.ICE.getDefaultState(), Block.NOTIFY_ALL);
                        sw.spawnParticles(net.minecraft.particle.ParticleTypes.SNOWFLAKE,
                                m.getX()+0.5, m.getY()+1.01, m.getZ()+0.5,
                                1, 0.10, 0.02, 0.10, 0.01);
                    }
                }
            }
        }
    }

    private static void send(ServerPlayerEntity p, boolean active, int durationTicks) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(active);
        buf.writeVarInt(Math.max(0, durationTicks));
        ServerPlayNetworking.send(p, S2C, buf);
    }

    /* ================= CLIENT ================= */

    @Environment(EnvType.CLIENT)
    private static final class ClientState {
        private static int ticksLeft = 0;
        private static boolean inited = false;
    }

    @Environment(EnvType.CLIENT)
    public static final class Client {
        public static void init() {
            if (ClientState.inited) return;
            ClientState.inited = true;

            net.seep.odd.abilities.artificer.mixer.brew.client.FrostyStepsOverlayFx.init();

            ClientPlayNetworking.registerGlobalReceiver(S2C, (client, handler, buf, rs) -> {
                boolean active = buf.readBoolean();
                int dur = buf.readVarInt();
                client.execute(() -> {
                    if (active) {
                        ClientState.ticksLeft = Math.max(ClientState.ticksLeft, dur);
                        net.seep.odd.abilities.artificer.mixer.brew.client.FrostyStepsOverlayFx.start(dur);
                    } else {
                        ClientState.ticksLeft = 0;
                        net.seep.odd.abilities.artificer.mixer.brew.client.FrostyStepsOverlayFx.stop();
                    }
                });
            });

            ClientTickEvents.END_CLIENT_TICK.register(mc -> {
                if (ClientState.ticksLeft > 0) {
                    ClientState.ticksLeft--;
                    net.seep.odd.abilities.artificer.mixer.brew.client.FrostyStepsOverlayFx.tickClient();
                    if (ClientState.ticksLeft <= 0) net.seep.odd.abilities.artificer.mixer.brew.client.FrostyStepsOverlayFx.stop();
                } else {
                    net.seep.odd.abilities.artificer.mixer.brew.client.FrostyStepsOverlayFx.tickClient();
                }
            });
        }
    }
}
