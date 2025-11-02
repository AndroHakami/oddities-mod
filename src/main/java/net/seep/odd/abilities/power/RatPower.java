package net.seep.odd.abilities.power;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
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

import java.util.*;

public final class RatPower implements Power {

    /* ======================= config ======================= */
    // Pehkui feel
    private static final float SCALE_BASE        = 0.25f;  // tiny body scale
    private static final float SCALE_MOTION      = 2.0f;   // double speed
    private static final float SCALE_JUMP_HEIGHT = 1.0f;   // vanilla jump height
    private static final float SCALE_STEP_HEIGHT = 1.0f;   // vanilla step
    private static final float SCALE_EYE_HEIGHT  = 0.27f;  // camera near rat eyes

    private static final double MAX_HEARTS = 7.0;          // 7 hearts => 14 HP

    // Primary targeting
    private static final double TARGET_MAX_DIST        = 4.0;
    private static final double TARGET_FALLBACK_RADIUS = 2.0;

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

    /* ======================= state ======================= */
    private static final class St {
        boolean applied; // applied pehkui + hearts
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

    /* ======================= input ======================= */

    /** Primary: toggle riding target player directly (no seat). */
    @Override
    public void activate(ServerPlayerEntity p) {
        if (!isCurrent(p)) return;
        World w = p.getWorld();
        if (!(w instanceof ServerWorld sw)) return;

        // If already riding a player → hop down
        if (p.getVehicle() instanceof PlayerEntity) {
            p.stopRiding();
            p.sendMessage(Text.literal("You hop down."), true);
            return;
        }

        // Find target player under crosshair (fallback nearest small radius)
        PlayerEntity target = raycastPlayer(p, TARGET_MAX_DIST);
        if (target == null) target = nearestPlayer(p, TARGET_FALLBACK_RADIUS);
        if (target == null || target.getUuid().equals(p.getUuid())) {
            p.sendMessage(Text.literal("No valid shoulder nearby."), true);
            return;
        }

        // Optional: keep it to one passenger for sanity
        if (target.hasPassengers()) {
            p.sendMessage(Text.literal("They're already carrying someone."), true);
            return;
        }

        // Directly ride the player
        if (p.startRiding(target, true)) {
            p.sendMessage(Text.literal("Perched on " + target.getName().getString() + "'s shoulder."), true);
        } else {
            p.sendMessage(Text.literal("Couldn't ride that player right now."), true);
        }
    }

    @Override public void activateSecondary(ServerPlayerEntity p) { /* passive only */ }
    @Override public void activateThird(ServerPlayerEntity p) { /* none */ }

    /* ======================= tick ======================= */

    public static void serverTick(ServerPlayerEntity p) {
        if (!isCurrent(p)) return;
        St st = S(p);

        if (!st.applied) {
            st.applied = true;

            var maxAttr = p.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
            if (maxAttr != null) {
                maxAttr.setBaseValue(MAX_HEARTS * 2.0); // 14 HP
                if (p.getHealth() > (float) (MAX_HEARTS * 2.0)) p.setHealth((float) (MAX_HEARTS * 2.0));
            }

            p.sendMessage(Text.literal("You feel small and speedy."), true);
        }

        // Keep scales enforced (covers respawns/dim changes/other mods)
        PehkuiUtil.applyScaleSafely(p,
                SCALE_BASE, SCALE_MOTION, SCALE_JUMP_HEIGHT, SCALE_STEP_HEIGHT, SCALE_EYE_HEIGHT);
    }

    /** Restore defaults when switching away from Rat. */
    public static void onDeactivated(ServerPlayerEntity p) {
        St st = S(p);
        if (!st.applied) return;
        st.applied = false;

        var maxAttr = p.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (maxAttr != null) {
            maxAttr.setBaseValue(20.0); // 10 hearts
            if (p.getHealth() > 20.0f) p.setHealth(20.0f);
        }

        PehkuiUtil.resetScalesSafely(p);
        if (p.getVehicle() instanceof PlayerEntity) p.stopRiding();
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

    /** Host detection for direct riding. */
    public static PlayerEntity findMountedHost(PlayerEntity rat) {
        Entity v = rat.getVehicle();
        if (v instanceof PlayerEntity p) return p;
        return null;
    }

    private static PlayerEntity raycastPlayer(ServerPlayerEntity p, double max) {
        Vec3d eye = p.getEyePos();
        Vec3d look = p.getRotationVector().normalize();
        Vec3d end = eye.add(look.multiply(max));

        EntityHitResult ehr = raycastEntity(p, eye, end, 0.3);
        if (ehr != null && ehr.getEntity() instanceof PlayerEntity tgt && !tgt.getUuid().equals(p.getUuid())) {
            return tgt;
        }

        var bhr = p.getWorld().raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
        if (bhr != null && bhr.getType() != net.minecraft.util.hit.HitResult.Type.MISS) {
            double dist = eye.distanceTo(Vec3d.ofCenter(bhr.getBlockPos()));
            end = eye.add(look.multiply(dist - 0.25));
        }

        Box box = new Box(eye, end).expand(0.5);
        List<PlayerEntity> list = p.getWorld().getEntitiesByClass(PlayerEntity.class, box, e -> e.isAlive() && !e.getUuid().equals(p.getUuid()));
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

        for (Entity e : p.getWorld().getOtherEntities(p, sweep, ent -> ent.isAlive() && ent != p && ent instanceof PlayerEntity)) {
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
        List<PlayerEntity> list = p.getWorld().getEntitiesByClass(PlayerEntity.class, p.getBoundingBox().expand(radius), e -> e.isAlive() && !e.getUuid().equals(p.getUuid()));
        PlayerEntity best = null; double bestD2 = Double.MAX_VALUE;
        for (PlayerEntity cand : list) {
            double d2 = p.squaredDistanceTo(cand);
            if (d2 < bestD2) { bestD2 = d2; best = cand; }
        }
        return best;
    }
}
