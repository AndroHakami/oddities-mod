package net.seep.odd.abilities.power;

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
import net.seep.odd.Oddities;
import net.seep.odd.block.ModBlocks;
import net.seep.odd.block.supercooker.SuperCookerBlock;
import net.seep.odd.block.supercooker.SuperCookerBlockEntity;

public final class ChefPower implements Power {
    public static final String ID = "chef";

    @Override public String id() { return ID; }
    @Override public String displayName() { return "Chef"; }
    @Override public boolean hasSlot(String slot) { return "primary".equals(slot); }

    @Override public long cooldownTicks() { return 20 * 20; } // 20s
    @Override public long secondaryCooldownTicks() { return 0; }
    @Override public long thirdCooldownTicks() { return 0; }

    @Override
    public Identifier iconTexture(String slot) {
        return new Identifier(Oddities.MOD_ID, "textures/gui/abilities/chef_super_cooker.png");
    }

    @Override public String longDescription() {
        return "Passive: 25% chance to double animal drops. Primary: summon Super Cooker.";
    }

    @Override public String slotTitle(String slot) { return "SUPER COOKER"; }
    @Override public String slotLongDescription(String slot) { return "Summon the Super Cooker above a block."; }

    @Override public Identifier portraitTexture() {
        return new Identifier(Oddities.MOD_ID, "textures/gui/overview/chef.png");
    }

    @Override
    public void activate(ServerPlayerEntity p) {
        ServerWorld sw = (ServerWorld) p.getWorld();

        HitResult hr = p.raycast(5.0, 1.0f, false);
        if (!(hr instanceof BlockHitResult bhr) || hr.getType() != HitResult.Type.BLOCK) {
            p.sendMessage(Text.literal("Look at a block to summon the Super Cooker."), true);
            return;
        }

        BlockPos base = bhr.getBlockPos();
        BlockPos placePos = base.up();

        if (!sw.getBlockState(base).isSolidBlock(sw, base)) {
            p.sendMessage(Text.literal("Must be summoned above a solid block."), true);
            return;
        }
        if (!sw.getBlockState(placePos).isAir()) {
            p.sendMessage(Text.literal("No space above that block."), true);
            return;
        }

        Direction facing = p.getHorizontalFacing().getOpposite();

        sw.setBlockState(placePos,
                ModBlocks.SUPER_COOKER.getDefaultState().with(SuperCookerBlock.FACING, facing));

        sw.playSound(null, placePos, SoundEvents.BLOCK_ANVIL_PLACE, SoundCategory.PLAYERS, 0.8f, 1.2f);

        if (sw.getBlockEntity(placePos) instanceof SuperCookerBlockEntity be) {
            be.serverStartEmerge();
        }
    }
}
