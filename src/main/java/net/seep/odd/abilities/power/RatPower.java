package net.seep.odd.abilities.power;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.EntityPassengersSetS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import net.seep.odd.Oddities;
import net.seep.odd.abilities.PowerAPI;
import net.seep.odd.abilities.rat.food.FoodEatenCallback;
import net.seep.odd.abilities.rat.food.RatFoodLogic;
import net.seep.odd.status.ModStatusEffects;
import virtuoel.pehkui.api.ScaleTypes;

import java.util.*;

public final class RatPower implements Power {

    /* ======================= config ======================= */
    private static final float SCALE_BASE        = 0.25f;
    private static final float SCALE_MOTION      = 2.0f;
    private static final float SCALE_JUMP_HEIGHT = 1.0f;
    private static final float SCALE_STEP_HEIGHT = 1.0f;
    private static final float SCALE_EYE_HEIGHT  = 0.27f;

    private static final double MAX_HEARTS = 7.0; // 14 HP

    private static final double TARGET_MAX_DIST        = 4.0;
    private static final double TARGET_FALLBACK_RADIUS = 2.0;
    private static final float  RAT_BASE_SCALE_MAX     = 0.75f;
    private static final int    PASSENGER_SYNC_INTERVAL_TICKS = 5;

    /* ======================= meta ======================= */
    @Override public String id() { return "rat"; }
    @Override public boolean hasSlot(String slot) { return "primary".equals(slot); }
    @Override public long cooldownTicks() { return 0; }
    @Override public long secondaryCooldownTicks() { return 0; }
    @Override public long thirdCooldownTicks() { return 0; }

    @Override
    public Identifier iconTexture(String slot) {
        return new Identifier(Oddities.MOD_ID, "textures/gui/abilities/rat_primary.png");
    }

    @Override public String longDescription() {
        return "Become a tiny, speedy rat, provides support to the players you ride, and go through the tiniest of openings.";
    }
    @Override public String slotLongDescription(String slot) {
        return "hop on/off a nearby player's shoulder, providing them with buffs based on the food you eat!";
    }
    @Override public Identifier portraitTexture() {
        return new Identifier(Oddities.MOD_ID, "textures/gui/overview/rat.png");
    }
    @Override public String slotTitle(String slot) {
        return switch (slot) {
            case "primary" -> "MY SEAT!";
            default -> Power.super.slotTitle(slot);
        };
    }

    /* ===================== POWERLESS helpers ===================== */
    private static final Map<UUID, Long> WARN_UNTIL = new HashMap<>();

    public static boolean isPowerless(ServerPlayerEntity p) {
        return p != null && p.hasStatusEffect(ModStatusEffects.POWERLESS);
    }

    private static void warnPowerlessOncePerSec(ServerPlayerEntity p) {
        if (p == null) return;
        long now = p.getWorld().getTime();
        long next = WARN_UNTIL.getOrDefault(p.getUuid(), 0L);
        if (now < next) return;
        WARN_UNTIL.put(p.getUuid(), now + 20);
        p.sendMessage(Text.literal("§cYou are powerless."), true);
    }

    /* ======================= state ======================= */
    private static final class St {
        boolean applied;
        UUID lastHostUuid;
        int lastMountSyncAt = Integer.MIN_VALUE;
    }

    private static final Map<UUID, St> DATA = new Object2ObjectOpenHashMap<>();
    private static St S(ServerPlayerEntity p) { return DATA.computeIfAbsent(p.getUuid(), u -> new St()); }

    private static boolean isCurrent(ServerPlayerEntity p) {
        var pow = Powers.get(PowerAPI.get(p));
        return pow instanceof RatPower;
    }

    /* ======================= lifecycle ======================= */
    public static void bootstrap() {
        FoodEatenCallback.EVENT.register(RatPower::onFoodEaten);
    }

    @Override
    public void forceDisable(ServerPlayerEntity p) {
        cleanupRiding(p);
    }

    /* ======================= input ======================= */

