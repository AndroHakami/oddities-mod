package net.seep.odd.abilities.rise.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.ints.Int2ByteOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import net.minecraft.client.MinecraftClient;
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
import net.minecraft.util.math.Vec3d;

import net.seep.odd.abilities.rise.entity.RisenZombieEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class RisenZombieRenderer extends EntityRenderer<RisenZombieEntity> {

    private final Int2ObjectOpenHashMap<Entity> dummyCache = new Int2ObjectOpenHashMap<>();
    private final Int2ByteOpenHashMap lastMoveState = new Int2ByteOpenHashMap(); // 0 idle, 1 walk, 2 run

    public RisenZombieRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.5F;
    }

    @Override
    public Identifier getTexture(RisenZombieEntity entity) {
        return new Identifier("minecraft", "textures/entity/zombie/zombie.png");
    }

    private Entity getOrCreateDummy(RisenZombieEntity risen) {
        int key = risen.getId();
        Entity cached = dummyCache.get(key);

        Identifier typeId = Identifier.tryParse(risen.getSourceTypeId());
        EntityType<?> type = (typeId == null) ? EntityType.ZOMBIE : Registries.ENTITY_TYPE.get(typeId);
        if (type == null) type = EntityType.ZOMBIE;

        if (type == risen.getType()) type = EntityType.ZOMBIE;

        if (cached == null || cached.getType() != type || cached.getWorld() != risen.getWorld()) {
            cached = type.create(risen.getWorld());
            if (cached == null) cached = EntityType.ZOMBIE.create(risen.getWorld());
            dummyCache.put(key, cached);
        }
        return cached;
    }

    @Override
    public void render(RisenZombieEntity risen, float entityYaw, float tickDelta,
                       MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {

        Entity dummy = getOrCreateDummy(risen);
        if (dummy == null) {
            super.render(risen, entityYaw, tickDelta, matrices, vertexConsumers, light);
            return;
        }

        // Apply appearance NBT
        NbtCompound nbt = risen.getSourceRenderNbt();
        if (nbt != null && !nbt.isEmpty()) {
            try { dummy.readNbt(nbt.copy()); } catch (Throwable ignored) {}
        }

        // ✅ ensure dummy is NOT stuck in "hurt/death" overlay
        clearHurtAndDeathTint(dummy);

        // Sync movement/rotation/time + vanilla limb anim + clamp head yaw
        syncProxy(risen, dummy, tickDelta);

        // Run > Walk > Idle (geckolib-only, safe)
        tryRunWalkIdle(dummy, risen);

        // Two-pass: desaturate then green overlay
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        try {
            RenderSystem.setShaderColor(0.62F, 0.62F, 0.62F, 1.0F);
            MinecraftClient.getInstance().getEntityRenderDispatcher().render(
                    dummy, 0.0, 0.0, 0.0, entityYaw, tickDelta, matrices, vertexConsumers, light
            );

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(0.10F, 0.62F, 0.10F, 0.70F);
            MinecraftClient.getInstance().getEntityRenderDispatcher().render(
                    dummy, 0.0, 0.0, 0.0, entityYaw, tickDelta, matrices, vertexConsumers, light
            );
        } finally {
            RenderSystem.disableBlend();
            RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        }
    }

    private static void clearHurtAndDeathTint(Entity dummy) {
        if (!(dummy instanceof LivingEntity)) return;
        LivingEntity le = (LivingEntity) dummy;

        // Health: make sure it isn't 0 from the kill snapshot
        try {
            le.setHealth(le.getMaxHealth());
        } catch (Throwable ignored) {}

        // Reflection: zero the fields used by LivingEntityRenderer overlay
        trySetIntField(le, LivingEntity.class, "hurtTime", 0);
        trySetIntField(le, LivingEntity.class, "deathTime", 0);
        trySetIntField(le, LivingEntity.class, "timeUntilRegen", 0);
        trySetFloatField(le, LivingEntity.class, "lastDamageTaken", 0.0F);
    }

    private static void syncProxy(RisenZombieEntity risen, Entity dummy, float tickDelta) {
        dummy.setPos(risen.getX(), risen.getY(), risen.getZ());
        dummy.setVelocity(risen.getVelocity());

        float bodyPrev = risen.prevYaw;
        float bodyNow  = risen.getYaw();

        dummy.prevYaw = bodyPrev;
        dummy.setYaw(bodyNow);

        dummy.prevPitch = risen.prevPitch;
        dummy.setPitch(risen.getPitch());

        // keep time-based anims alive
        try {
            Field f = Entity.class.getDeclaredField("age");
            f.setAccessible(true);
            f.setInt(dummy, risen.age);
        } catch (Throwable ignored) {}

        if (dummy instanceof LivingEntity) {
            LivingEntity d = (LivingEntity) dummy;

            float desiredNow = risen.getHeadYaw();
            float desiredPrev = getPrevHeadYawSafe(risen, bodyPrev);

            float headNow = clampHeadToBody(bodyNow, desiredNow);
            float headPrev = clampHeadToBody(bodyPrev, desiredPrev);

            try { d.setHeadYaw(headNow); } catch (Throwable ignored) {}

            trySetFloatField(d, LivingEntity.class, "prevHeadYaw", headPrev);
            trySetFloatField(d, LivingEntity.class, "headYaw", headNow);
            trySetFloatField(d, LivingEntity.class, "prevBodyYaw", bodyPrev);
            trySetFloatField(d, LivingEntity.class, "bodyYaw", bodyNow);

            updateLimbAnimatorSafe(d, risen, tickDelta);
        }
    }

    private static void updateLimbAnimatorSafe(LivingEntity dummy, RisenZombieEntity risen, float tickDelta) {
        float speed = (float)Math.sqrt(risen.getVelocity().x * risen.getVelocity().x + risen.getVelocity().z * risen.getVelocity().z);
        float s = MathHelper.clamp(speed * 4.0F, 0.0F, 1.5F);

        try {
            Field f = LivingEntity.class.getDeclaredField("limbAnimator");
            f.setAccessible(true);
            Object limb = f.get(dummy);
            if (limb == null) return;

            if (tryInvoke(limb, "updateLimbs", new Class[]{float.class, float.class, float.class}, new Object[]{s, 1.0F, tickDelta})) return;
            if (tryInvoke(limb, "updateLimbs", new Class[]{float.class, float.class}, new Object[]{s, tickDelta})) return;
            tryInvoke(limb, "updateLimbs", new Class[]{float.class}, new Object[]{s});
        } catch (Throwable ignored) {}
    }

    private static boolean tryInvoke(Object obj, String name, Class<?>[] sig, Object[] args) {
        try {
            Method m = obj.getClass().getMethod(name, sig);
            m.setAccessible(true);
            m.invoke(obj, args);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static float clampHeadToBody(float bodyYaw, float desiredHeadYaw) {
        float diff = MathHelper.wrapDegrees(desiredHeadYaw - bodyYaw);
        diff = MathHelper.clamp(diff, -85.0F, 85.0F);
        return bodyYaw + diff;
    }

    private static float getPrevHeadYawSafe(RisenZombieEntity risen, float fallback) {
        try {
            Field f = LivingEntity.class.getDeclaredField("prevHeadYaw");
            f.setAccessible(true);
            return f.getFloat(risen);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static void trySetFloatField(Object obj, Class<?> owner, String name, float value) {
        try {
            Field f = owner.getDeclaredField(name);
            f.setAccessible(true);
            f.setFloat(obj, value);
        } catch (Throwable ignored) {}
    }

    private static void trySetIntField(Object obj, Class<?> owner, String name, int value) {
        try {
            Field f = owner.getDeclaredField(name);
            f.setAccessible(true);
            f.setInt(obj, value);
        } catch (Throwable ignored) {}
    }

    private void tryRunWalkIdle(Entity dummy, RisenZombieEntity risen) {
        Vec3d v = risen.getVelocity();
        double h2 = v.x * v.x + v.z * v.z;

        byte state;
        if (h2 > 0.06) state = 2;        // run
        else if (h2 > 0.0025) state = 1; // walk
        else state = 0;                  // idle

        int key = risen.getId();
        byte prev = lastMoveState.getOrDefault(key, (byte)-1);
        if (prev == state) return;
        lastMoveState.put(key, state);

        Method m;
        try { m = dummy.getClass().getMethod("triggerAnim", String.class, String.class); }
        catch (Throwable ignored) { return; }

        String[] controllers = new String[] { "controller", "main", "movement", "move" };

        if (state == 2) {
            if (tryTrigger(m, dummy, controllers, "run")) return;
            if (tryTrigger(m, dummy, controllers, "walk")) return;
            tryTrigger(m, dummy, controllers, "idle");
            return;
        }
        if (state == 1) {
            if (tryTrigger(m, dummy, controllers, "walk")) return;
            tryTrigger(m, dummy, controllers, "idle");
            return;
        }
        tryTrigger(m, dummy, controllers, "idle");
    }

    private static boolean tryTrigger(Method m, Entity dummy, String[] controllers, String anim) {
        for (String c : controllers) {
            try { m.invoke(dummy, c, anim); return true; }
            catch (Throwable ignored) {}
        }
        return false;
    }
}
