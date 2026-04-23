package net.seep.odd.abilities.power;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.seep.odd.abilities.darkknight.DarkKnightRuntime;
import net.seep.odd.entity.ModEntities;
import net.seep.odd.entity.darkknight.DarkShieldEntity;

import java.util.List;
import java.util.Locale;

/**
 * Dark Knight power.
 *
 * Primary:
 *  SHIELD OF DARKNESS - place the shield on a looked-at living target.
 *
 * Secondary:
 *  TO ME - move/summon the shield onto yourself.
 *  Sneak + Secondary - recall/despawn the shield so it can recharge.
 */
public final class DarkKnightPower implements Power {

    @Override
    public String id() {
        return "dark_knight";
    }

    @Override
    public boolean hasSlot(String slot) {
        return "primary".equals(slot) || "secondary".equals(slot);
    }

    @Override
    public long cooldownTicks() {
        return 10L;
    }

    @Override
    public long secondaryCooldownTicks() {
        return 10L;
    }

    @Override
    public Identifier iconTexture(String slot) {
        return switch (slot) {
            case "primary" -> new Identifier("odd", "textures/gui/abilities/dark_knight_shield_of_darkness.png");
            case "secondary" -> new Identifier("odd", "textures/gui/abilities/dark_knight_to_me.png");
            default -> new Identifier("odd", "textures/gui/abilities/ability_default.png");
        };
    }

    @Override
    public String slotLongDescription(String slot) {
        return switch (slot) {
            case "primary" ->
                    "Place your Dark Shield onto a living target. It mitigates incoming damage and takes that damage itself instead.";
            case "secondary" ->
                    "Call the Dark Shield back to yourself. Pressing shift with the ability recalls it completely so it can recharge.";
            default -> "Dark Knight";
        };
    }

    @Override
    public String slotTitle(String slot) {
        return switch (slot) {
            case "primary" -> "SHIELD OF DARKNESS";
            case "secondary" -> "TO ME";
            default -> Power.super.slotTitle(slot);
        };
    }

    @Override
    public String longDescription() {
        return "Manifest a living shield of darkness that can guard allies or yourself. When the shield breaks, you surge forward with brutal power and a cleaving follow-up strike.";
    }