    @Override
    public void activate(ServerPlayerEntity p) {
        if (!isCurrent(p)) return;

        if (isPowerless(p)) {
            warnPowerlessOncePerSec(p);
            cleanupRiding(p);
            return;
        }

        St st = S(p);

        // already riding another player? hop down
        if (p.getVehicle() instanceof PlayerEntity) {
            cleanupRiding(p);
            return;
        }

        PlayerEntity target = raycastPlayer(p, TARGET_MAX_DIST);
        if (target == null) target = nearestPlayer(p, TARGET_FALLBACK_RADIUS);

        if (target == null || target.getUuid().equals(p.getUuid())) {
            p.sendMessage(Text.literal("No valid shoulder nearby."), true);
            return;
        }

        if (hostHasRatPassenger(target)) {
            p.sendMessage(Text.literal("They're already carrying someone."), true);
            return;
        }

        if (p.startRiding(target, true)) {
            st.lastHostUuid = target.getUuid();
            st.lastMountSyncAt = Integer.MIN_VALUE;
            p.fallDistance = 0.0f;

            if (p.getWorld() instanceof ServerWorld sw) {
                syncPassengers(sw, target, p);
            }

            p.sendMessage(Text.literal("Perched on " + target.getName().getString() + "'s shoulder."), true);
        } else {
            p.sendMessage(Text.literal("Couldn't ride that player right now."), true);
        }
    }

    @Override public void activateSecondary(ServerPlayerEntity p) { }
    @Override public void activateThird(ServerPlayerEntity p) { }

    /* ======================= tick ======================= */

    public static void serverTick(ServerPlayerEntity p) {
        if (!isCurrent(p)) return;
        St st = S(p);

        if (!st.applied) {
            st.applied = true;

            var maxAttr = p.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
            if (maxAttr != null) {
                maxAttr.setBaseValue(MAX_HEARTS * 2.0);
                if (p.getHealth() > (float)(MAX_HEARTS * 2.0)) p.setHealth((float)(MAX_HEARTS * 2.0));
            }
        }



        if (isPowerless(p)) {
            warnPowerlessOncePerSec(p);
            cleanupRiding(p);
            return;
        }

        if (!(p.getWorld() instanceof ServerWorld sw)) return;

        Entity vehicle = p.getVehicle();
        if (vehicle instanceof PlayerEntity host) {
            if (!host.isAlive()) {
                cleanupRiding(p);
                return;
            }

            st.lastHostUuid = host.getUuid();
            p.fallDistance = 0.0f;

            int now = (int) sw.getTime();
            if (now - st.lastMountSyncAt >= PASSENGER_SYNC_INTERVAL_TICKS) {
                st.lastMountSyncAt = now;
                syncPassengers(sw, host, p);
            }
            return;
        }

        // dismounted (SHIFT / any): clear remembered host
        st.lastHostUuid = null;
        st.lastMountSyncAt = Integer.MIN_VALUE;
    }

    public static void onDeactivated(ServerPlayerEntity p) {
        St st = S(p);
        if (!st.applied) return;
        st.applied = false;

        var maxAttr = p.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (maxAttr != null) {
            maxAttr.setBaseValue(20.0);
            if (p.getHealth() > 20.0f) p.setHealth(20.0f);
        }


        cleanupRiding(p);
        st.lastHostUuid = null;
        st.lastMountSyncAt = Integer.MIN_VALUE;
    }

    /* ======================= passive: share food ======================= */

    private static void onFoodEaten(ServerPlayerEntity eater, ItemStack stack, int food, float sat) {
        if (!isCurrent(eater)) return;

        RatFoodLogic.giveRatBonusSaturation(eater, food, sat);

        PlayerEntity host = findMountedHost(eater);
        if (host instanceof ServerPlayerEntity hostSp && hostSp.isAlive()) {
            RatFoodLogic.shareWithHostAndBuff(eater, hostSp, stack.getItem());
        }
    }

    /* ======================= helpers ======================= */

    public static PlayerEntity findMountedHost(PlayerEntity rat) {
        Entity v = rat.getVehicle();
        if (v instanceof PlayerEntity p) return p;

        if (rat instanceof ServerPlayerEntity sp) {
            St st = DATA.get(sp.getUuid());
            if (st != null && st.lastHostUuid != null && sp.getWorld() instanceof ServerWorld sw) {
                PlayerEntity host = sw.getPlayerByUuid(st.lastHostUuid);
                if (host != null) return host;
            }
        }
        return null;
    }

    private static void cleanupRiding(ServerPlayerEntity rat) {
        Entity vehicle = rat.getVehicle();
        rat.stopRiding();

        if (rat.getWorld() instanceof ServerWorld sw && vehicle != null) {
            syncPassengers(sw, vehicle, rat);
        }

        St st = S(rat);
        st.lastHostUuid = null;
        st.lastMountSyncAt = Integer.MIN_VALUE;
    }

