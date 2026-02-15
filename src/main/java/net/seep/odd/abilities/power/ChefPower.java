package net.seep.odd.abilities.power;

import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import net.minecraft.particle.DustParticleEffect;
import org.joml.Vector3f;

import net.seep.odd.Oddities;
import net.seep.odd.abilities.chef.ChefData;
import net.seep.odd.block.ModBlocks;
import net.seep.odd.block.supercooker.SuperCookerBlock;
import net.seep.odd.block.supercooker.SuperCookerBlockEntity;

public final class ChefPower implements Power {
    @Override public String id() { return "chef"; }
    @Override public String displayName() { return "Chef"; }
    @Override public boolean hasSlot(String slot) { return "primary".equals(slot); }
    @Override public long cooldownTicks() { return 10; } // quick toggle
    @Override public long secondaryCooldownTicks() { return 0; }
    @Override public long thirdCooldownTicks() { return 0; }

    @Override
    public Identifier iconTexture(String slot) {
        return new Identifier(Oddities.MOD_ID, "textures/gui/abilities/ability_default.png");
    }

    @Override public String longDescription() {
        return "Summon a Super Cooker. Cooking is a rhythm of stirring and timing.";
    }

    @Override
    public void activate(ServerPlayerEntity p) {
        ServerWorld sw = (ServerWorld) p.getWorld();
        ChefData data = ChefData.get(sw);

        // Toggle: if cooker exists, despawn it
        ChefData.CookerRef ref = data.getCooker(p.getUuid());
        if (ref != null) {
            // only despawn if same dimension
            Identifier dim = sw.getRegistryKey().getValue();
            if (dim.equals(ref.dimension)) {
                BlockPos oldPos = ref.pos;
                if (sw.getBlockState(oldPos).isOf(ModBlocks.SUPER_COOKER)) {
                    // drop ingredients/result/fuel before removing
                    if (sw.getBlockEntity(oldPos) instanceof SuperCookerBlockEntity be) {
                        be.dropAllCookerContents(sw);
                    }
                    sw.setBlockState(oldPos, net.minecraft.block.Blocks.AIR.getDefaultState(), 3);

                    // Jakarta-skyline dust burst (purple + orange)
                    dustBurst(sw, Vec3d.ofCenter(oldPos).add(0, 0.6, 0));

                    sw.playSound(null, oldPos, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 0.6f, 1.35f);
                    p.sendMessage(Text.literal("Cooker dismissed."), true);
                }
                data.clearCooker(p.getUuid());
                return;
            }
            // different dimension: just clear stale ref
            data.clearCooker(p.getUuid());
        }

        // Spawn new cooker above the block you’re looking at
        HitResult hr = p.raycast(6.0, 0.0f, false);
        if (!(hr instanceof BlockHitResult bhr) || hr.getType() != HitResult.Type.BLOCK) {
            p.sendMessage(Text.literal("Look at a block to place the cooker."), true);
            return;
        }

        BlockPos place = bhr.getBlockPos().up();
        if (!sw.getBlockState(place).isAir()) {
            p.sendMessage(Text.literal("Not enough space above that block."), true);
            return;
        }

        Direction facing = p.getHorizontalFacing().getOpposite();
        BlockState state = ModBlocks.SUPER_COOKER.getDefaultState();
        if (state.contains(SuperCookerBlock.FACING)) {
            state = state.with(SuperCookerBlock.FACING, facing);
        }

        sw.setBlockState(place, state, 3);

        if (sw.getBlockEntity(place) instanceof SuperCookerBlockEntity be) {
            be.serverStartEmerge();
        }

        data.setCooker(p.getUuid(), sw.getRegistryKey().getValue(), place);
        sw.playSound(null, place, SoundEvents.BLOCK_ANVIL_PLACE, SoundCategory.BLOCKS, 0.6f, 1.2f);
        p.sendMessage(Text.literal("Cooker summoned."), true);
    }

    private static void dustBurst(ServerWorld sw, Vec3d at) {
        // purple
        DustParticleEffect purple = new DustParticleEffect(new Vector3f(0.75f, 0.35f, 0.95f), 1.6f);
        // orange
        DustParticleEffect orange = new DustParticleEffect(new Vector3f(1.0f, 0.55f, 0.15f), 1.6f);

        for (int i = 0; i < 40; i++) {
            double dx = (sw.random.nextDouble() - 0.5) * 1.2;
            double dy = (sw.random.nextDouble()) * 0.7;
            double dz = (sw.random.nextDouble() - 0.5) * 1.2;
            sw.spawnParticles((i % 2 == 0) ? purple : orange,
                    at.x, at.y, at.z,
                    1, dx, dy, dz, 0.02);
        }
    }
}
