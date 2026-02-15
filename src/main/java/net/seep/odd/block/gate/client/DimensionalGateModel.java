package net.seep.odd.block.gate.client;

import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.block.gate.DimensionalGateBlockEntity;
import software.bernie.geckolib.model.GeoModel;

public class DimensionalGateModel extends GeoModel<DimensionalGateBlockEntity> {
    @Override
    public Identifier getModelResource(DimensionalGateBlockEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "geo/dimensional_gate.geo.json");
    }

    @Override
    public Identifier getTextureResource(DimensionalGateBlockEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "textures/block/dimensional_gate.png");
    }

    @Override
    public Identifier getAnimationResource(DimensionalGateBlockEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "animations/dimensional_gate.animation.json");
    }
}
