// src/main/java/net/seep/odd/block/combiner/enchant/WildRootShotHandler.java
package net.seep.odd.block.combiner.enchant;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * WILD trim -> Crossbow-only "Wild Root Shot"
 * - On crossbow bolt hit: root + slam downward for 0.5s (10 ticks)
 * - No extra fall damage (fallDistance forced to 0)
 * - Does NOT affect mobs with max HP > 50 hearts (100 health)
 *
 * Fabric 1.20.1: uses ServerLivingEntityEvents.ALLOW_DAMAGE (AFTER_DAMAGE isn't available here)
 */
public final class WildRootShotHandler {
    private WildRootShotHandler(){}

    private static boolean installed = false;

    private static final int ROOT_TICKS = 20;           // 0.5s
    private static final int FX_EVERY_T = 2;            // light particles
    private static final float MAX_HP_ALLOWED = 100f;   // 50 hearts

    private record Pending(RegistryKey<World> worldKey, int entityId, UUID entityUuid, int ticksLeft) {}

    // target UUID -> pending root info
    private static final Map<UUID, Pending> ROOTED = new HashMap<>();

    public static void init() {
        if (installed) return;
        installed = true;

        // Trigger on damage (server-side, before application)
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((LivingEntity entity, DamageSource source, float amount) -> {
            if (amount <= 0) return true;
            if (!(entity.getWorld() instanceof ServerWorld sw)) return true;

            LivingEntity target = entity;
            if (!target.isAlive()) return true;

            // skip big max HP mobs
            if (target.getMaxHealth() > MAX_HP_ALLOWED) return true;

            // must be crossbow projectile
            Entity direct = source.getSource();      // ✅ Yarn 1.20.1
            if (!(direct instanceof PersistentProjectileEntity proj)) return true;
            if (!proj.isShotFromCrossbow()) return true;

            // attacker must be living
            Entity atkEnt = source.getAttacker();    // ✅ Yarn 1.20.1
            if (!(atkEnt instanceof LivingEntity attacker)) return true;
            if (attacker == target) return true;

            // must have WILD enchant on a held CROSSBOW (not bow)
            if (CombinerEnchantments.WILD == null) return true;
            if (wildLevelOnHeldCrossbow(attacker) <= 0) return true;

            // apply/refresh root
            ROOTED.put(target.getUuid(), new Pending(
                    sw.getRegistryKey(),
                    target.getId(),
                    target.getUuid(),
                    ROOT_TICKS
            ));

            // immediate slam + tiny burst
            slamAndRootTick(target, true);
            spawnHitFx(sw, target);

            return true; // allow damage through
        });

        ServerTickEvents.END_SERVER_TICK.register(WildRootShotHandler::tickServer);
    }

    private static int wildLevelOnHeldCrossbow(LivingEntity attacker) {
        ItemStack main = attacker.getMainHandStack();
        if (main.getItem() instanceof CrossbowItem) {
            return EnchantmentHelper.getLevel(CombinerEnchantments.WILD, main);
        }
        ItemStack off = attacker.getOffHandStack();
        if (off.getItem() instanceof CrossbowItem) {
            return EnchantmentHelper.getLevel(CombinerEnchantments.WILD, off);
        }
        return 0;
    }

    private static void tickServer(MinecraftServer server) {
        if (ROOTED.isEmpty()) return;

        Iterator<Map.Entry<UUID, Pending>> it = ROOTED.entrySet().iterator();
        while (it.hasNext()) {
            Pending p = it.next().getValue();

            ServerWorld sw = server.getWorld(p.worldKey());
            if (sw == null) { it.remove(); continue; }

            Entity e = sw.getEntityById(p.entityId());
            if (!(e instanceof LivingEntity target) || !target.isAlive() || !target.getUuid().equals(p.entityUuid())) {
                it.remove();
                continue;
            }

            int left = p.ticksLeft();
            if (left <= 0) {
                target.fallDistance = 0.0f;
                it.remove();
                continue;
            }

            // enforce root + slam each tick
            slamAndRootTick(target, false);

            // light particles while rooted
            if ((left % FX_EVERY_T) == 0) spawnTickFx(sw, target);

            // update countdown
            it.remove();
            ROOTED.put(target.getUuid(), new Pending(p.worldKey(), p.entityId(), p.entityUuid(), left - 1));
        }
    }

    private static void slamAndRootTick(LivingEntity target, boolean initial) {
        Vec3d v = target.getVelocity();

        // root horizontal, force downward
        double down = initial ? -2.95 : -2.85;
        double newY = Math.min(v.y, down);

        target.setVelocity(0.0, newY, 0.0);
        target.velocityModified = true;

        // prevent fall damage buildup
        target.fallDistance = 0.0f;
    }

    private static void spawnHitFx(ServerWorld sw, LivingEntity target) {
        Vec3d c = target.getPos().add(0, 0.15, 0);
        sw.spawnParticles(ParticleTypes.COMPOSTER, c.x, c.y, c.z, 10, 0.22, 0.08, 0.22, 0.01);
        sw.spawnParticles(ParticleTypes.FALLING_SPORE_BLOSSOM,      c.x, c.y, c.z,  4, 0.18, 0.10, 0.18, 0.01);
    }

    private static void spawnTickFx(ServerWorld sw, LivingEntity target) {
        Vec3d c = target.getPos().add(0, 0.10, 0);

        // 3 light orbit points around feet
        for (int i = 0; i < 3; i++) {
            double ang = (i / 3.0) * Math.PI * 2.0;
            double r = 0.35;

            double x = c.x + Math.cos(ang) * r;
            double z = c.z + Math.sin(ang) * r;

            sw.spawnParticles(ParticleTypes.COMPOSTER, x, c.y, z, 1, 0.01, 0.01, 0.01, 0.0);
        }
    }
}