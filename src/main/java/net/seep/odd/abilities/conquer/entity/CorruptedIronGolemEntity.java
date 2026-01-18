package net.seep.odd.abilities.conquer.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.world.World;

public class CorruptedIronGolemEntity extends IronGolemEntity {
    public CorruptedIronGolemEntity(EntityType<? extends IronGolemEntity> type, World world) {
        super(type, world);
    }
}
