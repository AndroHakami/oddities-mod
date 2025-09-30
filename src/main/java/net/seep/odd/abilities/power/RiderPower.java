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


import java.util.Map;
import java.util.UUID;

public final class RiderPower implements Power {
    private static final Map<UUID, UUID> CAR_BY_PLAYER = new Object2ObjectOpenHashMap<>();

    /* ========= meta ========= */
    @Override public String id() { return "rider"; }
    @Override public boolean hasSlot(String slot) { return "primary".equals(slot) || "secondary".equals(slot); }
    @Override public long cooldownTicks() { return 20 * 2; }          // small GCD between summons
    @Override public long secondaryCooldownTicks() { return 20 * 3; } // small lockout on detonate
    @Override public Identifier iconTexture(String slot) {
        return new Identifier("odd", "textures/gui/abilities/" + ("secondary".equals(slot) ? "rider_detonate.png" : "rider.png"));
    }
    @Override public String longDescription() {
        return "Summon a two-seat car (left driver). Drift with CTRL+A/D to charge a boost (x1/x2/x3), reverse, honk (R), and do a charged jump with SPACE. Secondary detonates the car.";
    }
    @Override public String slotLongDescription(String slot) {
        return "secondary".equals(slot) ? "Detonate your car." : "Summon / mount your car.";
    }

    /* ========= inputs ========= */
    @Override
    public void activate(ServerPlayerEntity player) {
        if (!isCurrent(player)) return;
        ServerWorld sw = (ServerWorld) player.getWorld();
        RiderCarEntity car = getOrCreateCar(sw, player);
        if (car == null) return;

        // Mount driver (left seat)
        if (!player.hasVehicle()) {
            car.tryMountDriver(player);
            player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_MINECART_RIDING, SoundCategory.PLAYERS, 0.8f, 1.1f);
        } else {
            player.sendMessage(Text.literal("Rider: You’re already mounted."), true);
        }
    }

    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        if (!isCurrent(player)) return;
        ServerWorld sw = (ServerWorld) player.getWorld();
        RiderCarEntity car = findCar(sw, player);
        if (car == null || !car.isAlive()) {
            player.sendMessage(Text.literal("Rider: No car to detonate."), true);
            return;
        }
        car.serverDetonate(); // handles explosion + kill + animation hook
        player.getWorld().playSound(null, car.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.0f, 1.0f);
        player.sendMessage(Text.literal("Rider: Boom."), true);
        CAR_BY_PLAYER.remove(player.getUuid());
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
