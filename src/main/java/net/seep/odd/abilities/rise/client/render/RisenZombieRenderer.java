// FILE: src/main/java/net/seep/odd/abilities/rise/client/render/RisenZombieRenderer.java
package net.seep.odd.abilities.rise.client.render;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import net.seep.odd.abilities.rise.entity.RisenZombieEntity;

public final class RisenZombieRenderer extends EntityRenderer<RisenZombieEntity> {

    // Dark-green tint (single-pass = no z-fighting shimmer / “head shake”)
    private static final float TINT_R = 0.42f;
    private static final float TINT_G = 0.82f;
    private static final float TINT_B = 0.42f;
    private static final float TINT_A = 1.00f;

    private static final class DummyState {
        Entity dummy;
        EntityType<?> type;
        NbtCompound lastApplied;
        String lastTypeId;
    }

    private final Int2ObjectOpenHashMap<DummyState> dummyCache = new Int2ObjectOpenHashMap<>();

    public RisenZombieRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.5F;
    }

    @Override
    public Identifier getTexture(RisenZombieEntity entity) {
        return new Identifier("minecraft", "textures/entity/zombie/zombie.png");
    }

    private DummyState getOrCreateDummy(RisenZombieEntity risen) {
        int key = risen.getId();
        DummyState st = dummyCache.get(key);

        String typeStr = risen.getSourceTypeId();
        Identifier typeId = Identifier.tryParse(typeStr);
        EntityType<?> type = (typeId == null) ? EntityType.ZOMBIE : Registries.ENTITY_TYPE.get(typeId);
        if (type == null) type = EntityType.ZOMBIE;

        // never recurse into our own renderer
        if (type == risen.getType()) type = EntityType.ZOMBIE;

        boolean needNew =
                (st == null)
                        || (st.dummy == null)
                        || (st.type != type)
                        || (st.dummy.getWorld() != risen.getWorld());

        if (needNew) {
            Entity d = type.create(risen.getWorld());
            if (d == null) d = EntityType.ZOMBIE.create(risen.getWorld());

            st = new DummyState();
            st.dummy = d;
            st.type = type;
            st.lastApplied = null;
            st.lastTypeId = typeStr;
            dummyCache.put(key, st);
        }

        return st;
    }

    @Override
    public void render(RisenZombieEntity risen, float entityYaw, float tickDelta,
                       MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {

        DummyState st = getOrCreateDummy(risen);
        if (st == null || st.dummy == null) {
            super.render(risen, entityYaw, tickDelta, matrices, vertexConsumers, light);
            return;
        }

        Entity dummy = st.dummy;

        // Apply appearance NBT only when it changes (prevents “reset every frame” bugs)
        NbtCompound nbt = risen.getSourceRenderNbt();
        if (nbt != null && !nbt.isEmpty()) {
            if (st.lastApplied == null || !st.lastApplied.equals(nbt)) {
                st.lastApplied = nbt.copy();
                try { dummy.readNbt(st.lastApplied.copy()); } catch (Throwable ignored) {}
                clearHurtAndDeathTint(dummy);
            }
        } else {
            clearHurtAndDeathTint(dummy);
        }

        // Sync pose/time/rotation + limb anim + head yaw (FIXED: no bogus prevYaw fallback)
        syncProxy(risen, dummy, tickDelta);

        // Stable tint using VertexConsumer wrapper (buffer-safe)
        VertexConsumerProvider tinted = new TintProvider(vertexConsumers, TINT_R, TINT_G, TINT_B, TINT_A);

        @SuppressWarnings("unchecked")
        EntityRenderer<Entity> r = (EntityRenderer<Entity>) MinecraftClient.getInstance()
                .getEntityRenderDispatcher()
                .getRenderer(dummy);

        r.render(dummy, entityYaw, tickDelta, matrices, tinted, light);
    }

    private static void clearHurtAndDeathTint(Entity dummy) {
        if (!(dummy instanceof LivingEntity le)) return;

        try { le.setHealth(le.getMaxHealth()); } catch (Throwable ignored) {}

        // try reset fields used by hurt/death overlay (mapping can differ)
        try { var f = LivingEntity.class.getDeclaredField("hurtTime"); f.setAccessible(true); f.setInt(le, 0); } catch (Throwable ignored) {}
        try { var f = LivingEntity.class.getDeclaredField("deathTime"); f.setAccessible(true); f.setInt(le, 0); } catch (Throwable ignored) {}
        try { var f = LivingEntity.class.getDeclaredField("timeUntilRegen"); f.setAccessible(true); f.setInt(le, 0); } catch (Throwable ignored) {}
        try { var f = LivingEntity.class.getDeclaredField("lastDamageTaken"); f.setAccessible(true); f.setFloat(le, 0f); } catch (Throwable ignored) {}
    }

    private static void syncProxy(RisenZombieEntity risen, Entity dummy, float tickDelta) {
        dummy.setPos(risen.getX(), risen.getY(), risen.getZ());
        dummy.setVelocity(risen.getVelocity());

        dummy.prevYaw = risen.prevYaw;
        dummy.setYaw(risen.getYaw());

        dummy.prevPitch = risen.prevPitch;
        dummy.setPitch(risen.getPitch());

        // keep time-based anims alive (Yarn usually allows this; ignore if it doesn’t)
        try { dummy.age = risen.age; } catch (Throwable ignored) {}

        if (dummy instanceof LivingEntity d) {
            // Copy body/head yaw *properly* (this is what fixes the “rapid head shake”)
            try {
                d.bodyYaw     = risen.bodyYaw;
                d.prevBodyYaw = risen.prevBodyYaw;

                d.headYaw     = risen.headYaw;
                d.prevHeadYaw = risen.prevHeadYaw;
            } catch (Throwable ignored) {}

            updateLimbAnimatorSafe(d, risen, tickDelta);
        }
    }

    private static void updateLimbAnimatorSafe(LivingEntity dummy, RisenZombieEntity risen, float tickDelta) {
        float speed = (float)Math.sqrt(risen.getVelocity().x * risen.getVelocity().x + risen.getVelocity().z * risen.getVelocity().z);
        float s = MathHelper.clamp(speed * 4.0F, 0.0F, 1.5F);

        try {
            var f = LivingEntity.class.getDeclaredField("limbAnimator");
            f.setAccessible(true);
            Object limb = f.get(dummy);
            if (limb == null) return;

            // try multiple signatures (mapping/version safe)
            if (tryInvoke(limb, "updateLimbs",
                    new Class[]{float.class, float.class, float.class},
                    new Object[]{s, 1.0F, tickDelta})) return;

            if (tryInvoke(limb, "updateLimbs",
                    new Class[]{float.class, float.class},
                    new Object[]{s, tickDelta})) return;

            if (tryInvoke(limb, "updateLimbs",
                    new Class[]{float.class, float.class},
                    new Object[]{s, 1.0F})) return;

            tryInvoke(limb, "updateLimbs",
                    new Class[]{float.class},
                    new Object[]{s});
        } catch (Throwable ignored) {}
    }

    private static boolean tryInvoke(Object obj, String name, Class<?>[] sig, Object[] args) {
        try {
            var m = obj.getClass().getMethod(name, sig);
            m.setAccessible(true);
            m.invoke(obj, args);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /* ===================== Tint provider (buffer-safe) ===================== */

    private static final class TintProvider implements VertexConsumerProvider {
        private final VertexConsumerProvider parent;
        private final float rm, gm, bm, am;

        TintProvider(VertexConsumerProvider parent, float rm, float gm, float bm, float am) {
            this.parent = parent;
            this.rm = rm; this.gm = gm; this.bm = bm; this.am = am;
        }

        @Override
        public VertexConsumer getBuffer(RenderLayer layer) {
            return new TintConsumer(parent.getBuffer(layer), rm, gm, bm, am);
        }
    }

    private static final class TintConsumer implements VertexConsumer {
        private final VertexConsumer d;
        private final float rm, gm, bm, am;

        TintConsumer(VertexConsumer d, float rm, float gm, float bm, float am) {
            this.d = d;
            this.rm = rm; this.gm = gm; this.bm = bm; this.am = am;
        }

        private static int clamp255(int v) { return v < 0 ? 0 : Math.min(255, v); }

        @Override public VertexConsumer vertex(double x, double y, double z) { d.vertex(x, y, z); return this; }

        @Override
        public VertexConsumer color(int r, int g, int b, int a) {
            int rr = clamp255(Math.round(r * rm));
            int gg = clamp255(Math.round(g * gm));
            int bb = clamp255(Math.round(b * bm));
            int aa = clamp255(Math.round(a * am));
            d.color(rr, gg, bb, aa);
            return this;
        }

        @Override public VertexConsumer texture(float u, float v) { d.texture(u, v); return this; }
        @Override public VertexConsumer overlay(int u, int v) { d.overlay(u, v); return this; }
        @Override public VertexConsumer light(int u, int v) { d.light(u, v); return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { d.normal(x, y, z); return this; }
        @Override public void next() { d.next(); }

        @Override
        public void fixedColor(int r, int g, int b, int a) {
            int rr = clamp255(Math.round(r * rm));
            int gg = clamp255(Math.round(g * gm));
            int bb = clamp255(Math.round(b * bm));
            int aa = clamp255(Math.round(a * am));
            d.fixedColor(rr, gg, bb, aa);
        }

        @Override
        public void unfixColor() {
            d.unfixColor();
        }
    }
}