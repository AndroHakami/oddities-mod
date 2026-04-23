// src/main/java/net/seep/odd/block/combiner/enchant/VexSwordEffect.java
package net.seep.odd.block.combiner.enchant;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class VexSwordEffect {
    private VexSwordEffect() {}

    private static final int DELAY_TICKS = 20; // ✅ 1 second
    private static final int ORBIT_EVERY_T = 2; // ✅ light particles (every 0.1s)
    private static boolean registered = false;

    private record Key(UUID attacker, UUID target) {}

    private record Pending(RegistryKey<World> worldKey,
                           long startTick,
                           long executeTick,
                           UUID attackerUuid,
                           int targetId,
                           UUID targetUuid) {}

    /**
     * Keep it light and prevent spam:
     * max 1 pending per (attacker,target). New hits refresh the timer.
     */
    private static final Map<Key, Pending> PENDING = new LinkedHashMap<>();

    /** Call once during common init AFTER CombinerEnchantments.init(). */
    public static void init() {
        if (registered) return;
        registered = true;

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient) return ActionResult.PASS;
            if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            if (!(world instanceof ServerWorld sw)) return ActionResult.PASS;
            if (!(entity instanceof LivingEntity target) || !target.isAlive()) return ActionResult.PASS;

            ItemStack stack = sp.getMainHandStack();
            if (!(stack.getItem() instanceof SwordItem)) return ActionResult.PASS;

            // ✅ must have your custom VEX enchant
            if (CombinerEnchantments.VEX == null) return ActionResult.PASS;
            if (EnchantmentHelper.getLevel(CombinerEnchantments.VEX, stack) <= 0) return ActionResult.PASS;

            long now = sw.getTime();
            long execute = now + DELAY_TICKS;

            Key k = new Key(sp.getUuid(), target.getUuid());
            PENDING.put(k, new Pending(
                    sw.getRegistryKey(),
                    now,
                    execute,
                    sp.getUuid(),
                    target.getId(),
                    target.getUuid()
            ));

            return ActionResult.PASS;
        });

        ServerTickEvents.END_SERVER_TICK.register(VexSwordEffect::tickServer);
    }

    private static void tickServer(MinecraftServer server) {
        if (PENDING.isEmpty()) return;

        Iterator<Map.Entry<Key, Pending>> it = PENDING.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            Pending p = entry.getValue();

            ServerWorld sw = server.getWorld(p.worldKey);
            if (sw == null) { it.remove(); continue; }

            long now = sw.getTime();

            // target lookup
            var ent = sw.getEntityById(p.targetId);
            if (!(ent instanceof LivingEntity target) || !target.isAlive() || !target.getUuid().equals(p.targetUuid)) {
                it.remove();
                continue;
            }

            // ✅ orbit particles while waiting (light)
            if (now < p.executeTick) {
                if (((now - p.startTick) % ORBIT_EVERY_T) == 0) {
                    spawnOrbit(sw, target, now - p.startTick);
                }
                continue;
            }

            // execute
            it.remove();

            // attacker still exists? (optional, but nice)
            ServerPlayerEntity attacker = server.getPlayerManager().getPlayer(p.attackerUuid);
            if (attacker == null || !attacker.isAlive()) continue;
            if (attacker.getWorld() != sw) continue;

            // ✅ final burst + damage
            Vec3d center = target.getPos().add(0, target.getHeight() * 0.55, 0);

            spawnBurst(sw, center);

            // 1 magic damage (delayed)
            target.damage(sw.getDamageSources().magic(), 3.0f);
        }
    }

    private static void spawnOrbit(ServerWorld sw, LivingEntity target, long t) {
        Vec3d c = target.getPos().add(0, target.getHeight() * 0.55, 0);

        // 2 small orbit points each tick (very light)
        double base = (t * 0.55);
        double r = 0.42;

        for (int i = 0; i < 2; i++) {
            double ang = base + i * Math.PI;
            double x = c.x + Math.cos(ang) * r;
            double z = c.z + Math.sin(ang) * r;

            // slight vertical wobble
            double y = c.y + 0.12 * Math.sin(ang * 1.7 + base * 0.5);

            sw.spawnParticles(
                    ParticleTypes.SOUL_FIRE_FLAME,
                    x, y, z,
                    1,
                    0.01, 0.01, 0.01,
                    0.0
            );
        }
    }

    private static void spawnBurst(ServerWorld sw, Vec3d c) {
        // small “pop” that feels like the orbit flames collapse
        sw.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                c.x, c.y, c.z,
                14,
                0.22, 0.22, 0.22,
                0.015);

        sw.spawnParticles(ParticleTypes.SMOKE,
                c.x, c.y, c.z,
                6,
                0.18, 0.18, 0.18,
                0.005);
    }
}