package net.seep.odd.abilities.conquer.client.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.minecraft.client.render.entity.AbstractHorseEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.HorseEntityModel;
import net.minecraft.util.Identifier;

import net.seep.odd.abilities.conquer.entity.DarkHorseEntity;

@Environment(EnvType.CLIENT)
public final class DarkHorseEntityRenderer extends AbstractHorseEntityRenderer<DarkHorseEntity, HorseEntityModel<DarkHorseEntity>> {

    // Adjust path to match where you put the texture
    private static final Identifier TEXTURE = new Identifier("odd", "textures/entity/conquer/dark_horse.png");

    public DarkHorseEntityRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new HorseEntityModel<>(ctx.getPart(EntityModelLayers.HORSE)), 0.75f);
    }

    @Override
    public Identifier getTexture(DarkHorseEntity entity) {
        return TEXTURE;
    }
}
