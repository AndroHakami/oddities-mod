package net.seep.odd.abilities.lunar;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.seep.odd.abilities.lunar.net.LunarPackets;

import java.util.Map;
import java.util.UUID;

public final class LunarAnchors {
    private LunarAnchors() {}

    private static final class Anchor { final BlockPos pos; final Integer entityId;
        Anchor(BlockPos p, Integer id){ this.pos = p; this.entityId = id; } }
    private static final Map<UUID, Anchor> BY_PLAYER = new Object2ObjectOpenHashMap<>();

    public static void set(ServerPlayerEntity owner, BlockPos pos) {
        BY_PLAYER.put(owner.getUuid(), new Anchor(pos, null));
        LunarPackets.sendAnchorPos(owner, pos);
    }
    public static void set(ServerPlayerEntity owner, Entity target) {
        BY_PLAYER.put(owner.getUuid(), new Anchor(target.getBlockPos(), target.getId()));
        LunarPackets.sendAnchorEntity(owner, target.getId());
    }
    public static void clear(ServerPlayerEntity owner) {
        BY_PLAYER.remove(owner.getUuid());
        LunarPackets.clearAnchor(owner);
    }

    /** optional getter if you need it elsewhere */
    public static boolean has(ServerPlayerEntity owner) { return BY_PLAYER.containsKey(owner.getUuid()); }
}