    @Override
    public void activate(ServerPlayerEntity player) {
        if (!hasDarkKnight(player)) {
            player.sendMessage(Text.literal("Dark Knight power check failed."), true);
            return;
        }
        usePrimary(player);
    }

    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        if (!hasDarkKnight(player)) {
            player.sendMessage(Text.literal("Dark Knight power check failed."), true);
            return;
        }
        useSecondary(player);
    }

    @Override
    public void forceDisable(ServerPlayerEntity player) {
        recallShield(player);
    }

    /* =========================================================
       Power check
       ========================================================= */

    private static String normalizePowerId(String raw) {
        if (raw == null) {
            return "";
        }

        String out = raw.toLowerCase(Locale.ROOT).trim();
        if (out.startsWith("odd:")) {
            out = out.substring(4);
        }
        out = out.replace(' ', '_');
        return out;
    }

    public static boolean hasDarkKnight(PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity sp)) {
            return false;
        }

        String current = net.seep.odd.abilities.PowerAPI.get(sp);
        return "dark_knight".equals(normalizePowerId(current));
    }

    public static boolean hasPower(PlayerEntity player) {
        return hasDarkKnight(player);
    }

    /* =========================================================
       Shield tuning
       ========================================================= */

    public static final double ORBIT_RADIUS = 1.08D;
    public static final double ORBIT_HEIGHT_OFFSET = 0.12D;
    public static final float ORBIT_SPEED_RADIANS = 0.19F;

    private static final double TARGET_RANGE = 16.0D;
    private static final float CLEAVE_MAIN_BONUS_DAMAGE = 5.0F;
    private static final float CLEAVE_SPLASH_DAMAGE = 3.0F;
    private static final double CLEAVE_RADIUS = 2.75D;

    /* =========================================================
       Ability 1 - Shield of Darkness
       ========================================================= */

    public static boolean usePrimary(ServerPlayerEntity user) {
        LivingEntity target = findLivingTarget(user, TARGET_RANGE);
        if (target == null) {
            user.sendMessage(Text.literal("No living target in sight for Shield of Darkness."), true);
            return false;
        }

        return moveShield(user, target);
    }

    /* =========================================================
       Ability 2 - To Me
       ========================================================= */

    public static boolean useSecondary(ServerPlayerEntity user) {
        if (user.isSneaking()) {
            return recallShield(user);
        }
        return moveShield(user, user);
    }

    public static boolean recallShield(ServerPlayerEntity user) {
        if (!(user.getWorld() instanceof ServerWorld serverWorld)) {
            user.sendMessage(Text.literal("Dark Shield failed: not in ServerWorld."), true);
            return false;
        }

        DarkShieldEntity shield = DarkKnightRuntime.getShieldForOwner(serverWorld, user.getUuid());
        if (shield == null || !shield.isAlive()) {
            user.sendMessage(Text.literal("No active Dark Shield to recall."), true);
            return false;
        }

        shield.recallAndDiscard(false);
        user.getWorld().playSound(
                null,
                user.getX(), user.getY(), user.getZ(),
                SoundEvents.ITEM_CHORUS_FRUIT_TELEPORT,
                SoundCategory.PLAYERS,
                0.65F,
                0.60F
        );
        return true;
    }

    private static boolean moveShield(ServerPlayerEntity user, LivingEntity newProtectedTarget) {
        if (!(user.getWorld() instanceof ServerWorld serverWorld)) {
            user.sendMessage(Text.literal("Dark Shield failed: world is not server-side."), true);
            return false;
        }

        if (!hasDarkKnight(user)) {
            user.sendMessage(Text.literal("Dark Shield failed: power mismatch."), true);
            return false;
        }

        DarkShieldEntity activeShield = DarkKnightRuntime.getShieldForOwner(serverWorld, user.getUuid());
        if (activeShield != null && activeShield.isAlive()) {
            activeShield.placeOn(newProtectedTarget);
            user.getWorld().playSound(
                    null,
                    newProtectedTarget.getX(), newProtectedTarget.getY(), newProtectedTarget.getZ(),
                    SoundEvents.ITEM_CHORUS_FRUIT_TELEPORT,
                    SoundCategory.PLAYERS,
                    0.55F,
                    1.40F
            );
            return true;
        }

        DarkKnightRuntime.ShieldState state = DarkKnightRuntime.refresh(user.getUuid(), serverWorld.getTime());
        if (state.health <= 0.1F) {
            user.sendMessage(Text.literal("Your dark shield is still recharging."), true);
            return false;
        }

        DarkShieldEntity shield = ModEntities.DARK_SHIELD.create(serverWorld);
        if (shield == null) {
            user.sendMessage(Text.literal("Dark Shield failed: entity type returned null."), true);
            return false;
        }

        shield.bootstrap(user, newProtectedTarget, state.health);

        boolean spawned = serverWorld.spawnEntity(shield);
        if (!spawned) {
            user.sendMessage(Text.literal("Dark Shield failed: serverWorld.spawnEntity returned false."), true);
            return false;
        }

        DarkKnightRuntime.beginActive(user.getUuid(), shield.getUuid(), serverWorld.getTime());
        DarkKnightRuntime.setProtected(user.getUuid(), null, newProtectedTarget.getUuid());

        user.getWorld().playSound(
                null,
                newProtectedTarget.getX(), newProtectedTarget.getY(), newProtectedTarget.getZ(),
                SoundEvents.ITEM_CHORUS_FRUIT_TELEPORT,
                SoundCategory.PLAYERS,
                0.70F,
                0.80F
        );
        return true;
    }

    /* =========================================================
       Passive - shield break burst + cleave
       ========================================================= */

    public static void onShieldBroken(ServerPlayerEntity owner) {
        owner.addStatusEffect(new StatusEffectInstance(
                StatusEffects.SPEED,
                40,
                4,
                false,
                true,
                true
        ));

        owner.addStatusEffect(new StatusEffectInstance(
                StatusEffects.STRENGTH,
                40,
                1,
                false,
                true,
                true
        ));

        DarkKnightRuntime.armCleave(
                owner.getUuid(),
                owner.getWorld().getTime() + DarkKnightRuntime.PASSIVE_WINDOW_TICKS
        );
    }

    public static void tryConsumeCleaveAndHit(ServerPlayerEntity attacker, Entity originalTarget) {
        if (!hasDarkKnight(attacker) || !DarkKnightRuntime.consumeCleave(attacker)) {
            return;
        }

        if (originalTarget instanceof LivingEntity living && living.isAlive()) {
            living.damage(attacker.getDamageSources().playerAttack(attacker), CLEAVE_MAIN_BONUS_DAMAGE);
        }

        Box splashBox = attacker.getBoundingBox().expand(CLEAVE_RADIUS, 1.25D, CLEAVE_RADIUS);
        List<LivingEntity> nearby = attacker.getWorld().getEntitiesByClass(
                LivingEntity.class,
                splashBox,
                entity -> entity.isAlive() && entity != attacker && entity != originalTarget
        );

        Vec3d forward = attacker.getRotationVec(1.0F).normalize();
        for (LivingEntity other : nearby) {
            Vec3d toOther = other.getPos().subtract(attacker.getPos());
            if (toOther.lengthSquared() < 0.0001D) {
                continue;
            }

            Vec3d dir = toOther.normalize();
            if (forward.dotProduct(dir) < 0.15D) {
                continue;
            }

            other.damage(attacker.getDamageSources().playerAttack(attacker), CLEAVE_SPLASH_DAMAGE);
            other.takeKnockback(0.45D, attacker.getX() - other.getX(), attacker.getZ() - other.getZ());
        }

        attacker.getWorld().playSound(
                null,
                attacker.getX(), attacker.getY(), attacker.getZ(),
                SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP,
                SoundCategory.PLAYERS,
                0.9F,
                0.85F
        );
    }

    /* =========================================================
       Targeting
       ========================================================= */

    private static LivingEntity findLivingTarget(ServerPlayerEntity user, double range) {
        Vec3d start = user.getCameraPosVec(1.0F);
        Vec3d end = start.add(user.getRotationVec(1.0F).multiply(range));
        Box searchBox = user.getBoundingBox()
                .stretch(user.getRotationVec(1.0F).multiply(range))
                .expand(1.0D);

        EntityHitResult hit = ProjectileUtil.raycast(
                user,
                start,
                end,
                searchBox,
                entity -> entity instanceof LivingEntity living && living.isAlive() && entity != user,
                range * range
        );

        return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
    }
}