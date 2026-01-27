// src/main/java/net/seep/odd/block/falseflower/spell/StormEffect.java
package net.seep.odd.block.falseflower.spell;

import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.*;
import net.seep.odd.block.falseflower.FalseFlowerBlockEntity;

public final class StormEffect implements FalseFlowerSpellEffect {
    @Override
    public void tick(ServerWorld w, BlockPos pos, BlockState state, FalseFlowerBlockEntity be, int R, Box box) {
        Vec3d c = Vec3d.ofCenter(pos);

        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, box, LivingEntity::isAlive)) {
            if (!FalseFlowerSpellUtil.insideSphere(e.getPos(), c, R)) continue;
            var bolt = EntityType.LIGHTNING_BOLT.create(w);
            if (bolt != null) {
                bolt.refreshPositionAfterTeleport(e.getX(), e.getY(), e.getZ());
                w.spawnEntity(bolt);
            }
        }

        w.playSound(null, pos, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.BLOCKS, 1.2f, 0.9f);
    }
}
