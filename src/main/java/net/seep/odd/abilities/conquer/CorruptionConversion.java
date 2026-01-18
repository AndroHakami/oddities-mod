package net.seep.odd.abilities.conquer;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.seep.odd.abilities.conquer.entity.CorruptedIronGolemEntity;
import net.seep.odd.abilities.conquer.entity.CorruptedVillagerEntity;
import net.seep.odd.entity.ModEntities;

public final class CorruptionConversion {
    private CorruptionConversion() {}

    public static void ensureCorrupted(LivingEntity entity) {
        if (!(entity.getWorld() instanceof ServerWorld sw)) return;

        if (entity instanceof VillagerEntity v && !(entity instanceof CorruptedVillagerEntity)) {
            convert(sw, v, ModEntities.CORRUPTED_VILLAGER);
            return;
        }

        if (entity instanceof IronGolemEntity g && !(entity instanceof CorruptedIronGolemEntity)) {
            convert(sw, g, ModEntities.CORRUPTED_IRON_GOLEM);
        }
    }

    public static void ensureNormal(LivingEntity entity) {
        if (!(entity.getWorld() instanceof ServerWorld sw)) return;

        if (entity instanceof CorruptedVillagerEntity v) {
            convert(sw, v, EntityType.VILLAGER);
            return;
        }

        if (entity instanceof CorruptedIronGolemEntity g) {
            convert(sw, g, EntityType.IRON_GOLEM);
        }
    }

    private static <T extends MobEntity> void convert(ServerWorld sw, MobEntity from, EntityType<T> toType) {
        if (from.isRemoved() || !from.isAlive()) return;

        NbtCompound nbt = new NbtCompound();
        from.writeNbt(nbt);

        // Avoid UUID collisions during the same tick
        nbt.remove("UUID");

        T to = toType.create(sw);
        if (to == null) return;

        to.readNbt(nbt);

        // Force exact transform
        to.refreshPositionAndAngles(from.getX(), from.getY(), from.getZ(), from.getYaw(), from.getPitch());
        to.setVelocity(from.getVelocity());

        // Remove old first, then spawn new
        from.discard();
        sw.spawnEntity(to);
    }
}
