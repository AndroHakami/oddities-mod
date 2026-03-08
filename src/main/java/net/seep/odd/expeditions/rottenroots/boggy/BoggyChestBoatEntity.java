// FILE: src/main/java/net/seep/odd/expeditions/rottenroots/boggy/BoggyChestBoatEntity.java
package net.seep.odd.expeditions.rottenroots.boggy;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.entity.vehicle.ChestBoatEntity;
import net.minecraft.item.Item;
import net.minecraft.world.World;
import net.seep.odd.item.ModItems;

public class BoggyChestBoatEntity extends ChestBoatEntity {

    public BoggyChestBoatEntity(EntityType<? extends ChestBoatEntity> type, World world) {
        super(type, world);

        // 1.20.1 uses "variant", not setBoatType()
        this.setVariant(BoatEntity.Type.OAK);
    }

    @Override
    public Item asItem() {
        return ModItems.BOGGY_CHEST_BOAT;
    }
}