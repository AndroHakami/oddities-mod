package net.seep.odd.abilities.artificer.mixer.brew;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;

public final class LifeAuroraEffect {
    private LifeAuroraEffect() {}

    private static final Identifier S2C_LIFE_AURORA = new Identifier("odd", "life_aurora");

    // ===== TUNING =====
    public static final int DURATION_TICKS = 20 * 3; // 3 seconds
    public static final float RADIUS = 5.0f;

    // tick-based “instant lose”: only lasts a couple ticks unless refreshed
    private static final int REFRESH_TICKS = 3;

    // very high (amplifier = level-1)
    private static final int REGEN_AMP = 5;    // Regen VI
    private static final int STR_AMP   = 4;    // Strength V
    // ==================

    public static void apply(World world, BlockPos pos, @Nullable LivingEntity thrower, ItemStack stack) {
        if (!(world instanceof ServerWorld sw)) return;

        Vec3d center = Vec3d.ofCenter(pos).add(0.0, 0.05, 0.0);
        long auraId = makeId(sw, pos);

        // pretty sound at impact
        sw.playSound(null, pos, SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.BLOCKS, 0.9f, 1.55f);

        // tell nearby clients to render the aurora
        broadcastSpawn(sw, pos, auraId, center, RADIUS, DURATION_TICKS);

        // tick the zone for exactly 3 seconds
        for (int dt = 0; dt < DURATION_TICKS; dt++) {
            final int delay = dt;
            net.seep.odd.util.TickScheduler.runLater(sw, delay, () -> {
                if (!sw.isChunkLoaded(pos)) return;
                applyBuffTick(sw, center, RADIUS);
            });
        }
    }

    private static void applyBuffTick(ServerWorld sw, Vec3d center, double radius) {
        double r2 = radius * radius;

        Box aabb = new Box(
                center.x - radius, center.y - 2.0, center.z - radius,
                center.x + radius, center.y + 3.0, center.z + radius
        );

        for (LivingEntity e : sw.getEntitiesByClass(LivingEntity.class, aabb, ent -> ent.isAlive())) {
            if (e.squaredDistanceTo(center) > r2) continue;

            // refresh every tick so leaving drops quickly
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, REFRESH_TICKS, REGEN_AMP, false, false, true));
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE,     REFRESH_TICKS, STR_AMP,   false, false, true));
        }
    }

    private static long makeId(ServerWorld sw, BlockPos pos) {
        long t = sw.getTime();
        long mix = pos.asLong() ^ (t * 341873128712L) ^ (sw.getRandom().nextLong());
        return mix;
    }

    private static void broadcastSpawn(ServerWorld sw, BlockPos pos, long id, Vec3d center, float radius, int durationTicks) {
        for (ServerPlayerEntity watcher : PlayerLookup.tracking(sw, pos)) {
            sendSpawn(watcher, id, center, radius, durationTicks);
        }
    }

    private static void sendSpawn(ServerPlayerEntity p, long id, Vec3d center, float radius, int durationTicks) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeLong(id);
        buf.writeDouble(center.x);
        buf.writeDouble(center.y);
        buf.writeDouble(center.z);
        buf.writeFloat(radius);
        buf.writeVarInt(durationTicks);
        ServerPlayNetworking.send(p, S2C_LIFE_AURORA, buf);
    }

    /* ================= CLIENT ================= */

    @Environment(EnvType.CLIENT)
    public static final class Client {
        private static boolean inited = false;

        public static void init() {
            if (inited) return;
            inited = true;

            net.seep.odd.abilities.artificer.mixer.brew.client.LifeAuroraFx.init();

            ClientPlayNetworking.registerGlobalReceiver(S2C_LIFE_AURORA, (client, handler, buf, rs) -> {
                long id = buf.readLong();
                double x = buf.readDouble();
                double y = buf.readDouble();
                double z = buf.readDouble();
                float radius = buf.readFloat();
                int dur = buf.readVarInt();

                client.execute(() -> net.seep.odd.abilities.artificer.mixer.brew.client.LifeAuroraFx.spawn(id, x, y, z, radius, dur));
            });
        }
    }
}
