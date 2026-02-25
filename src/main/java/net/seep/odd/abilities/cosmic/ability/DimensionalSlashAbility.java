// src/main/java/net/seep/odd/abilities/cosmic/ability/DimensionalSlashAbility.java
package net.seep.odd.abilities.cosmic.ability;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import net.seep.odd.abilities.cosmic.CosmicNet;
import net.seep.odd.abilities.cosmic.CosmicFxNet;
import net.seep.odd.item.ModItems;
import net.seep.odd.sound.ModSounds;

import java.util.List;

public final class DimensionalSlashAbility {

    private static final int   CHARGE_MAX_TICKS    = 14;
    private static final double RANGE_MIN          = 6.0;
    private static final double RANGE_MAX          = 14.0;
    private static final double LINE_RADIUS        = 1.6;
    private static final float  DAMAGE_MIN         = 7.0f;
    private static final float  DAMAGE_MAX         = 16.0f;
    private static final double KNOCKBACK_STRENGTH = 0.65;
    private static final int    PARTICLE_STEPS     = 22;

    private static final double MIN_TRAVEL = 1.25;

    private static boolean hasKatana(ServerPlayerEntity p) {
        return p.getMainHandStack().isOf(ModItems.COSMIC_KATANA) || p.getOffHandStack().isOf(ModItems.COSMIC_KATANA);
    }

    public void beginCharge(ServerPlayerEntity p) {
        if (!(p.getWorld() instanceof ServerWorld sw)) return;

        // if you somehow started charge without katana, don't do cues
        if (!hasKatana(p)) return;

        sw.playSound(null, p.getX(), p.getY(), p.getZ(),
                ModSounds.COSMIC_PREPARE, SoundCategory.PLAYERS, 1f, 1.0f);
    }

    public void releaseAndSlash(ServerPlayerEntity p, int heldTicks) {
        if (!(p.getWorld() instanceof ServerWorld sw)) return;

        // server-authoritative gate
        if (!hasKatana(p)) return;

        float t      = MathHelper.clamp(heldTicks / (float)CHARGE_MAX_TICKS, 0.0f, 1.0f);
        double range = MathHelper.lerp(t, RANGE_MIN, RANGE_MAX);
        float dmg    = (float)MathHelper.lerp(t, DAMAGE_MIN, DAMAGE_MAX);

        Vec3d startEye = p.getEyePos();
        Vec3d look  = p.getRotationVec(1.0f).normalize();
        Vec3d endWanted = startEye.add(look.multiply(range));

        BlockHitResult bhr = p.getWorld().raycast(new RaycastContext(
                startEye, endWanted, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));

        Vec3d end = (bhr.getType() == HitResult.Type.BLOCK)
                ? bhr.getPos().subtract(look.multiply(0.35))
                : endWanted;

        Vec3d safeFeet = findNearestSafeFeetPos(sw, p, end, look, 0.6, 14);
        if (safeFeet.squaredDistanceTo(p.getPos()) < (MIN_TRAVEL * MIN_TRAVEL)) {
            Vec3d nudged = p.getPos().add(look.multiply(MIN_TRAVEL));
            Vec3d again  = findNearestSafeFeetPos(sw, p, nudged, look, 0.4, 10);
            if (again.squaredDistanceTo(p.getPos()) >= (0.6 * 0.6)) safeFeet = again;
        }

        slashLineDamage(sw, p, startEye, safeFeet, look, dmg, LINE_RADIUS);
        drawLineParticles(sw, startEye, safeFeet);

        sw.playSound(null, p.getX(), p.getY(), p.getZ(),
                SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.8f, 1.4f);
        sw.playSound(null, safeFeet.x, safeFeet.y, safeFeet.z,
                ModSounds.COSMIC_DASH, SoundCategory.PLAYERS, 1.0f, 1.0f);

        // Satin world trail (true dash path)
        Vec3d endEye = safeFeet.add(0.0, p.getStandingEyeHeight(), 0.0);
        float trailRadius = 0.85f + 0.03f * heldTicks;
        int trailDur = 6 + (heldTicks / 2);
        CosmicFxNet.broadcastDashTrail(p, startEye, endEye, trailRadius, trailDur);

        // Blink
        p.fallDistance = 0;
        p.setVelocity(0, 0, 0);
        p.networkHandler.requestTeleport(safeFeet.x, safeFeet.y, safeFeet.z, p.getYaw(), p.getPitch());

        CosmicNet.sendRift(sw, startEye, safeFeet, 20 * 8);
    }

    /* ------------ internals ------------ */

    private static void slashLineDamage(ServerWorld sw, ServerPlayerEntity src,
                                        Vec3d a, Vec3d b, Vec3d dir, float damage, double radius) {
        Box lane = new Box(a, b).expand(radius, radius, radius);
        List<Entity> candidates = sw.getOtherEntities(src, lane, e ->
                e.isAlive() && e.isAttackable() && e != src);

        if (candidates.isEmpty()) return;

        List<LivingEntity> hits = new ObjectArrayList<>(candidates.size());
        for (Entity e : candidates) {
            if (e instanceof LivingEntity le) {
                if (distSqPointToSegment(le.getPos(), a, b) <= (radius * radius)) hits.add(le);
            }
        }
        for (LivingEntity le : hits) {
            le.damage(sw.getDamageSources().playerAttack(src), damage);
            Vec3d kb = dir.normalize().multiply(KNOCKBACK_STRENGTH).add(0, 0.05, 0);
            le.addVelocity(kb.x, kb.y, kb.z);
            le.velocityModified = true;
        }
    }

    private static void drawLineParticles(ServerWorld sw, Vec3d a, Vec3d b) {
        Vec3d delta = b.subtract(a);
        for (int i = 0; i <= PARTICLE_STEPS; i++) {
            double t = i / (double) PARTICLE_STEPS;
            Vec3d p = a.add(delta.multiply(t));
            sw.spawnParticles(ParticleTypes.REVERSE_PORTAL, p.x, p.y + 0.05, p.z, 2, 0.02, 0.02, 0.02, 0.0);
        }
    }

    private static Vec3d findNearestSafeFeetPos(ServerWorld sw, ServerPlayerEntity p, Vec3d target, Vec3d look,
                                                double stepBack, int maxSteps) {
        if (isSpaceOk(sw, p, target)) return target;
        Vec3d back = look.normalize().multiply(-stepBack);
        Vec3d cur = target;
        for (int i = 0; i < maxSteps; i++) {
            cur = cur.add(back);
            if (isSpaceOk(sw, p, cur)) return cur;
        }
        return p.getPos();
    }

    private static boolean isSpaceOk(ServerWorld sw, ServerPlayerEntity p, Vec3d feet) {
        var bb = p.getBoundingBox().offset(feet.subtract(p.getPos())).expand(1.0E-3);
        return sw.isSpaceEmpty(null, bb);
    }

    private static double distSqPointToSegment(Vec3d p, Vec3d a, Vec3d b) {
        Vec3d ab = b.subtract(a);
        Vec3d ap = p.subtract(a);
        double ab2 = ab.lengthSquared();
        if (ab2 <= 1.0e-8) return ap.lengthSquared();
        double t = MathHelper.clamp(ap.dotProduct(ab) / ab2, 0.0, 1.0);
        Vec3d proj = a.add(ab.multiply(t));
        return p.squaredDistanceTo(proj);
    }
}