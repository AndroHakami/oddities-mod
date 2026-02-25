// src/main/java/net/seep/odd/block/combiner/client/CombinerModel.java
package net.seep.odd.block.combiner.client;

import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.block.combiner.CombinerBlockEntity;
import software.bernie.geckolib.model.GeoModel;

public class CombinerModel extends GeoModel<CombinerBlockEntity> {
    @Override
    public Identifier getModelResource(CombinerBlockEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "geo/combiner.geo.json");
    }

    @Override
    public Identifier getTextureResource(CombinerBlockEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "textures/block/combiner.png");
    }

    @Override
    public Identifier getAnimationResource(CombinerBlockEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "animations/combiner.animation.json");
    }
}