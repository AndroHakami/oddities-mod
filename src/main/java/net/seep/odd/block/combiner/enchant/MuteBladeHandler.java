// src/main/java/net/seep/odd/block/combiner/enchant/MuteBladeHandler.java
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
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class MuteBladeHandler {
    private MuteBladeHandler() {}

    private static boolean installed = false;

    private static final int EXPIRE_TICKS = 20 * 5; // 5s
    private static final int MAX_STACKS = 6;

    private static final class Mark {
        UUID attacker;
        long lastTick;
        int stacks;
        ServerWorld world;
        int targetId;
        UUID targetUuid;
    }

    private static final Map<UUID, Mark> MARKS = new HashMap<>();

    public static void init() {
        if (installed) return;
        installed = true;

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient) return ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            if (!(world instanceof ServerWorld sw)) return ActionResult.PASS;
            if (!(entity instanceof LivingEntity target) || !target.isAlive()) return ActionResult.PASS;

            ItemStack weapon = sp.getMainHandStack();
            if (!(weapon.getItem() instanceof SwordItem)) return ActionResult.PASS;

            if (CombinerEnchantments.SILENCE == null) return ActionResult.PASS;
            if (EnchantmentHelper.getLevel(CombinerEnchantments.SILENCE, weapon) <= 0) return ActionResult.PASS;

            // optional safety: don't stack on teammates
            if (target.isTeammate(sp)) return ActionResult.PASS;

            long now = sw.getTime();
            Mark m = MARKS.get(target.getUuid());
            if (m == null || (now - m.lastTick) > EXPIRE_TICKS) {
                m = new Mark();
                m.stacks = 0;
                MARKS.put(target.getUuid(), m);
            }

            m.attacker = sp.getUuid();
            m.lastTick = now;
            m.world = sw;
            m.targetId = target.getId();
            m.targetUuid = target.getUuid();

            m.stacks++;

            // little sculk “marker” pop each hit
            Vec3d c = target.getPos().add(0, target.getHeight() * 0.65, 0);
            sw.spawnParticles(ParticleTypes.SCULK_SOUL, c.x, c.y, c.z, 3, 0.12, 0.10, 0.12, 0.01);

            if (m.stacks < MAX_STACKS) {
                // quiet tick sound
                sw.playSound(null, target.getBlockPos(), SoundEvents.BLOCK_SCULK_SENSOR_CLICKING, SoundCategory.PLAYERS, 0.35f, 1.3f);
                return ActionResult.PASS;
            }

            // 6th hit -> sonic burst
            MARKS.remove(target.getUuid());

            sw.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 1.0f, 1.0f);
            sw.spawnParticles(ParticleTypes.SONIC_BOOM, c.x, c.y, c.z, 1, 0, 0, 0, 0);

            // strong knockback away from attacker
            Vec3d dir = target.getPos().subtract(sp.getPos()).normalize();
            double strength = 1.55;
            double yBoost = 0.55;

            target.addVelocity(dir.x * strength, yBoost, dir.z * strength);
            target.velocityModified = true;

            // a bit of magic damage (tuned)
            target.damage(sw.getDamageSources().magic(), 4.0f);

            return ActionResult.PASS;
        });

        ServerTickEvents.END_SERVER_TICK.register(MuteBladeHandler::tickExpire);
    }

    private static void tickExpire(MinecraftServer server) {
        if (MARKS.isEmpty()) return;

        // expire old marks
        Iterator<Map.Entry<UUID, Mark>> it = MARKS.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next().getValue();
            ServerWorld sw = e.world;
            if (sw == null) { it.remove(); continue; }

            long now = sw.getTime();
            if ((now - e.lastTick) > EXPIRE_TICKS) {
                it.remove();
                continue;
            }

            // optional: light “floating sculk thing” while marked (very subtle)
            if (((now - e.lastTick) % 10) == 0) {
                var ent = sw.getEntityById(e.targetId);
                if (ent instanceof LivingEntity le && le.isAlive() && le.getUuid().equals(e.targetUuid)) {
                    Vec3d c = le.getPos().add(0, le.getHeight() * 0.75, 0);
                    int count = MathHelper.clamp(e.stacks, 1, 5);
                    sw.spawnParticles(ParticleTypes.SCULK_SOUL, c.x, c.y, c.z, 1 + (count / 2), 0.08, 0.05, 0.08, 0.005);
                }
            }
        }
    }
}