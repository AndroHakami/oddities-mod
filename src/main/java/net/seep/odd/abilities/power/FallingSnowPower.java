// src/main/java/net/seep/odd/abilities/power/FallingSnowPower.java
package net.seep.odd.abilities.power;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import net.seep.odd.Oddities;
import net.seep.odd.abilities.fallingsnow.BigSnowballEntity;
import net.seep.odd.abilities.fallingsnow.FallingSnowClient;
import net.seep.odd.abilities.fallingsnow.FallingSnowNet;
import net.seep.odd.abilities.fallingsnow.HealingSnowballEntity;
import net.seep.odd.abilities.fallingsnow.OrbitingSnowballEntity;

public final class FallingSnowPower implements Power, ChargedPower, HoldReleasePower {

    /* ===== tuning ===== */
    private static final double BLINK_RANGE = 6.0;
    private static final int PRIMARY_MAX_CHARGES = 2;
    private static final int PRIMARY_RECHARGE_TICKS = 160; // ~8s per pip

    private static final float LANDING_DAMAGE = 8.0f; // 4♥, armor-respecting
    private static final int BIG_MIN_HOLD_TICKS = 20; // 1s
    private static final float BIG_DAMAGE = 10.0f;    // 5♥, armor-respecting
    private static final int BIG_SLOWNESS_TICKS = 100; // 5s
    private static final int BIG_SLOWNESS_LEVEL = 1;   // Slowness II
    private static final float HEAL_AMOUNT = 4.0f;     // 2♥

    @Override public String id() { return "fallingsnow"; }
    @Override public boolean hasSlot(String slot) { return "primary".equals(slot) || "secondary".equals(slot); }

    @Override public long cooldownTicks() { return 0; }
    @Override public long secondaryCooldownTicks() { return 40; }
    @Override public long thirdCooldownTicks() { return 0; }

    @Override
    public Identifier iconTexture(String slot) {
        return switch (slot) {
            case "primary"   -> new Identifier(Oddities.MOD_ID, "textures/gui/abilities/fallingsnow_blink.png");
            case "secondary" -> new Identifier(Oddities.MOD_ID, "textures/gui/abilities/fallingsnow_snowball.png");
            default          -> new Identifier(Oddities.MOD_ID, "textures/gui/abilities/ability_default.png");
        };
    }

    @Override public String longDescription() {
        return "Blink a short distance (2 charges). Tap to toss a healing snowball, or charge to launch a heavy snowball that knocks back and slows enemies.";
    }

    @Override public String slotLongDescription(String slot) {
        return switch (slot) {
            case "primary"   -> "Blink ~6 blocks. Landing on a target deals damage. Two charges that refill over time.";
            case "secondary" -> "Hold to charge. Tap: heal allies (+2♥). Charged: 5♥ damage, Slowness, strong knockback.";
            default -> "";
        };
    }

    @Override public Identifier portraitTexture() { return new Identifier(Oddities.MOD_ID, "textures/gui/overview/fallingsnow.png"); }

    /* ===== charges for primary ===== */
    @Override public boolean usesCharges(String slot) { return "primary".equals(slot); }
    @Override public int maxCharges(String slot)      { return "primary".equals(slot) ? PRIMARY_MAX_CHARGES : 0; }
    @Override public long rechargeTicks(String slot)  { return "primary".equals(slot) ? PRIMARY_RECHARGE_TICKS : 0L; }

