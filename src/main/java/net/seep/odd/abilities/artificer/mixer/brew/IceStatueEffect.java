// FILE: src/main/java/net/seep/odd/abilities/artificer/mixer/brew/IceStatueEffect.java
package net.seep.odd.abilities.artificer.mixer.brew;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import net.seep.odd.entity.IceStatueEntity;
import net.seep.odd.entity.ModEntities;

import java.util.UUID;

public final class IceStatueEffect {
    private IceStatueEffect() {}

    private static boolean inited = false;

    public static final int LIFETIME_TICKS = 20 * 60 * 3; // 3 minutes
    public static final float HP = 4.0f;

    private record Statue(ServerWorld world, UUID owner, UUID statueUuid, long endTick, Vec3d lastPos) {}
    private static final Object2ObjectOpenHashMap<UUID, Statue> ACTIVE = new Object2ObjectOpenHashMap<>();

    private static void init() {
        if (inited) return;
        inited = true;
        ServerTickEvents.START_SERVER_TICK.register(IceStatueEffect::tick);
    }

    public static void start(ServerPlayerEntity player) {
        if (player == null) return;
        init();

        ServerWorld sw = player.getServerWorld();
        Vec3d p = player.getPos();

        // remove old statue if exists
        Statue prev = ACTIVE.get(player.getUuid());
        if (prev != null) {
            Entity old = prev.world.getEntity(prev.statueUuid);
            if (old != null) old.discard();
            ACTIVE.remove(player.getUuid());
        }

        IceStatueEntity statue = ModEntities.ICE_STATUE.create(sw);
        if (statue == null) return;

        statue.refreshPositionAndAngles(p.x, p.y, p.z, player.getYaw(), 0.0f);
        statue.setOwnerUuid(player.getUuid());
        statue.setHealth(HP);

        // ✅ Basic humanoid statue pose (no capture/network)
        // Standing, arms slightly “relaxed” (tiny pitch so it looks less T-pose-ish)
        float headP = 0f, headY = 0f, headR = 0f;
        float bodyP = 0f, bodyY = 0f, bodyR = 0f;

        float rightArmP = 0.08f, rightArmY = 0f, rightArmR = 0.02f;
        float leftArmP  = 0.08f, leftArmY  = 0f, leftArmR  = -0.02f;

        float rightLegP = 0f, rightLegY = 0f, rightLegR = 0f;
        float leftLegP  = 0f, leftLegY  = 0f, leftLegR  = 0f;

        statue.applyPose(false,
                headP, headY, headR,
                bodyP, bodyY, bodyR,
                rightArmP, rightArmY, rightArmR,
                leftArmP,  leftArmY,  leftArmR,
                rightLegP, rightLegY, rightLegR,
                leftLegP,  leftLegY,  leftLegR
        );

        sw.spawnEntity(statue);

        long end = sw.getServer().getTicks() + LIFETIME_TICKS;
        ACTIVE.put(player.getUuid(), new Statue(sw, player.getUuid(), statue.getUuid(), end, statue.getPos()));

        sw.playSound(null, BlockPos.ofFloored(p), SoundEvents.BLOCK_GLASS_PLACE, SoundCategory.PLAYERS, 0.9f, 1.25f);
        sw.playSound(null, BlockPos.ofFloored(p), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.5f, 1.9f);
    }

    private static void tick(MinecraftServer server) {
        long now = server.getTicks();

        var it = ACTIVE.object2ObjectEntrySet().fastIterator();
        while (it.hasNext()) {
            var e = it.next();
            UUID owner = e.getKey();
            Statue s = e.getValue();

            Entity ent = s.world.getEntity(s.statueUuid);

            // keep lastPos updated if statue still exists
            Vec3d last = s.lastPos;
            if (ent != null && ent.isAlive()) last = ent.getPos();

            boolean expired = now >= s.endTick;
            boolean dead = (ent == null || !ent.isAlive());

            if (expired || dead) {
                ServerPlayerEntity sp = server.getPlayerManager().getPlayer(owner);
                if (sp != null) {
                    sp.teleport(s.world, last.x, last.y, last.z, sp.getYaw(), sp.getPitch());
                    s.world.playSound(null, BlockPos.ofFloored(last), SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 0.9f, 1.1f);
                }

                if (ent != null) ent.discard();
                it.remove();
                continue;
            }

            // store updated lastPos
            ACTIVE.put(owner, new Statue(s.world, s.owner, s.statueUuid, s.endTick, last));
        }
    }
}