// src/main/java/net/seep/odd/entity/necromancer/ZombieCorpseEntity.java
package net.seep.odd.entity.necromancer;

import net.minecraft.entity.EntityType;
import net.minecraft.world.World;

public final class ZombieCorpseEntity extends AbstractCorpseEntity {
    public ZombieCorpseEntity(EntityType<? extends ZombieCorpseEntity> type, World world) {
        super(type, world);
    }
}
