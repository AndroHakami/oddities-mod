// FILE: src/main/java/net/seep/odd/abilities/wizard/entity/client/CapybaraFamiliarModel.java
package net.seep.odd.abilities.wizard.entity.client;

import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.abilities.wizard.entity.CapybaraFamiliarEntity;
import software.bernie.geckolib.model.GeoModel;

/**
 * Uses GeckoLib geo + animation json.
 * If you already have a capybara geo/anim/texture, you can point these identifiers to those.
 */
public final class CapybaraFamiliarModel extends GeoModel<CapybaraFamiliarEntity> {

    // ✅ If you want to REUSE your existing capybara assets, just change these paths.
    private static final Identifier MODEL = new Identifier(Oddities.MOD_ID, "geo/capybara_familiar.geo.json");
    private static final Identifier ANIM  = new Identifier(Oddities.MOD_ID, "animations/capybara_familiar.animation.json");
    private static final Identifier TEX   = new Identifier(Oddities.MOD_ID, "textures/entity/capybara_familiar.png");

    @Override
    public Identifier getModelResource(CapybaraFamiliarEntity animatable) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(CapybaraFamiliarEntity animatable) {
        return TEX;
    }

    @Override
    public Identifier getAnimationResource(CapybaraFamiliarEntity animatable) {
        return ANIM;
    }
}
