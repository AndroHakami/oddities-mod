package net.seep.odd.block.combiner.enchant;

import io.github.fabricators_of_create.porting_lib.entity.events.ProjectileImpactEvent;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class WhirlpoolHarpoonHandler {
    private WhirlpoolHarpoonHandler() {}

    private static boolean installed = false;

    /* ===================== tuning ===================== */

    // how long the vortex lasts
    private static final int DURATION_TICKS = 26;

    // how often to spawn swirl particles
    private static final int SWIRL_PARTICLE_EVERY = 2;

    // pull radius (blocks). 2 meters ~ 4 blocks, but trident feels better slightly larger.
    private static final double PULL_RADIUS = 6.0;

    // ✅ swirl radius reduced by 50% (you asked for this)
    private static final double SWIRL_RADIUS = PULL_RADIUS * 0.5;

    // max health exemption: 50 hearts = 100 HP
    private static final float MAX_HP_EXEMPT = 100.0f;

    // pull strength
    private static final double MAX_PULL = 0.75;

    // small vertical lift so it feels watery, not just a vacuum
    private static final double Y_LIFT_MIN = 0.02;
    private static final double Y_LIFT_MAX = 0.10;

    /* ===================== state ===================== */

    private record Vortex(RegistryKey<World> worldKey,
                          long startTick,
                          long endTick,
                          Vec3d center,
                          UUID owner) {}

    // key by projectile uuid so multiple vortices can exist briefly
    private static final Map<UUID, Vortex> ACTIVE = new LinkedHashMap<>();

    /** Call once during common init AFTER CombinerEnchantments.init(). */
    public static void init() {
        if (installed) return;
        installed = true;

        // PortingLib impact hook (1.20.1): callback is void, arg is ProjectileImpactEvent
        ProjectileImpactEvent.PROJECTILE_IMPACT.register(WhirlpoolHarpoonHandler::onImpact);

        // tick vortices
        ServerTickEvents.END_SERVER_TICK.register(WhirlpoolHarpoonHandler::tickServer);
    }

    private static void onImpact(ProjectileImpactEvent event) {
        // projectile must exist and be a trident
        var proj = event.getProjectile();
        if (!(proj instanceof TridentEntity trident)) return;

        World w = trident.getWorld();
        if (!(w instanceof ServerWorld sw)) return;

        // must hit an entity (not a block)
        HitResult hit = event.getRayTraceResult();
        if (!(hit instanceof EntityHitResult ehr)) return;

        Entity hitEnt = ehr.getEntity();
        if (!(hitEnt instanceof LivingEntity target) || !target.isAlive()) return;

        // read the thrown trident stack (no mixins) so we can check the enchant
        ItemStack tridentStack = getTridentStackSafe(trident);
        if (tridentStack.isEmpty()) return;

        if (CombinerEnchantments.TIDE == null) return;
        int lvl = EnchantmentHelper.getLevel(CombinerEnchantments.TIDE, tridentStack);
        if (lvl <= 0) return;

        // vortex center around the HIT target mid-body
        Vec3d center = target.getPos().add(0.0, target.getHeight() * 0.55, 0.0);

        UUID owner = null;
        Entity o = trident.getOwner();
        if (o != null) owner = o.getUuid();

        long now = sw.getTime();

        // start: quick splash pop
        spawnStart(sw, center);

        // register / refresh vortex for this trident
        ACTIVE.put(trident.getUuid(), new Vortex(
                sw.getRegistryKey(),
                now,
                now + DURATION_TICKS,
                center,
                owner
        ));
    }

    private static void tickServer(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;

        Iterator<Map.Entry<UUID, Vortex>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            Vortex v = e.getValue();

            ServerWorld sw = server.getWorld(v.worldKey);
            if (sw == null) { it.remove(); continue; }

            long now = sw.getTime();

            // end -> burst + sound
            if (now >= v.endTick) {
                spawnBurst(sw, v.center);
                sw.playSound(
                        null,
                        BlockPos.ofFloored(v.center),
                        SoundEvents.ENTITY_PLAYER_SPLASH_HIGH_SPEED,
                        SoundCategory.PLAYERS,
                        0.75f, 1.05f
                );
                it.remove();
                continue;
            }

            // swirl visuals
            if (((now - v.startTick) % SWIRL_PARTICLE_EVERY) == 0) {
                spawnSwirl(sw, v.center, (int)(now - v.startTick));
            }

            // pull entities
            applyPull(sw, v, now);
        }
    }

    /* ===================== pulling ===================== */

    private static void applyPull(ServerWorld sw, Vortex v, long now) {
        Box box = new Box(
                v.center.x - PULL_RADIUS, v.center.y - 2.0, v.center.z - PULL_RADIUS,
                v.center.x + PULL_RADIUS, v.center.y + 2.0, v.center.z + PULL_RADIUS
        );

        ServerPlayerEntity ownerPlayer = (v.owner != null) ? sw.getServer().getPlayerManager().getPlayer(v.owner) : null;

        var targets = sw.getEntitiesByClass(LivingEntity.class, box, ent ->
                ent.isAlive()
                        && !ent.isSpectator()
                        && (v.owner == null || !ent.getUuid().equals(v.owner))  // don't pull owner
        );

        if (targets.isEmpty()) return;

        for (LivingEntity le : targets) {
            // team-safe (if owner is a player)
            if (ownerPlayer != null && le.isTeammate(ownerPlayer)) continue;

            // exempt big max-hp mobs (>= 50 hearts)
            if (le.getMaxHealth() >= MAX_HP_EXEMPT) continue;

            Vec3d pos = le.getPos().add(0.0, le.getHeight() * 0.45, 0.0);
            Vec3d toCenter = v.center.subtract(pos);

            double dist = toCenter.length();
            if (dist <= 0.0001 || dist > PULL_RADIUS) continue;

            double falloff = 1.0 - (dist / PULL_RADIUS);
            falloff = MathHelper.clamp(falloff, 0.0, 1.0);

            double strength = MAX_PULL * falloff;
            Vec3d dir = toCenter.normalize();

            double yLift = MathHelper.lerp(falloff, Y_LIFT_MIN, Y_LIFT_MAX);

            le.addVelocity(dir.x * strength, yLift, dir.z * strength);
            le.velocityModified = true;

            // keep it from turning into “bonus fall damage”
            le.fallDistance = 0.0f;
        }
    }

    /* ===================== visuals ===================== */

    private static void spawnStart(ServerWorld sw, Vec3d c) {
        sw.spawnParticles(ParticleTypes.SPLASH,
                c.x, c.y, c.z,
                10,
                0.25, 0.10, 0.25,
                0.02);
        sw.spawnParticles(ParticleTypes.BUBBLE_POP,
                c.x, c.y, c.z,
                6,
                0.18, 0.10, 0.18,
                0.01);
    }

    private static void spawnSwirl(ServerWorld sw, Vec3d c, int t) {
        // smooth swirl with a few points (kept light)
        int points = 12;
        double baseAng = t * 0.55;

        for (int i = 0; i < points; i++) {
            double ang = baseAng + (i / (double) points) * Math.PI * 2.0;

            // small radial wobble so it feels like water
            double r = SWIRL_RADIUS * (0.78 + 0.12 * Math.sin(ang * 1.8 + t * 0.2));

            double x = c.x + Math.cos(ang) * r;
            double z = c.z + Math.sin(ang) * r;

            double y = c.y + 0.10 * Math.sin(ang * 1.4 + t * 0.35);

            // tiny tangential drift
            double vx = -Math.sin(ang) * 0.02;
            double vz =  Math.cos(ang) * 0.02;

            sw.spawnParticles(ParticleTypes.BUBBLE, x, y, z, 1, vx, 0.01, vz, 0.0);

            // sprinkle a few splashes, not too dense
            if ((i % 3) == 0) {
                sw.spawnParticles(ParticleTypes.SPLASH, x, y, z, 1, 0.02, 0.01, 0.02, 0.0);
            }
        }
    }

    private static void spawnBurst(ServerWorld sw, Vec3d c) {
        sw.spawnParticles(ParticleTypes.SPLASH,
                c.x, c.y, c.z,
                28,
                0.45, 0.25, 0.45,
                0.05);

        sw.spawnParticles(ParticleTypes.BUBBLE_POP,
                c.x, c.y, c.z,
                18,
                0.35, 0.20, 0.35,
                0.03);
    }

    /* ===================== stack access (no mixins) ===================== */

    /**
     * TridentEntity stores the thrown ItemStack internally.
     * In Yarn 1.20.1, its accessor is protected, so we use reflection.
     */
    private static ItemStack getTridentStackSafe(TridentEntity trident) {
        try {
            Class<?> c = trident.getClass();
            while (c != null && c != Object.class) {
                for (Field f : c.getDeclaredFields()) {
                    if (f.getType() == ItemStack.class) {
                        f.setAccessible(true);
                        Object v = f.get(trident);
                        if (v instanceof ItemStack s) return s;
                    }
                }
                c = c.getSuperclass();
            }
        } catch (Throwable ignored) {}
        return ItemStack.EMPTY;
    }
}