    private static void syncPassengers(ServerWorld sw, Entity vehicle, Entity extraTarget) {
        if (vehicle == null) return;

        EntityPassengersSetS2CPacket pkt = new EntityPassengersSetS2CPacket(vehicle);
        HashSet<ServerPlayerEntity> targets = new HashSet<>();

        for (ServerPlayerEntity sp : PlayerLookup.tracking(vehicle)) targets.add(sp);
        if (vehicle instanceof ServerPlayerEntity vsp) targets.add(vsp);
        for (Entity passenger : vehicle.getPassengerList()) {
            if (passenger instanceof ServerPlayerEntity psp) targets.add(psp);
        }
        if (extraTarget instanceof ServerPlayerEntity esp) targets.add(esp);

        for (ServerPlayerEntity sp : targets) {
            sp.networkHandler.sendPacket(pkt);
        }
    }

    private static boolean hostHasRatPassenger(PlayerEntity host) {
        for (Entity passenger : host.getPassengerList()) {
            if (isRatPassenger(passenger)) return true;
        }
        return false;
    }

    public static boolean isRatPassenger(Entity entity) {
        if (!(entity instanceof PlayerEntity player)) return false;
        if (entity instanceof ServerPlayerEntity sp && isCurrent(sp)) return true;

        try {
            return ScaleTypes.BASE.getScaleData(player).getScale() <= RAT_BASE_SCALE_MAX;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static PlayerEntity raycastPlayer(ServerPlayerEntity p, double max) {
        Vec3d eye = p.getEyePos();
        Vec3d look = p.getRotationVector().normalize();
        Vec3d end = eye.add(look.multiply(max));

        EntityHitResult ehr = raycastEntity(p, eye, end, 0.3);
        if (ehr != null && ehr.getEntity() instanceof PlayerEntity tgt && !tgt.getUuid().equals(p.getUuid())) {
            return tgt;
        }

        var bhr = p.getWorld().raycast(new RaycastContext(eye, end,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
        if (bhr != null && bhr.getType() != net.minecraft.util.hit.HitResult.Type.MISS) {
            double dist = eye.distanceTo(Vec3d.ofCenter(bhr.getBlockPos()));
            end = eye.add(look.multiply(dist - 0.25));
        }

        Box box = new Box(eye, end).expand(0.5);
        List<PlayerEntity> list = p.getWorld().getEntitiesByClass(PlayerEntity.class, box,
                e -> e.isAlive() && !e.getUuid().equals(p.getUuid()));

        PlayerEntity best = null;
        double bestDot = 0.85;
        for (PlayerEntity cand : list) {
            Vec3d to = cand.getBoundingBox().getCenter().subtract(eye).normalize();
            double dot = to.dotProduct(look);
            if (dot > bestDot) { bestDot = dot; best = cand; }
        }
        return best;
    }

    private static EntityHitResult raycastEntity(ServerPlayerEntity p, Vec3d start, Vec3d end, double inflate) {
        Vec3d dir = end.subtract(start);
        Box sweep = p.getBoundingBox().stretch(dir).expand(inflate);
        double best = Double.MAX_VALUE;
        EntityHitResult bestHit = null;

        for (Entity e : p.getWorld().getOtherEntities(p, sweep, ent ->
                ent.isAlive() && ent != p && ent instanceof PlayerEntity)) {

            var bb = e.getBoundingBox().expand(inflate);
            var opt = bb.raycast(start, end);
            if (opt.isPresent()) {
                double dist = start.squaredDistanceTo(opt.get());
                if (dist < best) { best = dist; bestHit = new EntityHitResult(e, opt.get()); }
            }
        }
        return bestHit;
    }

    private static PlayerEntity nearestPlayer(ServerPlayerEntity p, double radius) {
        List<PlayerEntity> list = p.getWorld().getEntitiesByClass(PlayerEntity.class,
                p.getBoundingBox().expand(radius),
                e -> e.isAlive() && !e.getUuid().equals(p.getUuid()));

        PlayerEntity best = null;
        double bestD2 = Double.MAX_VALUE;
        for (PlayerEntity cand : list) {
            double d2 = p.squaredDistanceTo(cand);
            if (d2 < bestD2) { bestD2 = d2; best = cand; }
        }
        return best;
    }
}
