package net.seep.odd.abilities.tamer.client;

import net.minecraft.util.Identifier;
import net.seep.odd.abilities.tamer.entity.VillagerEvoEntity;
import software.bernie.geckolib.model.GeoModel;


public class VillagerEvo1Model extends GeoModel<VillagerEvoEntity> {
    @Override public Identifier getModelResource(VillagerEvoEntity a)      { return new Identifier("odd","geo/villager_evo.geo.json"); }
    @Override public Identifier getTextureResource(VillagerEvoEntity a)    { return new Identifier("odd","textures/entity/villager_evo.png"); }
    @Override public Identifier getAnimationResource(VillagerEvoEntity a)  { return new Identifier("odd","animations/villager_evo.animation.json"); }
}
