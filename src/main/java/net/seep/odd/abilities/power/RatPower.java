package net.seep.odd.abilities.power;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.decoration.ArmorStandEntity;
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
import net.minecraft.world.World;
import net.seep.odd.Oddities;
import net.seep.odd.abilities.PowerAPI;
import net.seep.odd.abilities.rat.PehkuiUtil;
import net.seep.odd.abilities.rat.food.FoodEatenCallback;
import net.seep.odd.abilities.rat.food.RatFoodLogic;
import net.seep.odd.status.ModStatusEffects;

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

    /* ===== shoulder seat tuning (no scoreboard tags) ===== */
    private static final String SEAT_NAME_PREFIX = "odd_rat_seat:"; // custom name marker (not visible)
    private static final double SHOULDER_SIDE = 0.34;              // +right shoulder
    private static final double SHOULDER_FWD  = 0.10;              // slightly forward
    private static final double SHOULDER_Y_EYE_OFFSET = -0.38;     // below eye Y
    private static final double HOST_SEAT_SCAN_R = 1.85;

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
        return "Become a tiny, speedy rat. Primary: hop on/off a player's shoulder. Passive: when you eat, you gain extra saturation and your host is fed with a small themed buff.";
    }
    @Override public String slotLongDescription(String slot) {
        return "Primary: hop on/off a nearby player's shoulder.";
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
        UUID lastHostUuid;          // host player uuid
        UUID seatUuid;              // invisible seat armorstand uuid
        int  lastMountSyncAt = -999999;
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
        cleanupRidingAndSync(p);
    }

    /* ======================= input ======================= */

    @Override
    public void activate(ServerPlayerEntity p) {
        if (!isCurrent(p)) return;

        if (isPowerless(p)) {
            warnPowerlessOncePerSec(p);
            cleanupRidingAndSync(p);
            return;
        }

        if (!(p.getWorld() instanceof ServerWorld sw)) return;
        St st = S(p);

        // already riding? hop down
        Entity veh = p.getVehicle();
        if (isRatSeat(veh) || veh instanceof PlayerEntity) {
            cleanupRidingAndSync(p);

            return;
        }

        PlayerEntity target = raycastPlayer(p, TARGET_MAX_DIST);
        if (target == null) target = nearestPlayer(p, TARGET_FALLBACK_RADIUS);

        if (target == null || target.getUuid().equals(p.getUuid())) {
            p.sendMessage(Text.literal("No valid shoulder nearby."), true);
            return;
        }

        if (hostHasOccupiedSeat(sw, target)) {
            p.sendMessage(Text.literal("They're already carrying someone."), true);
            return;
        }

        ArmorStandEntity seat = spawnSeat(sw, target, p.getUuid());
        if (seat == null) {
            p.sendMessage(Text.literal("Couldn't perch right now."), true);
            return;
        }

        if (p.startRiding(seat, true)) {
            st.lastHostUuid = target.getUuid();
            st.seatUuid = seat.getUuid();
            st.lastMountSyncAt = -999999;

            syncPassengers(sw, seat);
            p.sendMessage(Text.literal("Perched on " + target.getName().getString() + "'s shoulder."), true);
        } else {
            seat.discard();
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

        PehkuiUtil.applyScaleSafely(p,
                SCALE_BASE, SCALE_MOTION, SCALE_JUMP_HEIGHT, SCALE_STEP_HEIGHT, SCALE_EYE_HEIGHT);

        if (isPowerless(p)) {
            warnPowerlessOncePerSec(p);
            cleanupRidingAndSync(p);
            return;
        }

        if (!(p.getWorld() instanceof ServerWorld sw)) return;

        Entity veh = p.getVehicle();

        if (veh instanceof ArmorStandEntity seat && isRatSeat(seat)) {
            UUID hostId = hostIdFromSeat(seat);
            if (hostId != null) st.lastHostUuid = hostId;

            PlayerEntity host = (st.lastHostUuid != null) ? sw.getPlayerByUuid(st.lastHostUuid) : null;
            if (host == null || !host.isAlive()) {
                cleanupRidingAndSync(p);
                return;
            }

            Vec3d pos = shoulderPos(host);
            seat.refreshPositionAndAngles(pos.x, pos.y, pos.z, host.getYaw(), 0f);
            seat.setVelocity(Vec3d.ZERO);
            seat.velocityDirty = true;

            int now = (int) sw.getTime();
            if (now - st.lastMountSyncAt >= 20) {
                st.lastMountSyncAt = now;
                syncPassengers(sw, seat);
            }
            return;
        }

        // dismounted (SHIFT / any): discard seat + sync once
        if (st.seatUuid != null) {
            Entity e = sw.getEntity(st.seatUuid);
            if (e != null) {
                syncPassengers(sw, e);
                e.discard();
            }
            st.seatUuid = null;
            st.lastHostUuid = null;
        }
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

        PehkuiUtil.resetScalesSafely(p);
        cleanupRidingAndSync(p);
        st.lastHostUuid = null;
        st.seatUuid = null;
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
        if (rat instanceof ServerPlayerEntity sp) {
            St st = DATA.get(sp.getUuid());
            if (st != null && st.lastHostUuid != null && sp.getWorld() instanceof ServerWorld sw) {
                PlayerEntity host = sw.getPlayerByUuid(st.lastHostUuid);
                if (host != null) return host;
            }
        }
        Entity v = rat.getVehicle();
        return (v instanceof PlayerEntity p) ? p : null;
    }

    private static void cleanupRidingAndSync(ServerPlayerEntity rat) {
        if (!(rat.getWorld() instanceof ServerWorld sw)) {
            rat.stopRiding();
            return;
        }

        St st = S(rat);

        Entity veh = rat.getVehicle();
        rat.stopRiding();

        if (veh != null) syncPassengers(sw, veh);
        if (veh != null && isRatSeat(veh)) veh.discard();

        if (st.seatUuid != null) {
            Entity e = sw.getEntity(st.seatUuid);
            if (e != null) {
                syncPassengers(sw, e);
                e.discard();
            }
        }

        st.seatUuid = null;
        st.lastHostUuid = null;
    }

    private static void syncPassengers(ServerWorld sw, Entity vehicle) {
        if (vehicle == null) return;

        EntityPassengersSetS2CPacket pkt = new EntityPassengersSetS2CPacket(vehicle);
        HashSet<ServerPlayerEntity> targets = new HashSet<>();

        for (ServerPlayerEntity sp : PlayerLookup.tracking(vehicle)) targets.add(sp);
        if (vehicle instanceof ServerPlayerEntity vsp) targets.add(vsp);
        for (Entity passenger : vehicle.getPassengerList()) {
            if (passenger instanceof ServerPlayerEntity psp) targets.add(psp);
        }

        for (ServerPlayerEntity sp : targets) sp.networkHandler.sendPacket(pkt);
    }

    private static String seatName(UUID hostUuid, UUID ratUuid) {
        return SEAT_NAME_PREFIX + hostUuid + ":" + ratUuid;
    }

    private static boolean isRatSeat(Entity e) {
        if (!(e instanceof ArmorStandEntity as)) return false;
        Text n = as.getCustomName();
        if (n == null) return false;
        return n.getString().startsWith(SEAT_NAME_PREFIX);
    }

    private static UUID hostIdFromSeat(ArmorStandEntity seat) {
        Text n = seat.getCustomName();
        if (n == null) return null;
        String s = n.getString();
        if (!s.startsWith(SEAT_NAME_PREFIX)) return null;
        String rest = s.substring(SEAT_NAME_PREFIX.length());
        int idx = rest.indexOf(':');
        if (idx <= 0) return null;
        try { return UUID.fromString(rest.substring(0, idx)); }
        catch (Exception ignored) { return null; }
    }

    private static boolean hostHasOccupiedSeat(ServerWorld sw, PlayerEntity host) {
        UUID hostId = host.getUuid();
        String prefix = SEAT_NAME_PREFIX + hostId + ":";

        Box box = host.getBoundingBox().expand(HOST_SEAT_SCAN_R);
        List<ArmorStandEntity> seats = sw.getEntitiesByClass(
                ArmorStandEntity.class,
                box,
                as -> as != null && as.isAlive()
                        && as.getCustomName() != null
                        && as.getCustomName().getString().startsWith(prefix)
                        && !as.getPassengerList().isEmpty()
        );
        return !seats.isEmpty();
    }

    private static Vec3d shoulderPos(PlayerEntity host) {
        float yaw = host.getYaw();
        double rad = Math.toRadians(yaw);

        Vec3d fwd = new Vec3d(-MathHelper.sin((float)rad), 0, MathHelper.cos((float)rad));
        Vec3d right = new Vec3d(MathHelper.cos((float)rad), 0, MathHelper.sin((float)rad));

        double y = host.getEyeY() + SHOULDER_Y_EYE_OFFSET;
        Vec3d base = new Vec3d(host.getX(), y, host.getZ());

        return base.add(right.multiply(SHOULDER_SIDE)).add(fwd.multiply(SHOULDER_FWD));
    }

    private static ArmorStandEntity spawnSeat(ServerWorld sw, PlayerEntity host, UUID ratUuid) {
        ArmorStandEntity seat = new ArmorStandEntity(sw, host.getX(), host.getY(), host.getZ());

        // only use methods that are definitely accessible
        seat.setInvisible(true);
        seat.setInvulnerable(true);
        seat.setNoGravity(true);
        seat.setSilent(true);

        seat.setCustomName(Text.literal(seatName(host.getUuid(), ratUuid)));
        seat.setCustomNameVisible(false);

        Vec3d pos = shoulderPos(host);
        seat.refreshPositionAndAngles(pos.x, pos.y, pos.z, host.getYaw(), 0f);

        boolean ok = sw.spawnEntity(seat);
        return ok ? seat : null;
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