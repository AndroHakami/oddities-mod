// FILE: src/main/java/net/seep/odd/expeditions/rottenroots/boggy/BoggyBoatEntity.java
package net.seep.odd.expeditions.rottenroots.boggy;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.item.Item;
import net.minecraft.world.World;
import net.seep.odd.item.ModItems;

public class BoggyBoatEntity extends BoatEntity {

    public BoggyBoatEntity(EntityType<? extends BoatEntity> type, World world) {
        super(type, world);
        this.setVariant(BoatEntity.Type.OAK); // fine
    }

    @Override
    public Item asItem() {
        return ModItems.BOGGY_BOAT;
    }
}