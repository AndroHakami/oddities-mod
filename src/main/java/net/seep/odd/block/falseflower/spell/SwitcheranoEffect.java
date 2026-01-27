// src/main/java/net/seep/odd/block/falseflower/spell/SwitcheranoEffect.java
package net.seep.odd.block.falseflower.spell;

import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.*;
import net.seep.odd.block.falseflower.FalseFlowerBlockEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import net.minecraft.util.math.random.Random;

public final class SwitcheranoEffect implements FalseFlowerSpellEffect {
    private static final long PERIOD = 100L; // 5 seconds

    @Override
    public void tick(ServerWorld w, BlockPos pos, BlockState state, FalseFlowerBlockEntity be, int R, Box box) {
        Vec3d c = Vec3d.ofCenter(pos);

        if (be.nextSwapTick == 0L) be.nextSwapTick = w.getTime() + PERIOD;
        if (w.getTime() < be.nextSwapTick) return;
        be.nextSwapTick = w.getTime() + PERIOD;

        List<ServerPlayerEntity> inside = new ArrayList<>();
        for (ServerPlayerEntity sp : w.getPlayers()) {
            if (FalseFlowerSpellUtil.insideSphere(sp.getPos(), c, R)) inside.add(sp);
        }
        if (inside.size() < 2) return;

        List<Vec3d> locs = new ArrayList<>(inside.size());
        for (ServerPlayerEntity sp : inside) locs.add(sp.getPos());
        net.minecraft.util.Util.shuffle((IntStream) locs, w.random);

        for (int i = 0; i < inside.size(); i++) {
            ServerPlayerEntity sp = inside.get(i);
            Vec3d t = locs.get(i);
            sp.teleport(w, t.x, t.y, t.z, sp.getYaw(), sp.getPitch());
            w.spawnParticles(net.minecraft.particle.ParticleTypes.PORTAL,
                    sp.getX(), sp.getY() + 0.8, sp.getZ(),
                    10, 0.4, 0.4, 0.4, 0.02);
        }

        w.playSound(null, pos, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.BLOCKS, 0.65f, 1.2f);
    }
}
