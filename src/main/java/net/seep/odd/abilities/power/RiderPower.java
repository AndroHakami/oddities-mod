package net.seep.odd.abilities.power;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
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
        return "Summon a four seated car... with some explosive properties and a sick radio!";
    }

    @Override public String slotLongDescription(String slot) {
        return switch (slot) {
            case "primary"   -> "Summon a four seater that drifts and navigats through any terrain.";
            case "secondary" -> "Overcharge the car's engine to the max, blowing it up.";
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

        RiderCarEntity existing = findCar(sw, player);
        if (existing != null && existing.isAlive()) {
            if (player.getVehicle() == existing) player.stopRiding();
            existing.discard();
            clearCarReference(player.getUuid(), existing.getUuid());
            return;
        }

        RiderCarEntity car = getOrCreateCar(sw, player);
        if (car == null) return;

        // Mount driver (left seat)
        if (!player.hasVehicle()) {
            car.tryMountDriver(player);

            // ✅ emit from the car (entity position), not player/blockpos
            sw.playSound(null, car.getX(), car.getY(), car.getZ(),
                    SoundEvents.ENTITY_MINECART_RIDING, SoundCategory.PLAYERS, 0.8f, 1.1f);
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

    public static void clearCarReference(UUID ownerUuid, UUID carUuid) {
        if (ownerUuid == null) return;
        UUID current = CAR_BY_PLAYER.get(ownerUuid);
        if (carUuid == null || Objects.equals(current, carUuid)) {
            CAR_BY_PLAYER.remove(ownerUuid);
        }
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
        return findCar(owner.getServer(), owner.getUuid());
    }

    private static RiderCarEntity findCar(MinecraftServer server, UUID ownerUuid) {
        if (server == null || ownerUuid == null) return null;

        UUID mappedId = CAR_BY_PLAYER.get(ownerUuid);
        if (mappedId != null) {
            for (ServerWorld world : server.getWorlds()) {
                Entity e = world.getEntity(mappedId);
                if (e instanceof RiderCarEntity rc && rc.isAlive()) {
                    return rc;
                }
            }
            CAR_BY_PLAYER.remove(ownerUuid);
        }

        RiderCarEntity found = null;
        ArrayList<RiderCarEntity> extras = new ArrayList<>();

        for (ServerWorld world : server.getWorlds()) {
            for (Entity e : world.iterateEntities()) {
                if (!(e instanceof RiderCarEntity rc) || !rc.isAlive()) continue;
                if (!ownerUuid.equals(rc.getOwner())) continue;

                if (found == null) {
                    found = rc;
                } else {
                    extras.add(rc);
                }
            }
        }

        if (found != null) {
            CAR_BY_PLAYER.put(ownerUuid, found.getUuid());
        }
        for (RiderCarEntity extra : extras) {
            extra.discard();
        }
        return found;
    }
}