    /* ===== PRIMARY: blink ===== */
    @Override
    public void activate(ServerPlayerEntity p) {
        ServerWorld sw = (ServerWorld) p.getWorld();

        Vec3d eye  = p.getCameraPosVec(1.0f);
        Vec3d look = p.getRotationVec(1.0f);
        Vec3d end  = eye.add(look.multiply(BLINK_RANGE));

        HitResult blockHit = sw.raycast(new RaycastContext(
                eye, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
        Vec3d pathEnd = (blockHit.getType() == HitResult.Type.MISS) ? end : blockHit.getPos();

        EntityHitResult entHit = raycastEntity(sw, p, eye, pathEnd, 0.4f);

        Vec3d targetPos = pathEnd;
        LivingEntity landedOn = null;
        if (entHit != null && entHit.getEntity() instanceof LivingEntity le && le.isAlive()) {
            landedOn = le;
            targetPos = entHit.getPos().subtract(look.multiply(0.6));
        }

        Vec3d safe = findSafeStandPos(sw, targetPos);
        if (safe == null) {
            p.sendMessage(Text.literal("No safe spot."), true);
            sw.playSound(null, p.getBlockPos(), SoundEvents.BLOCK_SNOW_BREAK, SoundCategory.PLAYERS, 0.8f, 0.8f);
            return;
        }

        spawnSnowTrail(sw, eye, safe);
        sw.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.9f, 1.25f);
        p.teleport(sw, safe.x, safe.y, safe.z, p.getYaw(), p.getPitch());

        if (landedOn != null && landedOn.isAlive() && landedOn.squaredDistanceTo(p) < 2.0) {
            landedOn.damage(sw.getDamageSources().playerAttack(p), LANDING_DAMAGE);
            Vec3d dir = new Vec3d(landedOn.getX() - p.getX(), 0, landedOn.getZ() - p.getZ()).normalize();
            landedOn.addVelocity(dir.x * 0.4, 0.1, dir.z * 0.4);
            landedOn.velocityModified = true;

            sw.spawnParticles(ParticleTypes.SNOWFLAKE, landedOn.getX(), landedOn.getBodyY(0.5), landedOn.getZ(), 12, 0.2,0.2,0.2,0.01);
            sw.playSound(null, landedOn.getBlockPos(), SoundEvents.BLOCK_SNOW_HIT, SoundCategory.PLAYERS, 1.0f, 1.2f);
        }

        FallingSnowNet.s2cPingCharges(p); // HUD nudge (optional)
    }

    /* ===== SECONDARY: hold → release with front “snow core” ===== */

    @Override
    public void onHoldStart(ServerPlayerEntity p, String slot) {
        if (!"secondary".equals(slot)) return;
        ServerWorld sw = (ServerWorld) p.getWorld();

        // spawn a single front-facing “core” if not present
        if (findOrbiting(sw, p) == null) {
            OrbitingSnowballEntity core = new OrbitingSnowballEntity(sw, p);
            sw.spawnEntity(core);
        }

        sw.playSound(null, p.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 0.7f, 1.2f);
    }

    @Override
    public void onHoldTick(ServerPlayerEntity p, String slot, int heldTicks) {
        if (!"secondary".equals(slot)) return;
        ServerWorld sw = (ServerWorld) p.getWorld();

        // sucking snowflakes into the core
        OrbitingSnowballEntity core = findOrbiting(sw, p);
        if (core != null) {
            Vec3d c = core.getPos();
            // a quick ring of flakes that look like they drift inward
            for (int i = 0; i < 6; i++) {
                double a = (i / 6.0) * Math.PI * 2.0;
                double r = 0.8 + sw.random.nextDouble() * 0.4;
                double x = c.x + Math.cos(a) * r;
                double y = c.y + 0.1 + (sw.random.nextDouble() - 0.5) * 0.2;
                double z = c.z + Math.sin(a) * r;
                sw.spawnParticles(ParticleTypes.SNOWFLAKE, x, y, z, 1,
                        (c.x - x) * 0.2, (c.y - y) * 0.2, (c.z - z) * 0.2, 0.01);
            }

            // flip to BIG at threshold (visual turns to a snow block)
            if (heldTicks == BIG_MIN_HOLD_TICKS) {
                core.setBig(true);
                sw.playSound(null, p.getBlockPos(), SoundEvents.BLOCK_LARGE_AMETHYST_BUD_BREAK, SoundCategory.PLAYERS, 0.9f, 0.9f);
            }
        }
    }

    @Override
    public void onHoldRelease(ServerPlayerEntity p, String slot, int heldTicks, boolean canceled) {
        if (!"secondary".equals(slot) || canceled) return;
        ServerWorld sw = (ServerWorld) p.getWorld();
        boolean big = heldTicks >= BIG_MIN_HOLD_TICKS;

        // remove the visual core if present
        OrbitingSnowballEntity core = findOrbiting(sw, p);
        if (core != null) core.discard();

        p.swingHand(Hand.MAIN_HAND, true); // throw animation

        if (big) {
            BigSnowballEntity bigBall = new BigSnowballEntity(sw, p, BIG_DAMAGE, BIG_SLOWNESS_TICKS, BIG_SLOWNESS_LEVEL, 1.15);
            shootFromPlayer(p, bigBall, 1.25f, 0.999f);
            sw.spawnEntity(bigBall);
            sw.playSound(null, p.getBlockPos(), SoundEvents.BLOCK_SNOW_FALL, SoundCategory.PLAYERS, 1.0f, 0.7f);
        } else {
            HealingSnowballEntity healBall = new HealingSnowballEntity(sw, p, HEAL_AMOUNT);
            shootFromPlayer(p, healBall, 1.1f, 0.999f);
            sw.spawnEntity(healBall);
            sw.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_SNOWBALL_THROW, SoundCategory.PLAYERS, 0.9f, 1.1f);
        }
    }

