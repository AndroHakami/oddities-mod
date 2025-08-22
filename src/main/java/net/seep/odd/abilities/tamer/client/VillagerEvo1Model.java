package net.seep.odd.abilities.tamer.client;

import net.minecraft.util.Identifier;
import net.seep.odd.abilities.tamer.entity.VillagerEvo1Entity;
import software.bernie.geckolib.model.GeoModel;


public class VillagerEvo1Model extends GeoModel<VillagerEvo1Entity> {
    @Override public Identifier getModelResource(VillagerEvo1Entity a)      { return new Identifier("odd","geo/villager_evo1.geo.json"); }
    @Override public Identifier getTextureResource(VillagerEvo1Entity a)    { return new Identifier("odd","textures/entity/villager_evo1.png"); }
    @Override public Identifier getAnimationResource(VillagerEvo1Entity a)  { return new Identifier("odd","animations/villager_evo1.animation.json"); }
}
