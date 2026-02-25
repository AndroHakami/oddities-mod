// src/main/java/net/seep/odd/block/cosmic_katana/client/CosmicKatanaBlockModel.java
package net.seep.odd.block.cosmic_katana.client;

import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.block.cosmic_katana.CosmicKatanaBlockEntity;
import software.bernie.geckolib.model.GeoModel;

public class CosmicKatanaBlockModel extends GeoModel<CosmicKatanaBlockEntity> {
    @Override
    public Identifier getModelResource(CosmicKatanaBlockEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "geo/cosmic_katana_block.geo.json");
    }

    @Override
    public Identifier getTextureResource(CosmicKatanaBlockEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "textures/block/cosmic_katana_block.png");
    }

    @Override
    public Identifier getAnimationResource(CosmicKatanaBlockEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "animations/cosmic_katana_block.animation.json");
    }
}