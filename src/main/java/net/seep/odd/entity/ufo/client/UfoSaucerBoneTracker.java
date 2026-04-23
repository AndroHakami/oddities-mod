package net.seep.odd.entity.ufo.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Environment(EnvType.CLIENT)
public final class UfoSaucerBoneTracker {
    private static final Map<Integer, Vec3d> ARM_WORLD_POS = new ConcurrentHashMap<>();

    private UfoSaucerBoneTracker() {}

    public static void setArmWorldPos(int entityId, Vec3d pos) {
        ARM_WORLD_POS.put(entityId, pos);
    }

    public static Vec3d getArmWorldPos(int entityId) {
        return ARM_WORLD_POS.get(entityId);
    }

    public static void remove(int entityId) {
        ARM_WORLD_POS.remove(entityId);
    }
}