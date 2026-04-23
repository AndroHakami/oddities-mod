// src/main/java/net/seep/odd/block/falseflower/spell/FalseFlowerSpellUtil.java
package net.seep.odd.block.falseflower.spell;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class FalseFlowerSpellUtil {
    private FalseFlowerSpellUtil() {}

    public static boolean insideSphere(Vec3d p, Vec3d c, int r) {
        return p.squaredDistanceTo(c) <= (double) r * (double) r;
    }

    public static int hsvToRgb(float h, float s, float v) {
        h = (h % 1f + 1f) % 1f;
        s = MathHelper.clamp(s, 0f, 1f);
        v = MathHelper.clamp(v, 0f, 1f);

        float c = v * s;
        float x = c * (1f - Math.abs((h * 6f) % 2f - 1f));
        float m = v - c;

        float r1, g1, b1;
        float hh = h * 6f;
        if (hh < 1f)      { r1 = c; g1 = x; b1 = 0; }
        else if (hh < 2f) { r1 = x; g1 = c; b1 = 0; }
        else if (hh < 3f) { r1 = 0; g1 = c; b1 = x; }
        else if (hh < 4f) { r1 = 0; g1 = x; b1 = c; }
        else if (hh < 5f) { r1 = x; g1 = 0; b1 = c; }
        else              { r1 = c; g1 = 0; b1 = x; }

        int r = (int)((r1 + m) * 255f);
        int g = (int)((g1 + m) * 255f);
        int b = (int)((b1 + m) * 255f);

        r = MathHelper.clamp(r, 0, 255);
        g = MathHelper.clamp(g, 0, 255);
        b = MathHelper.clamp(b, 0, 255);
        return (r << 16) | (g << 8) | b;
    }

    /**
     * Dedicated-server safe magic damage.
     *
     * Do NOT use reflection here: reflection method names are not remapped in
     * production jars, which can make the spell work in dev/client but fail on a
     * dedicated server.
     */
    public static boolean damageMagicSafe(ServerWorld w, LivingEntity e, float amt) {
        try {
            DamageSource magic = new DamageSource(
                    w.getRegistryManager()
                            .get(RegistryKeys.DAMAGE_TYPE)
                            .entryOf(DamageTypes.MAGIC)
            );
            return e.damage(magic, amt);
        } catch (Throwable ignored) {}

        try {
            DamageSource generic = new DamageSource(
                    w.getRegistryManager()
                            .get(RegistryKeys.DAMAGE_TYPE)
                            .entryOf(DamageTypes.GENERIC)
            );
            return e.damage(generic, amt);
        } catch (Throwable ignored) {}

        // last-resort fallback so the bullets still work even if damage source creation changes
        if (e.isInvulnerable()) return false;
        e.setHealth(Math.max(0.0f, e.getHealth() - amt));
        return true;
    }

    public static boolean insideSphere(Vec3d p, Vec3d center, double radius) {
        return p.squaredDistanceTo(center) <= (radius * radius);
    }

    /** ✅ One-shot “charge” time: bigger radius => slower activation. */
    public static int activationTicksForRadius(int radiusBlocks) {
        int r = Math.max(1, radiusBlocks);
        // R=6 -> 26 ticks, R=18 -> 50 ticks
        return 14 + (r * 2);
    }

    /** Reflection-safe Pehkui base scale set. */
    public static void setPehkuiBaseScale(net.minecraft.entity.Entity e, float s) {
        try {
            Class<?> scaleTypesCl = Class.forName("virtuoel.pehkui.api.ScaleTypes");
            Object BASE = scaleTypesCl.getField("BASE").get(null);

            Class<?> scaleTypeCl = Class.forName("virtuoel.pehkui.api.ScaleType");
            var getScaleData = scaleTypeCl.getMethod("getScaleData", net.minecraft.entity.Entity.class);

            Class<?> scaleDataCl = Class.forName("virtuoel.pehkui.api.ScaleData");
            var setScale = scaleDataCl.getMethod("setScale", float.class);

            Object data = getScaleData.invoke(BASE, e);
            setScale.invoke(data, s);
        } catch (Throwable ignored) {}
    }

    /**
     * Applies motion in a way that reliably reaches players as well as mobs.
     */
    public static void addVelocitySafe(Entity e, double x, double y, double z) {
        e.setVelocity(e.getVelocity().add(x, y, z));
        e.velocityDirty = true;
        e.velocityModified = true;

        if (e instanceof ServerPlayerEntity sp) {
            sp.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(sp));
        }
    }
}
