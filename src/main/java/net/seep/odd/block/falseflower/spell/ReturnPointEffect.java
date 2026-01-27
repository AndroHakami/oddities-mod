// src/main/java/net/seep/odd/block/falseflower/spell/ReturnPointEffect.java
package net.seep.odd.block.falseflower.spell;

import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.seep.odd.block.falseflower.FalseFlowerBlockEntity;

public final class ReturnPointEffect implements FalseFlowerSpellEffect {
    private static final long DURATION = 20L * 20L;

    @Override
    public void tick(ServerWorld w, BlockPos pos, BlockState state, FalseFlowerBlockEntity be, int R, Box box) {
        Vec3d c = Vec3d.ofCenter(pos);

        for (ServerPlayerEntity sp : w.getPlayers()) {
            if (!FalseFlowerSpellUtil.insideSphere(sp.getPos(), c, R)) continue;
            FalseFlowerSpellRuntime.setReturnPoint(sp, DURATION);
        }
    }
}
