package net.seep.odd.render;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.RenderLayer.MultiPhaseParameters;
import net.minecraft.client.render.RenderPhase.*;
import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;

import static net.minecraft.client.render.RenderLayer.*; // for constants like ENTITY_GLINT_PROGRAM, etc.

/** Glint-like layer that uses our own texture and rides on top of the base item pass. */
public final class SuperchargeLayers {
    private SuperchargeLayers() {}

    public static final Identifier OVERLAY_TEX =
            new Identifier(Oddities.MOD_ID, "textures/misc/supercharge_overlay.png");

    private static RenderLayer SUPERCHARGE_OVERLAY;

    public static RenderLayer overlay() {
        if (SUPERCHARGE_OVERLAY == null) {
            // Match vanilla entity glint settings but swap in our texture.
            MultiPhaseParameters params = MultiPhaseParameters.builder()
                    .texture(new Texture(OVERLAY_TEX, true, false)) // blur=true, mipmap=false
                    .program(ENTITY_GLINT_PROGRAM)                  // <— 1.20.1 name
                    .texturing(ENTITY_GLINT_TEXTURING)              // drives the animated twinkle
                    .transparency(ADDITIVE_TRANSPARENCY)            // nice power-y bloom; switch to TRANSLUCENT if too strong
                    .cull(DISABLE_CULLING)
                    .depthTest(EQUAL_DEPTH_TEST)                    // only where the base already drew
                    .lightmap(DISABLE_LIGHTMAP)
                    .writeMaskState(COLOR_MASK)
                    .build(true);

            SUPERCHARGE_OVERLAY = RenderLayer.of(
                    "odd_supercharge_overlay",
                    VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL,
                    VertexFormat.DrawMode.QUADS,
                    256,
                    false,   // outline
                    true,    // needs sorting
                    params
            );
        }
        return SUPERCHARGE_OVERLAY;
    }
}
