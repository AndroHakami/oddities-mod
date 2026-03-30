package net.seep.odd.block.false_memory.client;

import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.block.false_memory.FalseMemoryBlockEntity;
import software.bernie.geckolib.model.GeoModel;

public class FalseMemoryBlockModel extends GeoModel<FalseMemoryBlockEntity> {
    @Override
    public Identifier getModelResource(FalseMemoryBlockEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "geo/false_memory.geo.json");
    }

    @Override
    public Identifier getTextureResource(FalseMemoryBlockEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "textures/block/false_memory.png");
    }

    @Override
    public Identifier getAnimationResource(FalseMemoryBlockEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "animations/false_memory.animation.json");
    }
}