package net.seep.odd.render;

import net.minecraft.client.render.VertexConsumer;

/** Multiplies incoming vertex color by a tint; all other attributes pass through. */
public final class TintingVertexConsumer implements VertexConsumer {
    private final VertexConsumer d;
    private final float tr, tg, tb;

    public TintingVertexConsumer(VertexConsumer delegate, float r, float g, float b) {
        this.d = delegate; this.tr = r; this.tg = g; this.tb = b;
    }

    private int s(int c, float t) { int v = Math.round(c * t); return v > 255 ? 255 : Math.max(v, 0); }

    @Override public VertexConsumer vertex(double x, double y, double z) { return d.vertex(x, y, z); }

    @Override
    public VertexConsumer color(int r, int g, int b, int a) {
        return d.color(s(r, tr), s(g, tg), s(b, tb), a);
    }

    @Override public VertexConsumer texture(float u, float v) { return d.texture(u, v); }
    @Override public VertexConsumer overlay(int u, int v) { return d.overlay(u, v); }
    @Override public VertexConsumer light(int u, int v) { return d.light(u, v); }
    @Override public VertexConsumer normal(float nx, float ny, float nz) { return d.normal(nx, ny, nz); }
    @Override public void next() { d.next(); }

    @Override
    public void fixedColor(int r, int g, int b, int a) {
        d.fixedColor(s(r, tr), s(g, tg), s(b, tb), a);
    }

    @Override public void unfixColor() { d.unfixColor(); }
}
