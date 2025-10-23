package net.seep.odd.render;

import net.minecraft.client.render.VertexConsumer;

/** Writes a constant RGBA color for all vertices (ignores incoming per-vertex color). */
public final class FlatColorVertexConsumer implements VertexConsumer {
    private final VertexConsumer d;
    private final int r, g, b, a; // 0..255

    public FlatColorVertexConsumer(VertexConsumer delegate, float rf, float gf, float bf, float af) {
        this.d = delegate;
        this.r = clamp255(rf);
        this.g = clamp255(gf);
        this.b = clamp255(bf);
        this.a = clamp255(af);
    }

    private static int clamp255(float f) {
        int v = Math.round(f * 255f);
        if (v < 0) return 0;
        if (v > 255) return 255;
        return v;
    }

    @Override public VertexConsumer vertex(double x, double y, double z) { return d.vertex(x, y, z); }
    @Override public VertexConsumer color(int r, int g, int b, int a)     { return d.color(this.r, this.g, this.b, this.a); }
    @Override public VertexConsumer texture(float u, float v)             { return d.texture(u, v); }
    @Override public VertexConsumer overlay(int u, int v)                 { return d.overlay(u, v); }
    @Override public VertexConsumer light(int u, int v)                   { return d.light(u, v); }
    @Override public VertexConsumer normal(float nx, float ny, float nz)  { return d.normal(nx, ny, nz); }
    @Override public void next()                                          { d.next(); }

    @Override public void fixedColor(int r, int g, int b, int a)          { d.fixedColor(this.r, this.g, this.b, this.a); }
    @Override public void unfixColor()                                    { d.unfixColor(); }
}
