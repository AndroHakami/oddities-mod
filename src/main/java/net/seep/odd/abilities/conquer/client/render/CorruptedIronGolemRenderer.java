package net.seep.odd.abilities.conquer.client.render;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.IronGolemEntityRenderer;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.abilities.conquer.client.CorruptionRenderUtil;

public final class CorruptedIronGolemRenderer extends IronGolemEntityRenderer {

    private static final Identifier DARK_GOLEM =
            new Identifier(Oddities.MOD_ID, "textures/entity/conquer/dark_iron_golem.png");

    private static boolean printedOnce = false;

    public CorruptedIronGolemRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        System.out.println("[Oddities][Conquer] CorruptedIronGolemRenderer CONSTRUCTED");
    }

    @Override
    public Identifier getTexture(IronGolemEntity entity) {
        return DARK_GOLEM;
    }


}
