// FILE: src/main/java/net/seep/odd/abilities/wizard/WizardCasting.java
package net.seep.odd.abilities.wizard;

import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import net.seep.odd.abilities.power.WizardPower;
import net.seep.odd.abilities.wizard.entity.*;
import net.seep.odd.entity.ModEntities;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class WizardCasting {
    private WizardCasting() {}

    // ---- Costs ----
    public static final float COST_FIRE_NORMAL  = 10f;
    public static final float COST_FIRE_BIG     = 30f;

    public static final float COST_WATER_NORMAL = 20f;
    public static final float COST_WATER_BIG    = 30f;

    public static final float COST_AIR_NORMAL   = 20f;
    public static final float COST_AIR_BIG      = 40f;

    public static final float COST_EARTH_NORMAL = 10f;
    public static final float COST_EARTH_BIG    = 40f;

    public static final double BIG_TARGET_RANGE = 48.0;

    public static int chargeTicksFor(WizardElement e) {
        return switch (e) {
            case FIRE  -> 20 * 2;
            case WATER -> 20 * 1;
            case AIR   -> 20 * 3;
            case EARTH -> 20 * 3;
        };
    }

    public static void castNormal(ServerPlayerEntity p) {
        if (!(p.getWorld() instanceof ServerWorld sw)) return;

        WizardElement e = WizardPower.getElement(p);
        float cost = switch (e) {
            case FIRE  -> COST_FIRE_NORMAL;
            case WATER -> COST_WATER_NORMAL;
            case AIR   -> COST_AIR_NORMAL;
            case EARTH -> COST_EARTH_NORMAL;
        };

        if (!trySpendMana(p, cost)) return;

        switch (e) {
            case FIRE  -> spawnFireProjectile(sw, p);
            case WATER -> spawnWaterProjectile(sw, p);
            case EARTH -> spawnEarthProjectile(sw, p);
            case AIR   -> doAirWindX(sw, p);
        }
    }

    public static void castBig(ServerPlayerEntity p) {
        if (!(p.getWorld() instanceof ServerWorld sw)) return;

        Vec3d at = raycastCirclePos(p, BIG_TARGET_RANGE);
        if (at == null) {
            at = p.getPos().add(p.getRotationVector().normalize().multiply(6.0)).add(0, 0.01, 0);
        }

        castBigAt(p, at);
    }

    public static void castBigAt(ServerPlayerEntity p, Vec3d at) {
        if (!(p.getWorld() instanceof ServerWorld sw)) return;

        WizardElement e = WizardPower.getElement(p);
        float cost = switch (e) {
            case FIRE  -> COST_FIRE_BIG;
            case WATER -> COST_WATER_BIG;
            case AIR   -> COST_AIR_BIG;
            case EARTH -> COST_EARTH_BIG;
        };
        if (!trySpendMana(p, cost)) return;

        switch (e) {
            case FIRE  -> doFireBigAt(sw, p, at);
            case WATER -> doWaterBigAt(sw, p, at);
            case AIR   -> spawnTornadoAt(sw, p, at, false);
            case EARTH -> spawnEarthquakeAt(sw, p, at);
        }
    }

    public static void castComboAt(ServerPlayerEntity p, WizardCombo combo, Vec3d at) {
        if (!(p.getWorld() instanceof ServerWorld sw)) return;

        switch (combo) {
            case STEAM_CLOUD -> {
                if (!trySpendMana(p, 20f)) return;
                spawnSteamCloud(sw, at); // now lightning storm (keeps name)
            }
            case FIRE_TORNADO -> {
                if (!trySpendMana(p, 40f)) return;
                spawnTornadoAt(sw, p, at, true);
            }
            case LIFE_RESTORATION -> {
                if (!trySpendMana(p, 25f)) return;
                doLifeRestoration(sw, p);
            }
            case SONIC_SCREECH -> {
                if (!trySpendMana(p, 35f)) return;
                doSonicScreechUp(sw, p, at);
            }
            case SWAPPERINO -> {
                if (!trySpendMana(p, 30f)) return;
                doSwapperino(sw, p, at);
            }
            case METEOR_STRIKE -> {
                if (!trySpendMana(p, 55f)) return;
                spawnMeteorEntity(sw, p, at);
            }
            default -> p.sendMessage(Text.literal("Unknown combo."), true);
        }
    }

    private static void spawnFireProjectile(ServerWorld sw, ServerPlayerEntity p) {
        WizardFireProjectileEntity proj = new WizardFireProjectileEntity(ModEntities.WIZARD_FIRE_PROJECTILE, p, sw);
        proj.setPosition(p.getX(), p.getEyeY() - 0.1, p.getZ());
        proj.setVelocity(p, p.getPitch(), p.getYaw(), 0.0f, 1.7f, 0.5f);
        sw.spawnEntity(proj);
    }

    private static void spawnWaterProjectile(ServerWorld sw, ServerPlayerEntity p) {
        WizardWaterProjectileEntity proj = new WizardWaterProjectileEntity(ModEntities.WIZARD_WATER_PROJECTILE, p, sw);
        proj.setPosition(p.getX(), p.getEyeY() - 0.1, p.getZ());
        proj.setVelocity(p, p.getPitch(), p.getYaw(), 0.0f, 1.6f, 0.5f);
        sw.spawnEntity(proj);
    }

    private static void spawnEarthProjectile(ServerWorld sw, ServerPlayerEntity p) {
        WizardEarthProjectileEntity proj = new WizardEarthProjectileEntity(ModEntities.WIZARD_EARTH_PROJECTILE, p, sw);
        proj.setPosition(p.getX(), p.getEyeY() - 0.1, p.getZ());
        proj.setVelocity(p, p.getPitch(), p.getYaw(), 0.0f, 1.10f, 0.25f);
        sw.spawnEntity(proj);
    }

    private static void doAirWindX(ServerWorld sw, ServerPlayerEntity p) {
        Vec3d forward = p.getRotationVector().normalize();
        Vec3d up = new Vec3d(0, 1, 0);
        Vec3d right = forward.crossProduct(up).normalize();

        Vec3d center = p.getPos().add(0, 1.1, 0).add(forward.multiply(1.0));

        double range = 1.25;
        Box box = new Box(center, center).expand(1.1, 0.9, 1.1);
        List<LivingEntity> list = sw.getEntitiesByClass(LivingEntity.class, box, e -> e.isAlive() && e != p);

        for (LivingEntity le : list) {
            Vec3d to = le.getPos().add(0, le.getHeight() * 0.5, 0)
                    .subtract(p.getPos().add(0, 1.1, 0));
            double d = to.length();
            if (d > range) continue;

            double dot = to.normalize().dotProduct(forward);
            if (dot < 0.15) continue;

            le.damage(sw.getDamageSources().magic(), 6.0f);
            Vec3d kb = forward.multiply(0.85).add(0, 0.35, 0);
            le.addVelocity(kb.x, kb.y, kb.z);
            le.velocityModified = true;
        }

        for (int i = 0; i < 6; i++) {
            float t = (i / 5f) - 0.5f;
            Vec3d d1 = right.multiply(t * 0.9).add(up.multiply(t * 0.55));
            Vec3d d2 = right.multiply(t * 0.9).add(up.multiply(-t * 0.55));

            sw.spawnParticles(net.minecraft.particle.ParticleTypes.SWEEP_ATTACK,
                    center.x + d1.x, center.y + d1.y, center.z + d1.z,
                    1, 0, 0, 0, 0);
            sw.spawnParticles(net.minecraft.particle.ParticleTypes.SWEEP_ATTACK,
                    center.x + d2.x, center.y + d2.y, center.z + d2.z,
                    1, 0, 0, 0, 0);
        }

        sw.playSound(null, p.getX(), p.getY(), p.getZ(),
                SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.7f, 1.25f);
    }

    private static void doFireBigAt(ServerWorld sw, ServerPlayerEntity p, Vec3d at) {
        sw.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.0f, 1.15f);
        sw.spawnParticles(net.minecraft.particle.ParticleTypes.FLAME, at.x, at.y + 0.35, at.z,
                120, 1.4, 0.7, 1.4, 0.02);

        sw.createExplosion(null, at.x, at.y, at.z, 1.8f, World.ExplosionSourceType.NONE);

        BlockPos base = BlockPos.ofFloored(at.x, at.y - 0.5, at.z);
        int r = 5;

        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            BlockPos pos = base.add(dx, 0, dz);
            if (pos.getSquaredDistance(base) > r * r) continue;

            BlockPos above = pos.up();
            if (!sw.getBlockState(above).isAir()) continue;
            if (sw.getBlockState(pos).isAir()) continue;

            if (Blocks.FIRE.getDefaultState().canPlaceAt(sw, above)) {
                sw.setBlockState(above, Blocks.FIRE.getDefaultState(), 3);
            }
        }
    }

    // ✅ UPDATED: ice cube around entity, proportional + animated
    private static void doWaterBigAt(ServerWorld sw, ServerPlayerEntity p, Vec3d at) {
        LivingEntity target = raycastLiving(p, 32.0);

        if (target != null) {
            target.setFrozenTicks(220);
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 90, 4, false, true, true));
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 90, 1, false, true, true));

            WizardTempEntities.spawnIceCube(sw, target, 90);
        } else {
            float rad = 3.0f;
            Box box = new Box(at.x - rad, at.y - 1.0, at.z - rad, at.x + rad, at.y + 3.0, at.z + rad);
            List<LivingEntity> list = sw.getEntitiesByClass(LivingEntity.class, box, e -> e.isAlive() && e != p);
            for (LivingEntity le : list) {
                if (le.squaredDistanceTo(at) > rad * rad) continue;
                le.setFrozenTicks(180);
                le.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 70, 3, false, true, true));

                // little extra “ice forming” feel
                WizardTempEntities.spawnIceEruption(sw, le.getPos(), 60);
            }
        }

        sw.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_GLASS_PLACE, SoundCategory.PLAYERS, 1.0f, 1.35f);
        sw.spawnParticles(net.minecraft.particle.ParticleTypes.SNOWFLAKE, at.x, at.y + 0.15, at.z,
                120, 1.6, 0.35, 1.6, 0.02);
        sw.spawnParticles(net.minecraft.particle.ParticleTypes.CLOUD, at.x, at.y + 0.10, at.z,
                60, 1.2, 0.15, 1.2, 0.01);
    }

    private static void spawnTornadoAt(ServerWorld sw, ServerPlayerEntity p, Vec3d at, boolean fire) {
        Vec3d dir = p.getRotationVector().normalize();

        if (!fire) {
            WizardTornadoEntity t = new WizardTornadoEntity(ModEntities.WIZARD_TORNADO, sw);
            t.refreshPositionAndAngles(at.x, at.y, at.z, p.getYaw(), p.getPitch());
            t.setDirection(dir);
            t.setOwnerId(p.getUuid());
            sw.spawnEntity(t);
        } else {
            WizardFireTornadoEntity t = new WizardFireTornadoEntity(ModEntities.WIZARD_FIRE_TORNADO, sw);
            t.refreshPositionAndAngles(at.x, at.y, at.z, p.getYaw(), p.getPitch());
            t.setDirection(dir);
            t.setOwnerId(p.getUuid());
            sw.spawnEntity(t);
        }
    }

    private static void spawnEarthquakeAt(ServerWorld sw, ServerPlayerEntity p, Vec3d at) {
        WizardEarthquakeEntity eq = new WizardEarthquakeEntity(ModEntities.WIZARD_EARTHQUAKE, sw);
        eq.refreshPositionAndAngles(at.x, at.y, at.z, 0f, 0f);
        eq.setOwnerId(p.getUuid());
        sw.spawnEntity(eq);
    }

    // ✅ UPDATED: "Smoke Cloud" (Steam Cloud) now = 5 lightning strikes
    private static void spawnSteamCloud(ServerWorld sw, Vec3d pos) {
        BlockPos base = BlockPos.ofFloored(pos);
        int r = 3;

        sw.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 1.0f, 1.0f);
        sw.spawnParticles(net.minecraft.particle.ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y + 0.25, pos.z,
                40, 1.2, 0.4, 1.2, 0.02);

        for (int i = 0; i < 5; i++) {
            int ox = sw.random.nextInt(r * 2 + 1) - r;
            int oz = sw.random.nextInt(r * 2 + 1) - r;

            BlockPos p = base.add(ox, 0, oz);

            LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(sw);
            if (bolt == null) continue;

            bolt.refreshPositionAfterTeleport(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
            sw.spawnEntity(bolt);
        }
    }

    private static void doLifeRestoration(ServerWorld sw, ServerPlayerEntity p) {
        int r = 12;

        sw.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_TOTEM_USE, SoundCategory.PLAYERS, 1.0f, 1.0f);
        sw.spawnParticles(net.minecraft.particle.ParticleTypes.TOTEM_OF_UNDYING,
                p.getX(), p.getY() + 1.0, p.getZ(),
                140, 1.2, 0.9, 1.2, 0.02);

        Box box = p.getBoundingBox().expand(r, 5, r);
        List<LivingEntity> list = sw.getEntitiesByClass(LivingEntity.class, box, e -> e.isAlive());
        for (LivingEntity le : list) {
            if (le.squaredDistanceTo(p) > r * r) continue;
            if (!p.canSee(le)) continue;
            le.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 1, false, true, true));
            le.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 200, 0, false, true, true));
        }
    }

    private static void doSonicScreechUp(ServerWorld sw, ServerPlayerEntity p, Vec3d at) {
        sw.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 1.0f, 1.15f);
        sw.spawnParticles(net.minecraft.particle.ParticleTypes.SONIC_BOOM, at.x, at.y + 0.25, at.z, 1, 0, 0, 0, 0);

        float radius = 3.5f;
        Box box = new Box(at.x - radius, at.y - 1.0, at.z - radius, at.x + radius, at.y + 4.0, at.z + radius);
        List<LivingEntity> list = sw.getEntitiesByClass(LivingEntity.class, box, e -> e.isAlive());

        for (LivingEntity le : list) {
            if (le == p) continue;
            if (le.squaredDistanceTo(at) > radius * radius) continue;

            le.damage(sw.getDamageSources().magic(), 12.0f);
            le.addVelocity(0.0, 1.35, 0.0);
            le.velocityModified = true;
        }
    }

    // ✅ UPDATED: Swapperino restrictions + safe +1 teleport
    private static void doSwapperino(ServerWorld sw, ServerPlayerEntity p, Vec3d at) {
        double r = 3.0;

        Vec3d aCenter = p.getPos();
        Vec3d bCenter = at;
        Vec3d delta = bCenter.subtract(aCenter);

        Box boxA = new Box(aCenter.x - r, aCenter.y - 2, aCenter.z - r, aCenter.x + r, aCenter.y + 3, aCenter.z + r);
        Box boxB = new Box(bCenter.x - r, bCenter.y - 2, bCenter.z - r, bCenter.x + r, bCenter.y + 3, bCenter.z + r);

        // Only living entities that can die + max hp <= 60
        List<LivingEntity> listA = sw.getEntitiesByClass(LivingEntity.class, boxA, WizardCasting::canSwapperinoTeleport);
        List<LivingEntity> listB = sw.getEntitiesByClass(LivingEntity.class, boxB, WizardCasting::canSwapperinoTeleport);

        Set<UUID> aIds = new HashSet<>();
        aIds.add(p.getUuid()); // player always swaps (spell still works without entity requirement)
        for (LivingEntity e : listA) aIds.add(e.getUuid());

        Set<UUID> bIds = new HashSet<>();
        for (LivingEntity e : listB) bIds.add(e.getUuid());

        for (UUID id : aIds) {
            Entity e = sw.getEntity(id);
            if (e == null) continue;
            Vec3d np = e.getPos().add(delta);
            teleportEntity(sw, e, np.x, np.y, np.z);
        }

        for (UUID id : bIds) {
            if (aIds.contains(id)) continue;
            Entity e = sw.getEntity(id);
            if (e == null) continue;
            Vec3d np = e.getPos().subtract(delta);
            teleportEntity(sw, e, np.x, np.y, np.z);
        }

        sw.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.35f);
        sw.spawnParticles(net.minecraft.particle.ParticleTypes.PORTAL, at.x, at.y + 1.0, at.z, 90, 1.2, 0.8, 1.2, 0.15);
        sw.spawnParticles(net.minecraft.particle.ParticleTypes.PORTAL, p.getX(), p.getY() + 1.0, p.getZ(), 90, 1.2, 0.8, 1.2, 0.15);
    }

    private static boolean canSwapperinoTeleport(LivingEntity le) {
        if (le == null || !le.isAlive()) return false;
        if (le.isInvulnerable()) return false;

        // players in creative/spectator should not be yoinked
        if (le instanceof ServerPlayerEntity sp) {
            if (sp.isCreative() || sp.isSpectator()) return false;
        }

        // hp limit
        return le.getMaxHealth() <= 60.0f;
    }

    private static Vec3d findSafeAbove(ServerWorld sw, Entity e, double x, double y, double z) {
        // ensure at least +1 block so big mobs don’t sink into ground
        double baseY = Math.floor(y) + 1.05;

        var dims = e.getDimensions(e.getPose());
        for (int i = 0; i < 8; i++) {
            double yy = baseY + i;
            Box box = dims.getBoxAt(x, yy, z);
            if (sw.isSpaceEmpty(e, box)) {
                return new Vec3d(x, yy, z);
            }
        }

        // fallback
        return new Vec3d(x, y + 1.2, z);
    }

    private static void spawnMeteorEntity(ServerWorld sw, ServerPlayerEntity p, Vec3d at) {
        sw.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.8f, 1.6f);

        WizardMeteorEntity m = new WizardMeteorEntity(ModEntities.WIZARD_METEOR, sw);
        m.setOwnerId(p.getUuid());
        m.setTarget(at);

        m.refreshPositionAndAngles(at.x, at.y + 42.0, at.z, 0f, 0f);
        sw.spawnEntity(m);
    }

    // ✅ UPDATED: uses safe +1 teleport positioning
    private static void teleportEntity(ServerWorld sw, Entity e, double x, double y, double z) {
        Vec3d safe = findSafeAbove(sw, e, x, y, z);

        if (e instanceof ServerPlayerEntity sp) {
            sp.teleport(sw, safe.x, safe.y, safe.z, sp.getYaw(), sp.getPitch());
            sp.setVelocity(Vec3d.ZERO);
        } else {
            e.requestTeleport(safe.x, safe.y, safe.z);
            e.setVelocity(Vec3d.ZERO);
        }
    }

    public static CapybaraFamiliarEntity getOrEnsureFamiliar(ServerWorld sw, ServerPlayerEntity owner) {
        UUID id = owner.getUuid();

        Box box = owner.getBoundingBox().expand(96.0);
        List<CapybaraFamiliarEntity> list =
                sw.getEntitiesByClass(CapybaraFamiliarEntity.class, box, e -> e.isAlive() && id.equals(e.getOwnerUuidSafe()));

        if (!list.isEmpty()) {
            for (int i = 1; i < list.size(); i++) list.get(i).discard();
            return list.get(0);
        }

        CapybaraFamiliarEntity fam = new CapybaraFamiliarEntity(ModEntities.CAPYBARA_FAMILIAR, sw);
        fam.refreshPositionAndAngles(owner.getX(), owner.getY(), owner.getZ(), owner.getYaw(), owner.getPitch());
        fam.setOwnerUuid(id);
        sw.spawnEntity(fam);
        return fam;
    }

    public static void despawnFamiliar(ServerWorld sw, UUID ownerId) {
        BlockPos spawn = sw.getSpawnPos();
        Box box = new Box(spawn).expand(512.0);

        List<CapybaraFamiliarEntity> list =
                sw.getEntitiesByClass(CapybaraFamiliarEntity.class, box, e -> ownerId.equals(e.getOwnerUuidSafe()));

        for (CapybaraFamiliarEntity e : list) e.discard();
    }

    public static LivingEntity raycastLiving(ServerPlayerEntity p, double range) {
        Vec3d start = p.getCameraPosVec(1f);
        Vec3d end = start.add(p.getRotationVector().multiply(range));

        Box box = p.getBoundingBox().stretch(p.getRotationVector().multiply(range)).expand(1.0);
        EntityHitResult ehr = ProjectileUtil.raycast(
                p, start, end, box,
                e -> (e instanceof LivingEntity le) && le.isAlive() && e != p,
                range * range
        );

        if (ehr != null && ehr.getEntity() instanceof LivingEntity le) return le;
        return null;
    }

    public static Vec3d raycastPos(ServerPlayerEntity p, double range) {
        Vec3d start = p.getCameraPosVec(1f);
        Vec3d end = start.add(p.getRotationVector().multiply(range));
        HitResult hr = p.getWorld().raycast(new RaycastContext(
                start, end,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                p
        ));
        return hr.getPos();
    }

    private static Vec3d raycastCirclePos(ServerPlayerEntity p, double range) {
        HitResult hr = p.raycast(range, 1.0f, false);
        if (hr.getType() != HitResult.Type.BLOCK) return null;

        BlockPos bp = ((BlockHitResult) hr).getBlockPos();
        return new Vec3d(bp.getX() + 0.5, bp.getY() + 1.01, bp.getZ() + 0.5);
    }

    private static boolean trySpendMana(ServerPlayerEntity p, float cost) {
        float mana = WizardPower.getMana(p);
        if (mana < cost) {
            p.sendMessage(Text.literal("Not enough mana."), true);
            return false;
        }
        WizardPower.setMana(p, mana - cost);
        return true;
    }
}
