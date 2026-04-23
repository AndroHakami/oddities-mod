// src/main/java/net/seep/odd/block/combiner/enchant/SpireStompersHandler.java
package net.seep.odd.block.combiner.enchant;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.seep.odd.sound.ModSounds;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SpireStompersHandler {
    private SpireStompersHandler(){}

    // per-player cooldown so we don't multi-trigger in the same fall
    private static final Map<UUID, Integer> cd = new HashMap<>();

    // tuning
    private static final float STOMP_DAMAGE = 7.0f; // strong
    private static final int COOLDOWN_TICKS = 8;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                UUID id = p.getUuid();

                // cooldown tick down
                cd.computeIfPresent(id, (k, v) -> v > 0 ? v - 1 : null);

                // needs boots with SPIRE enchant
                ItemStack boots = p.getInventory().getArmorStack(0); // 0 = FEET
                if (boots.isEmpty() || CombinerEnchantments.SPIRE == null) continue;
                if (EnchantmentHelper.getLevel(CombinerEnchantments.SPIRE, boots) <= 0) continue;

                // must be falling, not on ground, cooldown ready
                Vec3d vel = p.getVelocity();
                if (vel.y >= -0.28 || p.isOnGround() || cd.containsKey(id)) continue;

                // find living entity just under feet
                Box feet = p.getBoundingBox().offset(0, -0.35, 0).expand(0.2, 0.25, 0.2);
                var world = p.getWorld();
                var hits = world.getEntitiesByClass(LivingEntity.class, feet,
                        e -> e.isAlive() && e != p && !e.isSpectator());

                if (hits.isEmpty()) continue;

                // pick closest
                LivingEntity target = hits.get(0);
                double best = target.squaredDistanceTo(p);
                for (int i = 1; i < hits.size(); i++) {
                    LivingEntity le = hits.get(i);
                    double d = le.squaredDistanceTo(p);
                    if (d < best) { best = d; target = le; }
                }

                // deal damage
                target.damage(world.getDamageSources().playerAttack(p), STOMP_DAMAGE);

                // bounce up; cancel fall
                p.fallDistance = 0.0f;
                p.addVelocity(0.0, 1.25, 0.0);
                p.velocityModified = true;

                // tiny knockback on target
                Vec3d kb = target.getPos().subtract(p.getPos());
                if (kb.lengthSquared() > 1.0E-6) kb = kb.normalize();
                kb = kb.multiply(0.34).add(0, 0.22, 0);
                target.addVelocity(kb.x, kb.y, kb.z);
                target.velocityModified = true;

                // LIGHT particles (not dense)
                Vec3d c = target.getPos().add(0, target.getHeight() * 0.5, 0);
                if (world instanceof net.minecraft.server.world.ServerWorld sw) {
                    sw.spawnParticles(ParticleTypes.CLOUD, c.x, c.y, c.z, 6, 0.25, 0.10, 0.25, 0.01);
                    sw.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, c.x, c.y, c.z, 6, 0.20, 0.12, 0.20, 0.005);
                }

                // cooldown
                cd.put(id, COOLDOWN_TICKS);

                // SFX
                world.playSound(null, target.getBlockPos(), ModSounds.ITALIAN_STOMPERS_JUMP, SoundCategory.PLAYERS, 1.0f, 1.15f);
            }
        });
    }
}