    /* ===== helpers ===== */

    private static void shootFromPlayer(ServerPlayerEntity p, ThrownItemEntity proj, float velocity, float inaccuracy) {
        Vec3d look = p.getRotationVec(1.0f);
        proj.setPosition(p.getX(), p.getEyeY() - 0.1, p.getZ());
        proj.setVelocity(look.x, look.y + 0.05, look.z, velocity, (float)(inaccuracy * 5.0));
    }

    private static OrbitingSnowballEntity findOrbiting(ServerWorld sw, ServerPlayerEntity owner) {
        var list = sw.getEntitiesByClass(OrbitingSnowballEntity.class,
                owner.getBoundingBox().expand(6.0),
                e -> e.getOwner() == owner);
        return list.isEmpty() ? null : list.get(0);
    }

    private static EntityHitResult raycastEntity(ServerWorld world, Entity shooter, Vec3d start, Vec3d end, float inflate) {
        Vec3d dir = end.subtract(start);
        Box box = shooter.getBoundingBox().stretch(dir).expand(inflate);
        return net.minecraft.entity.projectile.ProjectileUtil.raycast(
                shooter, start, end, box,
                e -> e.isAlive() && e.isAttackable() && !e.isSpectator() && e != shooter, dir.lengthSquared()
        );
    }

    private static Vec3d findSafeStandPos(ServerWorld sw, Vec3d target) {
        BlockPos base = BlockPos.ofFloored(target);
        for (int dy = -1; dy <= 2; dy++) {
            BlockPos p = base.up(dy);
            Box feet = EntityType.PLAYER.getDimensions().getBoxAt(Vec3d.ofCenter(p));
            Box head = feet.offset(0, 1.0, 0);
            if (sw.isSpaceEmpty(null, feet) && sw.isSpaceEmpty(null, head)) {
                return new Vec3d(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
            }
        }
        return null;
    }

    private static void spawnSnowTrail(ServerWorld sw, Vec3d from, Vec3d to) {
        Vec3d d = to.subtract(from);
        int steps = Math.max(4, (int)(d.length() * 8.0));
        for (int i = 0; i <= steps; i++) {
            Vec3d p = from.lerp(to, i / (double)steps);
            sw.spawnParticles(ParticleTypes.SNOWFLAKE, p.x, p.y, p.z, 1, 0.02,0.02,0.02, 0.0);
        }
    }

    /* client bootstrap (HUD + receivers) */
    @Environment(EnvType.CLIENT)
    public static final class Client {
        public static void init() { FallingSnowClient.init(); }
    }
}
