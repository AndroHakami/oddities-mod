// src/main/java/net/seep/odd/abilities/power/ForgerPower.java
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

import net.seep.odd.abilities.forger.ForgerData;
import net.seep.odd.block.ModBlocks;
import net.seep.odd.block.combiner.CombinerBlock;
import net.seep.odd.block.combiner.CombinerBlockEntity;
import net.seep.odd.status.ModStatusEffects;

public final class ForgerPower implements Power {
    @Override public String id() { return "forger"; }
    @Override public String displayName() { return "Forger"; }
    @Override public boolean hasSlot(String slot) { return "primary".equals(slot); }
    @Override public long cooldownTicks() { return 10; }

    @Override
    public Identifier iconTexture(String slot) {
        return new Identifier("odd", "textures/gui/abilities/forger_combiner.png");
    }

    @Override
    public String longDescription() {
        return "Upgrade tools to unlock their true potential, as the enhancer you alone can unlock the final upgrade of every tool and armour!";
    }
    @Override public String slotLongDescription(String slot) {
        return switch (slot) {
            case "primary"   -> "Summon the enhancer, a powerful block that takes in magic from armour trims to enchant various tools";
            default          -> "Forger";
        };
    }
    @Override
    public String slotTitle(String slot) {
        return switch (slot) {
            case "primary" -> "THE ENHANCER";
            default -> Power.super.slotTitle(slot);
        };
    }




    private static boolean isPowerless(ServerPlayerEntity p) {
        return p != null && p.hasStatusEffect(ModStatusEffects.POWERLESS);
    }

    @Override
    public void activate(ServerPlayerEntity p) {
        if (isPowerless(p)) {
            p.sendMessage(Text.literal("§cYou are powerless."), true);
            return;
        }
        if (!(p.getWorld() instanceof ServerWorld sw)) return;

        ForgerData data = ForgerData.get(sw);

        // toggle off if exists in this dimension
        ForgerData.CombinerRef ref = data.get(p.getUuid());
        if (ref != null) {
            Identifier dim = sw.getRegistryKey().getValue();
            if (dim.equals(ref.dimension)) {
                BlockPos old = ref.pos;
                if (sw.getBlockState(old).isOf(ModBlocks.COMBINER)) {
                    if (sw.getBlockEntity(old) instanceof CombinerBlockEntity be) {
                        be.dropAllContents(sw);
                    }
                    sw.setBlockState(old, net.minecraft.block.Blocks.AIR.getDefaultState(), 3);
                    sw.playSound(null, old, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 0.55f, 1.25f);

                }
                data.clear(p.getUuid());
                return;
            }
            data.clear(p.getUuid());
        }

        // place above looked block
        HitResult hr = p.raycast(6.0, 0.0f, false);
        if (!(hr instanceof BlockHitResult bhr) || hr.getType() != HitResult.Type.BLOCK) {

            return;
        }

        BlockPos place = bhr.getBlockPos().up();
        if (!sw.getBlockState(place).isAir()) {

            return;
        }

        Direction facing = p.getHorizontalFacing().getOpposite();
        BlockState st = ModBlocks.COMBINER.getDefaultState();
        if (st.contains(CombinerBlock.FACING)) st = st.with(CombinerBlock.FACING, facing);

        sw.setBlockState(place, st, 3);

        if (sw.getBlockEntity(place) instanceof CombinerBlockEntity be) {
            be.serverStartEmerge();
        }

        data.set(p.getUuid(), sw.getRegistryKey().getValue(), place);
        sw.playSound(null, place, SoundEvents.BLOCK_ANVIL_PLACE, SoundCategory.BLOCKS, 0.65f, 1.2f);

    }
}