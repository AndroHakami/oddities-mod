// FILE: src/main/java/net/seep/odd/abilities/wizard/IcePrison.java
package net.seep.odd.abilities.wizard;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class IcePrison {
    private IcePrison() {}

    private static boolean inited = false;

    private static final Object2ObjectOpenHashMap<UUID, Entry> ACTIVE = new Object2ObjectOpenHashMap<>();

    private static final class Entry {
        final ServerWorld world;
        final UUID targetId;
        final List<BlockPos> placed;
        long endTick;

        Entry(ServerWorld world, UUID targetId, List<BlockPos> placed, long endTick) {
            this.world = world;
            this.targetId = targetId;
            this.placed = placed;
            this.endTick = endTick;
        }
    }

    private static void ensureInit() {
        if (inited) return;
        inited = true;

        ServerTickEvents.END_SERVER_TICK.register(IcePrison::tickAll);
    }

    public static void freeze(ServerWorld world, LivingEntity target, int durationTicks) {
        ensureInit();

        // visuals + gameplay
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, durationTicks, 4, false, true, true));
        target.setFrozenTicks(Math.max(target.getFrozenTicks(), durationTicks));

        Box bb = target.getBoundingBox();

        // cube bounds around entity, expanded so it "wraps" them
        int minX = MathHelper.floor(bb.minX) - 1;
        int maxX = MathHelper.floor(bb.maxX) + 1;
        int minY = MathHelper.floor(bb.minY);
        int maxY = MathHelper.ceil(bb.maxY) + 1;
        int minZ = MathHelper.floor(bb.minZ) - 1;
        int maxZ = MathHelper.floor(bb.maxZ) + 1;

        List<BlockPos> placed = new ArrayList<>();

        BlockState ice = Blocks.ICE.getDefaultState();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    boolean surface =
                            x == minX || x == maxX ||
                                    y == minY || y == maxY ||
                                    z == minZ || z == maxZ;

                    if (!surface) continue;

                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState cur = world.getBlockState(pos);

                    // only place where it's empty / replaceable
                    if (cur.isAir() || cur.isReplaceable()) {
                        world.setBlockState(pos, ice, Block.NOTIFY_LISTENERS);
                        placed.add(pos);
                    }
                }
            }
        }

        // initial burst of particles
        world.spawnParticles(ParticleTypes.SNOWFLAKE,
                target.getX(), target.getBodyY(0.5), target.getZ(),
                80, target.getWidth() * 0.8, target.getHeight() * 0.6, target.getWidth() * 0.8, 0.01);

        ACTIVE.put(target.getUuid(), new Entry(world, target.getUuid(), placed, world.getServer().getTicks() + durationTicks));
    }

    private static void tickAll(MinecraftServer server) {
        long now = server.getTicks();

        // particle animation + cleanup
        var it = ACTIVE.values().iterator();
        while (it.hasNext()) {
            Entry e = it.next();
            LivingEntity target = (LivingEntity) e.world.getEntity(e.targetId);

            if (target != null && target.isAlive()) {
                // animated snow swirling
                e.world.spawnParticles(ParticleTypes.SNOWFLAKE,
                        target.getX(), target.getBodyY(0.5), target.getZ(),
                        6, target.getWidth() * 0.7, target.getHeight() * 0.5, target.getWidth() * 0.7, 0.01);
            }

            if (now >= e.endTick || target == null || !target.isAlive()) {
                // remove placed ice (only if still ice)
                for (BlockPos pos : e.placed) {
                    if (e.world.getBlockState(pos).isOf(Blocks.ICE)) {
                        e.world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                    }
                }
                it.remove();
            }
        }
    }
}
