// src/main/java/net/seep/odd/abilities/chef/ChefFoodHooks.java
package net.seep.odd.abilities.chef;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;

import net.seep.odd.abilities.chef.net.ChefFoodNet;
import net.seep.odd.status.ModStatusEffects;

public final class ChefFoodHooks {
    private ChefFoodHooks() {}

    private static boolean inited = false;
    private static final Object2LongOpenHashMap<java.util.UUID> SONIC_CD_UNTIL = new Object2LongOpenHashMap<>();

    public static void init() {
        if (inited) return;
        inited = true;

        // ✅ server receiver for triple jump
        ChefFoodNet.initServer();

        // ✅ reset extra jumps when grounded / effect ends
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                if (!p.hasStatusEffect(ModStatusEffects.DRAGON_BURRITO) || p.isOnGround()) {
                    ChefFoodNet.resetJumps(p.getUuid());
                }
            }
        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient) return ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;

            // Outer icecream: levitate what you hit
            if (sp.hasStatusEffect(ModStatusEffects.OUTER_ICECREAM) && entity instanceof LivingEntity le) {
                le.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                        net.minecraft.entity.effect.StatusEffects.LEVITATION,
                        20, // 1s
                        2,  // Levitation III
                        true, true, true
                ));
            }

            // Deepdark fries: sonic beam on attack
            if (sp.hasStatusEffect(ModStatusEffects.DEEPDARK_SONIC)) {
                doSonicBeam((ServerWorld) world, sp);
            }

            return ActionResult.PASS;
        });

        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (world.isClient) return ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;

            // Deepdark fries: also fire on block swings (feels like “every swing”)
            if (sp.hasStatusEffect(ModStatusEffects.DEEPDARK_SONIC)) {
                doSonicBeam((ServerWorld) world, sp);
            }

            return ActionResult.PASS;
        });
    }

    private static void doSonicBeam(ServerWorld sw, ServerPlayerEntity sp) {
        long now = sw.getTime();
        long nextOk = SONIC_CD_UNTIL.getOrDefault(sp.getUuid(), 0L);
        if (now < nextOk) return;
        SONIC_CD_UNTIL.put(sp.getUuid(), now + 2); // prevents double trigger same tick

        Vec3d start = sp.getEyePos();
        Vec3d dir = sp.getRotationVec(1.0f).normalize();
        double range = 12.0;
        Vec3d end = start.add(dir.multiply(range));

        // sound + initial particle
        sw.playSound(null, sp.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 0.7f, 1.15f);
        sw.spawnParticles(ParticleTypes.SONIC_BOOM, start.x, start.y, start.z, 1, 0, 0, 0, 0.0);

        // sample line particles
        for (double d = 1.0; d <= range; d += 1.6) {
            Vec3d p = start.add(dir.multiply(d));
            sw.spawnParticles(ParticleTypes.SONIC_BOOM, p.x, p.y, p.z, 1, 0, 0, 0, 0.0);
        }

        // piercing damage: hit everything near the ray
        Box box = new Box(start, end).expand(1.5);
        for (Entity e : sw.getOtherEntities(sp, box, ent -> ent instanceof LivingEntity le && le.isAlive() && ent != sp)) {
            LivingEntity le = (LivingEntity) e;

            Vec3d point = le.getPos().add(0, le.getStandingEyeHeight() * 0.5, 0);
            double t = point.subtract(start).dotProduct(dir);
            if (t < 0 || t > range) continue;

            Vec3d closest = start.add(dir.multiply(t));
            double distSq = point.squaredDistanceTo(closest);
            if (distSq > (1.1 * 1.1)) continue;

            le.damage(sw.getDamageSources().magic(), 3.0f);

            // tiny push
            Vec3d push = dir.multiply(0.35);
            le.addVelocity(push.x, 0.05, push.z);
            le.velocityModified = true;
        }
    }
}