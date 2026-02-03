// src/main/java/net/seep/odd/entity/necromancer/SkeletonCorpseEntity.java
package net.seep.odd.entity.necromancer;

import net.minecraft.entity.EntityType;
import net.minecraft.world.World;

public final class SkeletonCorpseEntity extends AbstractCorpseEntity {
    public SkeletonCorpseEntity(EntityType<? extends SkeletonCorpseEntity> type, World world) {
        super(type, world);
    }
}
