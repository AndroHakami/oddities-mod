// src/main/java/net/seep/odd/block/combiner/enchant/WayfinderRicochetHandler.java
package net.seep.odd.block.combiner.enchant;

import io.github.fabricators_of_create.porting_lib.entity.events.ProjectileImpactEvent;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * WAYFINDER (bow): the longer an arrow stays in the air, the more damage it deals,
 * up to a big cap (default 3x).
 *
 * Uses PortingLib ProjectileImpactEvent (1 param callback, void).
 */
public final class WayfinderRicochetHandler {
    private WayfinderRicochetHandler() {}

    private static boolean installed = false;

    /* =================== EASY TUNING =================== */

    /** Hard cap multiplier at long range. (Requested: up to triple) */
    private static final double MAX_MULTIPLIER = 3.0;

    /** How long (in ticks) the arrow must be in the air to reach MAX_MULTIPLIER. 20t = 1s. */
    private static final int TICKS_TO_MAX = 60; // 3s -> feels like “long range”

    /** Ignore super-short flights (prevents point-blank being boosted). */
    private static final int START_RAMP_AFTER_TICKS = 6;

    /** Curve shaping: higher = slower start, steeper later. */
    private static final double RAMP_EXPONENT = 1.25;

    /** Safety cleanup so UUIDs don’t live forever if something weird happens. */
    private static final int CLEANUP_AFTER_TICKS = 20 * 45; // 45s

    /* =================== STATE =================== */

    private record ShotData(long bornTick, long expireTick, int level, double baseDamage) {}
    private static final Map<UUID, ShotData> SHOTS = new HashMap<>();

    /** Call once during common init AFTER CombinerEnchantments.init(). */
    public static void init() {
        if (installed) return;
        installed = true;

        // Track arrows when they spawn/load on the server.
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (!(entity instanceof PersistentProjectileEntity arrow)) return;

            // Only care if the shooter has a WAYFINDER-enchanted BOW.
            Entity owner = arrow.getOwner();
            if (!(owner instanceof LivingEntity shooter)) return;

            ItemStack bow = findWayfinderBow(shooter);
            if (bow.isEmpty()) return;

            int lvl = EnchantmentHelper.getLevel(CombinerEnchantments.WAYFINDER, bow);
            if (lvl <= 0) return;

            long now = world.getTime();

            // Base damage snapshot (before we scale it later)
            double base = arrow.getDamage();

            // If this was loaded from disk and already aged, approximate bornTick.
            // (Entity.age is public in Yarn; no protected inGround field needed.)
            long born = now - Math.max(0, arrow.age);

            SHOTS.put(arrow.getUuid(), new ShotData(
                    born,
                    now + CLEANUP_AFTER_TICKS,
                    lvl,
                    base
            ));
        });

        // Apply multiplier right at impact (entity hit only).
        ProjectileImpactEvent.PROJECTILE_IMPACT.register(WayfinderRicochetHandler::onImpact);

        // Cleanup old entries
        ServerTickEvents.END_SERVER_TICK.register(WayfinderRicochetHandler::tickCleanup);
    }

    /* =================== IMPACT =================== */

    private static void onImpact(ProjectileImpactEvent event) {
        Entity projEnt = event.getProjectile();
        HitResult hit = event.getRayTraceResult();

        if (!(projEnt instanceof PersistentProjectileEntity arrow)) return;
        if (!(arrow.getWorld() instanceof ServerWorld sw)) return;

        // Only matter on ENTITY hits (not blocks)
        if (!(hit instanceof EntityHitResult ehr)) return;

        Entity hitEnt = ehr.getEntity();
        if (!(hitEnt instanceof LivingEntity target) || !target.isAlive()) return;

        // Must be a tracked WAYFINDER arrow
        ShotData data = SHOTS.remove(arrow.getUuid());
        if (data == null) return;

        long now = sw.getTime();
        long airTicks = Math.max(0L, now - data.bornTick);

        double mult = multiplierFor(airTicks, data.level);
        if (mult <= 1.001) return;

        // Set damage BEFORE vanilla damage is applied.
        arrow.setDamage(data.baseDamage * mult);
    }

    /* =================== HELPERS =================== */

    private static ItemStack findWayfinderBow(LivingEntity shooter) {
        if (CombinerEnchantments.WAYFINDER == null) return ItemStack.EMPTY;

        ItemStack main = shooter.getMainHandStack();
        if (isWayfinderBow(main)) return main;

        ItemStack off = shooter.getOffHandStack();
        if (isWayfinderBow(off)) return off;

        // If you ever want to support “bow in inventory”, do it here — but usually not needed.
        return ItemStack.EMPTY;
    }

    private static boolean isWayfinderBow(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (!(stack.getItem() instanceof BowItem)) return false;
        return EnchantmentHelper.getLevel(CombinerEnchantments.WAYFINDER, stack) > 0;
    }

    private static double multiplierFor(long airTicks, int level) {
        // small grace so point-blank isn’t boosted
        long t = Math.max(0L, airTicks - START_RAMP_AFTER_TICKS);

        // optional: higher levels ramp slightly faster
        double levelScale = 1.0 + 0.25 * Math.max(0, level - 1);
        double scaledTicks = t * levelScale;

        double x = scaledTicks / (double) TICKS_TO_MAX;
        x = MathHelper.clamp((float) x, 0.0f, 1.0f);

        double eased = Math.pow(x, RAMP_EXPONENT);
        return 1.0 + (MAX_MULTIPLIER - 1.0) * eased;
    }

    private static void tickCleanup(MinecraftServer server) {
        if (SHOTS.isEmpty()) return;

        ServerWorld overworld = server.getOverworld();
        if (overworld == null) return;
        long now = overworld.getTime();

        Iterator<Map.Entry<UUID, ShotData>> it = SHOTS.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            if (now >= e.getValue().expireTick) it.remove();
        }
    }
}