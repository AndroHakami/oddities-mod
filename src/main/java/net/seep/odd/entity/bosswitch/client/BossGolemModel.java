package net.seep.odd.entity.bosswitch.client;

import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.entity.bosswitch.BossGolemEntity;
import software.bernie.geckolib.model.GeoModel;

public final class BossGolemModel extends GeoModel<BossGolemEntity> {
    @Override
    public Identifier getModelResource(BossGolemEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "geo/boss_golem.geo.json");
    }

    @Override
    public Identifier getTextureResource(BossGolemEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "textures/entity/boss_golem.png");
    }

    @Override
    public Identifier getAnimationResource(BossGolemEntity animatable) {
        return new Identifier(Oddities.MOD_ID, "animations/boss_golem.animation.json");
    }
}