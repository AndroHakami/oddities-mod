package net.seep.odd.block.rps_machine.client;

import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.block.rps_machine.RpsMachineBlockEntity;
import software.bernie.geckolib.model.GeoModel;

public class RpsMachineBlockModel extends GeoModel<RpsMachineBlockEntity> {
    @Override
    public Identifier getModelResource(RpsMachineBlockEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "geo/rps_machine.geo.json");
    }

    @Override
    public Identifier getTextureResource(RpsMachineBlockEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "textures/block/rps_machine.png");
    }

    @Override
    public Identifier getAnimationResource(RpsMachineBlockEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "animations/rps_machine.animation.json");
    }
}