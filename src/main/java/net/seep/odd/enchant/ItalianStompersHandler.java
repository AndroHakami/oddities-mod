package net.seep.odd.enchant;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.seep.odd.sound.ModSounds;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ItalianStompersHandler {
    private ItalianStompersHandler(){}

    // simple per-player cooldown so we don't multi-trigger in the same fall
    private static final Map<UUID, Integer> cd = new HashMap<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                UUID id = p.getUuid();

                // cooldown tick down
                cd.computeIfPresent(id, (k, v) -> v > 0 ? v - 1 : null);

                // needs boots with our enchant
                ItemStack boots = p.getInventory().getArmorStack(0); // 0 = FEET
                if (boots.isEmpty() || EnchantmentHelper.getLevel(ModEnchantments.ITALIAN_STOMPERS, boots) <= 0) continue;

                // must be falling, not on ground, and cooldown ready
                Vec3d vel = p.getVelocity();
                if (vel.y >= -0.28 || p.isOnGround() || cd.containsKey(id)) continue;

                // find a living entity just under the player's feet
                Box feet = p.getBoundingBox().offset(0, -0.35, 0).expand(0.2, 0.25, 0.2);
                var world = p.getWorld();
                var hits = world.getEntitiesByClass(LivingEntity.class, feet, e -> e.isAlive() && e != p && !e.isSpectator());

                if (hits.isEmpty()) continue;

                // pick the closest
                LivingEntity target = hits.get(0);

                // deal damage and bounce
                float dmg = 6.0f; // tune as you like
                target.damage(world.getDamageSources().playerAttack(p), dmg);

                // bounce up; cancel fall
                p.fallDistance = 0.0f;
                p.addVelocity(0.0, 1.2, 0.0);
                p.velocityModified = true;


                // tiny knockback on target
                Vec3d kb = target.getPos().subtract(p.getPos()).normalize().multiply(0.3).add(0, 0.2, 0);
                target.addVelocity(kb.x, kb.y, kb.z);
                target.velocityModified = true;

                // short cooldown (ticks)
                cd.put(id, 10);

                // optional: SFX/particles here if you want
                // world.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, SoundCategory.PLAYERS, 1f, 1f);
                world.playSound(null, target.getBlockPos(), ModSounds.ITALIAN_STOMPERS_JUMP, SoundCategory.PLAYERS, 1.0f, 1.0f);
            }
        });
    }
}
