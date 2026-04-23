package net.seep.odd.abilities.power;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.seep.odd.abilities.PowerAPI;
import net.seep.odd.abilities.core.CoreLinkManager;
import net.seep.odd.abilities.core.CoreNet;
import net.seep.odd.abilities.data.CooldownState;
import net.seep.odd.abilities.net.PowerNetworking;
import net.seep.odd.sound.ModSounds;
import net.seep.odd.status.ModStatusEffects;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CorePower implements Power, DeferredCooldownPower {
    public static final CorePower INSTANCE = new CorePower();

    public static final int PRIMARY_COOLDOWN_TICKS = 20 * 2;
    public static final int SECONDARY_COOLDOWN_TICKS = 20 * 35;
    public static final int PASSIVE_COOLDOWN_TICKS = 20 * 150;

    private static final int PRIMARY_CHARGE_TICKS = 20;
    private static final int PASSIVE_CHARGE_TICKS = 60;

    private static final float PRIMARY_SELF_COST = 10.0F;
    private static final float PRIMARY_DAMAGE = 12.0F;
    private static final float PASSIVE_DAMAGE = 28.0F;

    private static final double PRIMARY_RADIUS = 7.5D;
    private static final double PASSIVE_RADIUS = 12.0D;
    private static final double PACT_RANGE = 24.0D;

    private static final int ROOT_SLOWNESS_AMP = 8;
    private static final int ROOT_LOCK_TICKS = 6;

    @Override public String id() { return "core"; }
    @Override public boolean hasSlot(String slot) { return "primary".equals(slot) || "secondary".equals(slot); }
    @Override public long cooldownTicks() { return PRIMARY_COOLDOWN_TICKS; }
    @Override public long secondaryCooldownTicks() { return SECONDARY_COOLDOWN_TICKS; }

    @Override public boolean deferPrimaryCooldown() { return true; }
    @Override public boolean deferSecondaryCooldown() { return true; }
    @Override public boolean deferThirdCooldown() { return false; }

    @Override
    public Identifier iconTexture(String slot) {
        return switch (slot) {
            case "primary" -> new Identifier("odd", "textures/gui/abilities/core_primary.png");
            case "secondary" -> new Identifier("odd", "textures/gui/abilities/core_pact.png");
            default -> new Identifier("odd", "textures/gui/abilities/ability_default.png");
        };
    }

    @Override
    public String longDescription() {
        return "An unstable core beats inside you. Detonate it at will, or bind another soul and blink to them when the pact is called in.";
    }

    @Override
    public String slotTitle(String slot) {
        return switch (slot) {
            case "primary" -> "UNSTABLE CORE";
            case "secondary" -> "FORGE A PACT";
            default -> Power.super.slotTitle(slot);
        };
    }

    @Override
    public String slotLongDescription(String slot) {
        return switch (slot) {
            case "primary" -> "Spend health to trigger a short unstable-core charge, then blast everything around you in a lesser detonation.";
            case "secondary" -> "Look at a player to forge a soul pact. Press again later to blink straight to that linked soul. Milk can cleanse the pact.";
            default -> "Core";
        };
    }

    @Override
    public Identifier portraitTexture() {
        return new Identifier("odd", "textures/gui/overview/core_portrait.png");
    }

    private static final class State {
        boolean passiveCharging;
        int passiveTicks;

        boolean primaryCharging;
        int primaryTicks;
    }

    private static final Map<UUID, State> DATA = new Object2ObjectOpenHashMap<>();
    private static final Object2LongOpenHashMap<UUID> LAST_PASSIVE_USE = new Object2LongOpenHashMap<>();
    private static final Object2LongOpenHashMap<UUID> WARN_UNTIL = new Object2LongOpenHashMap<>();
    private static final Set<UUID> BYPASS_PASSIVE_DEATH = new HashSet<>();

    private static State S(ServerPlayerEntity player) {
        return DATA.computeIfAbsent(player.getUuid(), u -> new State());
    }

    private static boolean isCurrent(ServerPlayerEntity player) {
        Power power = Powers.get(PowerAPI.get(player));
        return power instanceof CorePower;
    }

    private static boolean passiveReady(ServerPlayerEntity player) {
        return passiveRemainingTicks(player) <= 0;
    }

    public static int passiveRemainingTicks(ServerPlayerEntity player) {
        long now = player.getWorld().getTime();
        long last = LAST_PASSIVE_USE.getOrDefault(player.getUuid(), Long.MIN_VALUE / 4L);
        long elapsed = now - last;
        return (int) Math.max(0L, PASSIVE_COOLDOWN_TICKS - elapsed);
    }

    private static void markPassiveUse(ServerPlayerEntity player) {
        LAST_PASSIVE_USE.put(player.getUuid(), player.getWorld().getTime());
    }

    private static void warnOncePerSec(ServerPlayerEntity p, String msg) {
        long now = p.getWorld().getTime();
        long nextOk = WARN_UNTIL.getOrDefault(p.getUuid(), 0L);
        if (now < nextOk) return;
        WARN_UNTIL.put(p.getUuid(), now + 20L);
        p.sendMessage(Text.literal(msg), true);
    }

    @Override
    public void activate(ServerPlayerEntity player) {
        if (!isCurrent(player)) return;

        State st = S(player);
        if (st.passiveCharging || st.primaryCharging) return;

        long rem = PowerAPI.getRemainingCooldownTicks(player, "primary");
        if (rem > 0) return;

        // Apply the self-cost as real magic damage so it behaves like normal damage
        // instead of directly shaving hearts off the health bar.
        BYPASS_PASSIVE_DEATH.add(player.getUuid());
        float beforeHealth = player.getHealth();
        player.damage(player.getDamageSources().magic(), PRIMARY_SELF_COST);

        if (!player.isAlive()) {
            return;
        }

        // If something fully prevented the damage, do not grant a free activation.
        if (player.getHealth() >= beforeHealth - 0.001F) {
            BYPASS_PASSIVE_DEATH.remove(player.getUuid());
            return;
        }

        PowerAPI.beginUse(player, "primary");
        PowerAPI.setHeld(player, "primary", true);

        st.primaryCharging = true;
        st.primaryTicks = PRIMARY_CHARGE_TICKS;

        rootPlayer(player, false);
        CoreNet.sendPrimarySpin(player, 24);
        playCoreFastTick(player);
        spawnChargeSteam(player, 24);
    }

    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        if (!isCurrent(player)) return;

        if (PowerAPI.getRemainingCooldownTicks(player, "secondary") > 0) return;

        if (player.hasStatusEffect(ModStatusEffects.CORE_PACT) && CoreLinkManager.hasLink(player)) {
            doBlink(player);
            return;
        }

        ServerPlayerEntity target = findLookTarget(player, PACT_RANGE);
        if (target == null) {
            warnOncePerSec(player, "§dLook at a player to forge a pact.");
            return;
        }

        if (target == player) return;

        CoreLinkManager.setLinkedTarget(player, target.getUuid());
        player.addStatusEffect(new StatusEffectInstance(ModStatusEffects.CORE_PACT, 630720000, 0, false, true, true));

        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.PACT_LINK, SoundCategory.PLAYERS, 1.0F, 1.0F);
        target.getWorld().playSound(null, target.getX(), target.getY(), target.getZ(), ModSounds.PACT_LINK, SoundCategory.PLAYERS, 1.0F, 1.0F);
    }

    @Override
    public void forceDisable(ServerPlayerEntity player) {
        if (player == null) return;

        State st = DATA.get(player.getUuid());
        if (st != null) {
            st.primaryCharging = false;
            st.primaryTicks = 0;
            st.passiveCharging = false;
            st.passiveTicks = 0;
        }

        PowerAPI.setHeld(player, "primary", false);
        CoreLinkManager.clear(player);
        if (player.hasStatusEffect(ModStatusEffects.CORE_PACT)) {
            player.removeStatusEffect(ModStatusEffects.CORE_PACT);
        }
    }

    public static void serverTick(ServerPlayerEntity player) {
        if (!isCurrent(player) || !(player.getWorld() instanceof ServerWorld world)) return;

        State st = S(player);

        if (st.primaryCharging) {
            tickPrimary(player, world, st);
        }
        if (st.passiveCharging) {
            tickPassive(player, world, st);
        }
    }

    private static void tickPrimary(ServerPlayerEntity player, ServerWorld world, State st) {
        rootPlayer(player, false);
        spawnChargeSteam(player, 18);

        if ((st.primaryTicks % 5) == 0) {
            playCoreFastTick(player);
        }

        st.primaryTicks--;
        if (st.primaryTicks > 0) return;

        st.primaryCharging = false;
        PowerAPI.setHeld(player, "primary", false);

        detonate(world, player, PRIMARY_RADIUS, PRIMARY_DAMAGE, 1.45D, false);
        startCooldown(player, "primary", PRIMARY_COOLDOWN_TICKS);
    }

    private static void tickPassive(ServerPlayerEntity player, ServerWorld world, State st) {
        rootPlayer(player, true);
        spawnChargeSteam(player, 30);
        pulseDarkness(player, world, PASSIVE_RADIUS + 6.0D);

        if ((st.passiveTicks % 6) == 0) {
            playCoreTick(player);
        }

        st.passiveTicks--;
        if (st.passiveTicks > 0) return;

        st.passiveCharging = false;

        player.setHealth(player.getMaxHealth());
        player.removeStatusEffect(StatusEffects.SLOWNESS);
        player.removeStatusEffect(StatusEffects.RESISTANCE);

        detonate(world, player, PASSIVE_RADIUS, PASSIVE_DAMAGE, 2.1D, true);
    }

    private static void rootPlayer(ServerPlayerEntity player, boolean strongResist) {
        player.setVelocity(Vec3d.ZERO);
        player.velocityModified = true;

        player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.SLOWNESS,
                ROOT_LOCK_TICKS,
                ROOT_SLOWNESS_AMP,
                true, false, false
        ));

        if (strongResist) {
            player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.RESISTANCE,
                    ROOT_LOCK_TICKS,
                    8,
                    true, false, false
            ));
        }
    }

    private static void pulseDarkness(ServerPlayerEntity source, ServerWorld world, double radius) {
        Box box = source.getBoundingBox().expand(radius);
        for (ServerPlayerEntity other : world.getEntitiesByClass(ServerPlayerEntity.class, box, p -> p != source && p.isAlive())) {
            if (other.squaredDistanceTo(source) > radius * radius) continue;
            other.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 24, 0, true, false, true));
        }
    }

    private static void spawnChargeSteam(ServerPlayerEntity player, int count) {
        if (!(player.getWorld() instanceof ServerWorld world)) return;

        double x = player.getX();
        double y = player.getBodyY(0.50D);
        double z = player.getZ();

        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z,
                Math.max(6, count / 2),
                0.42D, 0.55D, 0.42D,
                0.18D);

    }

    private static void detonate(ServerWorld world,
                                 ServerPlayerEntity owner,
                                 double radius,
                                 float maxDamage,
                                 double knockback,
                                 boolean huge) {
        double x = owner.getX();
        double y = owner.getBodyY(0.45D);
        double z = owner.getZ();

        CoreNet.sendBlastFx(world, x, y, z, (float) radius, huge ? 28 : 20, huge);


        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, huge ? 90 : 50, radius * 0.12D, 0.55D, radius * 0.12D, huge ? 0.34D : 0.24D);



        world.playSound(null, owner.getX(), owner.getY(), owner.getZ(), net.minecraft.sound.SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, huge ? 2.0F : 1.4F, huge ? 0.85F : 1.0F);

        Box box = owner.getBoundingBox().expand(radius);
        for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, box, e -> e.isAlive() && e != owner)) {
            double dist = target.getPos().distanceTo(owner.getPos());
            if (dist > radius) continue;

            double scale = 1.0D - (dist / radius);
            if (scale <= 0.0D) continue;

            float damage = Math.max(2.0F, (float) (maxDamage * scale));
            Vec3d away = target.getPos().subtract(owner.getPos());
            if (away.lengthSquared() < 1.0E-4D) away = new Vec3d(0.0D, 0.1D, 0.0D);
            away = away.normalize();

            target.damage(world.getDamageSources().explosion(owner, owner), damage);
            target.addVelocity(away.x * knockback * scale, 0.22D + (0.38D * scale), away.z * knockback * scale);
            target.velocityModified = true;
        }
    }

    private static void playCoreTick(ServerPlayerEntity player) {
        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.CORE_TICK, SoundCategory.PLAYERS, 1.0F, 1.0F);
    }

    private static void playCoreFastTick(ServerPlayerEntity player) {
        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.CORE_FAST_TICK, SoundCategory.PLAYERS, 0.9F, 1.15F);
    }

    private static void doBlink(ServerPlayerEntity player) {
        ServerPlayerEntity target = CoreLinkManager.resolveLinkedPlayer(player);
        if (target == null || !target.isAlive()) {
            CoreLinkManager.clear(player);
            player.removeStatusEffect(ModStatusEffects.CORE_PACT);
            warnOncePerSec(player, "§cYour pact target is gone.");
            return;
        }

        if (!(player.getWorld() instanceof ServerWorld playerWorld) || !(target.getWorld() instanceof ServerWorld targetWorld)) {
            return;
        }

        Vec3d pos = target.getPos();
        float yaw = target.getYaw();
        float pitch = target.getPitch();

        if (playerWorld == targetWorld) {
            player.requestTeleport(pos.x, pos.y, pos.z);
            player.setYaw(yaw);
            player.setPitch(pitch);
        } else {
            player.teleport(targetWorld, pos.x, pos.y, pos.z, yaw, pitch);
        }

        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.PACT_TELEPORT, SoundCategory.PLAYERS, 1.0F, 1.0F);
        if (player.getWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getBodyY(0.45D), player.getZ(), 36, 0.35D, 0.6D, 0.35D, 0.01D);
        }

        CoreLinkManager.clear(player);
        player.removeStatusEffect(ModStatusEffects.CORE_PACT);
        startCooldown(player, "secondary", SECONDARY_COOLDOWN_TICKS);
    }

    private static ServerPlayerEntity findLookTarget(ServerPlayerEntity player, double range) {
        Vec3d start = player.getCameraPosVec(1.0F);
        Vec3d end = start.add(player.getRotationVec(1.0F).multiply(range));
        Box searchBox = player.getBoundingBox().stretch(player.getRotationVec(1.0F).multiply(range)).expand(1.5D, 1.5D, 1.5D);

        EntityHitResult hit = ProjectileUtil.raycast(
                player,
                start,
                end,
                searchBox,
                entity -> entity instanceof ServerPlayerEntity sp && sp != player && sp.isAlive(),
                range * range
        );

        if (hit == null) return null;
        Entity entity = hit.getEntity();
        return entity instanceof ServerPlayerEntity sp ? sp : null;
    }

    private static boolean tryTriggerPassive(ServerPlayerEntity player) {
        if (!isCurrent(player)) return false;

        State st = S(player);
        if (st.passiveCharging || st.primaryCharging) return false;
        if (!passiveReady(player)) return false;

        markPassiveUse(player);
        st.passiveCharging = true;
        st.passiveTicks = PASSIVE_CHARGE_TICKS;

        player.setHealth(1.0F);
        player.extinguish();
        player.setVelocity(Vec3d.ZERO);

        rootPlayer(player, true);
        spawnChargeSteam(player, 35);
        CoreNet.sendPassiveSpin(player, 68, (float) PASSIVE_RADIUS);
        playCoreTick(player);
        return true;
    }

    private static void startCooldown(ServerPlayerEntity player, String slot, int cooldownTicks) {
        String id = PowerAPI.get(player);
        if (id == null || id.isEmpty()) return;

        long now = player.getWorld().getTime();
        String key = id;
        String lane = "primary";

        switch (slot) {
            case "secondary" -> {
                key = id + "#secondary";
                lane = "secondary";
            }
            case "third" -> {
                key = id + "#third";
                lane = "third";
            }
            case "fourth" -> {
                key = id + "#fourth";
                lane = "fourth";
            }
            default -> {
                // primary
            }
        }

        CooldownState.get(player.getServer()).setLastUse(player.getUuid(), key, now);
        PowerNetworking.sendCooldown(player, lane, Math.max(0, cooldownTicks));
    }

    static {
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (player instanceof ServerPlayerEntity sp && isCurrent(sp)) {
                State st = S(sp);
                if (st.primaryCharging || st.passiveCharging) return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });

        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (player instanceof ServerPlayerEntity sp && isCurrent(sp)) {
                State st = S(sp);
                if (st.primaryCharging || st.passiveCharging) return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });

        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (player instanceof ServerPlayerEntity sp && isCurrent(sp)) {
                State st = S(sp);
                if (st.primaryCharging || st.passiveCharging) return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (player instanceof ServerPlayerEntity sp && isCurrent(sp)) {
                State st = S(sp);
                if (st.primaryCharging || st.passiveCharging) return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });

        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, damageSource, damageAmount) -> {
            if (!(entity instanceof ServerPlayerEntity player)) return true;
            if (BYPASS_PASSIVE_DEATH.remove(player.getUuid())) return true;
            return !tryTriggerPassive(player);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.player.getUuid();
            DATA.remove(id);
            WARN_UNTIL.removeLong(id);
            BYPASS_PASSIVE_DEATH.remove(id);
        });
    }
}
