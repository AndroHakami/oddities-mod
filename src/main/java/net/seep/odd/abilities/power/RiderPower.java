package net.seep.odd.abilities.power;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

import net.seep.odd.abilities.PowerAPI;
import net.seep.odd.entity.ModEntities;
import net.seep.odd.entity.car.RiderCarEntity;
import net.seep.odd.status.ModStatusEffects;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class RiderPower implements Power {
    private static final Map<UUID, UUID> CAR_BY_PLAYER = new Object2ObjectOpenHashMap<>();

    /* ===================== POWERLESS override helpers (same style as MistyVeil) ===================== */

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

    /* ========= meta ========= */
    @Override public String id() { return "rider"; }
    @Override public boolean hasSlot(String slot) { return "primary".equals(slot) || "secondary".equals(slot); }
    @Override public long cooldownTicks() { return 20 * 2; }          // small GCD between summons
    @Override public long secondaryCooldownTicks() { return 20 * 3; } // small lockout on detonate

    @Override
    public Identifier iconTexture(String slot) {
        return switch (slot) {
            case "primary"   -> new Identifier("odd", "textures/gui/abilities/rider_car.png");
            case "secondary" -> new Identifier("odd", "textures/gui/abilities/rider_explosion.png");
            default          -> new Identifier("odd", "textures/gui/abilities/ability_default.png");
        };
    }

    @Override public String longDescription() {
        return "Summon a two-seat car (left driver). Drift with CTRL+A/D to charge a boost (x1/x2/x3), reverse, honk (R), and do a charged jump with SPACE. Secondary detonates the car.";
    }

    @Override public String slotLongDescription(String slot) {
        return switch (slot) {
            case "primary"   -> "Summon a really cool car";
            case "secondary" -> "Explode your really cool car";
            default -> "";
        };
    }

    @Override public String slotTitle(String slot) {
        return switch (slot) {
            case "primary" -> "MY RIDE";
            case "secondary" -> "IRISH ENGINEERING";
            default -> Power.super.slotTitle(slot);
        };
    }

    /* ========= POWERLESS: force disable ========= */
    @Override
    public void forceDisable(ServerPlayerEntity player) {
        // Don’t delete the car (active effect), just kick them out + block new actions while powerless.
        if (player != null && player.hasVehicle()) player.stopRiding();
    }

    /* ========= inputs ========= */
    @Override
    public void activate(ServerPlayerEntity player) {
        if (!isCurrent(player)) return;

        if (isPowerless(player)) {
            warnPowerlessOncePerSec(player);
            if (player.hasVehicle()) player.stopRiding();
            return;
        }

        if (!(player.getWorld() instanceof ServerWorld sw)) return;

        RiderCarEntity car = getOrCreateCar(sw, player);
        if (car == null) return;

        // Mount driver (left seat)
        if (!player.hasVehicle()) {
            car.tryMountDriver(player);

            // ✅ emit from the car (entity position), not player/blockpos
            sw.playSound(null, car.getX(), car.getY(), car.getZ(),
                    SoundEvents.ENTITY_MINECART_RIDING, SoundCategory.PLAYERS, 0.8f, 1.1f);
        } else {

        }
    }

    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        if (!isCurrent(player)) return;

        if (isPowerless(player)) {
            warnPowerlessOncePerSec(player);
            if (player.hasVehicle()) player.stopRiding();
            return;
        }

        if (!(player.getWorld() instanceof ServerWorld sw)) return;

        RiderCarEntity car = findCar(sw, player);
        if (car == null || !car.isAlive()) {

            return;
        }

        car.serverDetonate(); // handles explosion + kill + animation hook

        // ✅ emit from the car position (not blockpos)
        sw.playSound(null, car.getX(), car.getY(), car.getZ(),
                SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.0f, 1.0f);


        CAR_BY_PLAYER.remove(player.getUuid());
    }

    /* ========= OPTIONAL: if your power-manager calls serverTick per power ========= */
    public static void serverTick(ServerPlayerEntity p) {
        if (!isCurrent(p)) return;
        if (isPowerless(p) && p.hasVehicle()) {
            p.stopRiding();
        }
    }

    /* ========= helpers ========= */
    private static boolean isCurrent(ServerPlayerEntity p) {
        var pow = Powers.get(PowerAPI.get(p));
        return pow instanceof RiderPower;
    }

    private static RiderCarEntity getOrCreateCar(ServerWorld sw, ServerPlayerEntity owner) {
        RiderCarEntity existing = findCar(sw, owner);
        if (existing != null && existing.isAlive()) return existing;

        RiderCarEntity car = ModEntities.RIDER_CAR.create(sw);
        if (car == null) return null;

        car.setOwner(owner.getUuid());
        Vec3d spawn = owner.getPos().add(owner.getRotationVector().multiply(1.0)).add(0, 0.1, 0);
        car.refreshPositionAndAngles(spawn.x, spawn.y, spawn.z, owner.getYaw(), 0f);

        sw.spawnEntity(car);
        CAR_BY_PLAYER.put(owner.getUuid(), car.getUuid());
        return car;
    }

    private static RiderCarEntity findCar(ServerWorld sw, ServerPlayerEntity owner) {
        UUID id = CAR_BY_PLAYER.get(owner.getUuid());
        if (id == null) return null;
        var e = sw.getEntity(id);
        return (e instanceof RiderCarEntity rc) ? rc : null;
    }
}