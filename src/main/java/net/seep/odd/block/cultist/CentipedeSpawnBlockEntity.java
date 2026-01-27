package net.seep.odd.block.cultist;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import net.seep.odd.block.ModBlocks;
import net.seep.odd.entity.ModEntities;
import net.seep.odd.entity.cultist.CentipedeEntity;

public final class CentipedeSpawnBlockEntity extends BlockEntity {

    private int cooldown = 20;

    public CentipedeSpawnBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.CENTIPEDE_SPAWN_BE, pos, state);
    }

    public static void tick(World world, BlockPos pos, BlockState state, CentipedeSpawnBlockEntity be) {
        if (!(world instanceof ServerWorld sw)) return;

        // ✅ Critical safety: tick may fire after block was replaced/removed
        if (state == null || state.isAir()) return;
        if (!state.isOf(ModBlocks.CENTIPEDE_SPAWN)) return;

        // Only active if players are nearby
        PlayerEntity player = sw.getClosestPlayer(
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                16.0,
                p -> p.isAlive()
                        && !p.isSpectator()

        );
        if (player == null) return;

        // Limit population near the spawner
        int nearby = sw.getEntitiesByClass(CentipedeEntity.class, new Box(pos).expand(12), e -> e.isAlive()).size();
        if (nearby >= 10) return;

        if (--be.cooldown > 0) return;

        // reset cooldown (spawns roughly every 1.5s)
        be.cooldown = 30 + sw.random.nextInt(25);

        // pick a nearby spawn point
        BlockPos spawnPos = findSpawnPos(sw, pos);
        if (spawnPos == null) return;

        CentipedeEntity c = ModEntities.CENTIPEDE.create(sw);
        if (c == null) return;

        c.refreshPositionAndAngles(
                spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5,
                sw.random.nextFloat() * 360f, 0f
        );

        // 1.20.1 signature (5 args)
        c.initialize(sw, sw.getLocalDifficulty(spawnPos), SpawnReason.SPAWNER, null, null);

        sw.spawnEntity(c);
    }

    private static BlockPos findSpawnPos(ServerWorld sw, BlockPos origin) {
        for (int tries = 0; tries < 12; tries++) {
            int dx = sw.random.nextInt(7) - 3;
            int dz = sw.random.nextInt(7) - 3;
            int dy = sw.random.nextInt(3) - 1;

            BlockPos p = origin.add(dx, dy, dz);

            if (!sw.getBlockState(p).isAir()) continue;
            if (!sw.getBlockState(p.up()).isAir()) continue;

            BlockPos floor = p.down();
            if (!sw.getBlockState(floor).isSolidBlock(sw, floor)) continue;

            // avoid spawning inside the spawner itself
            if (p.getSquaredDistance(Vec3d.ofCenter(origin)) < 1.0) continue;

            return p;
        }

        // fallback: above the block
        BlockPos top = origin.up();
        if (sw.getBlockState(top).isAir() && sw.getBlockState(top.up()).isAir()) return top;
        return null;
    }
}
