package net.seep.odd.abilities.power;

import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
 // 1.20.1: use net.minecraft.text.Text
import net.minecraft.text.Text;
import net.seep.odd.block.ModBlocks;

public class ForgerPower implements Power {
    @Override public String id() { return "forger"; }
    @Override public String displayName() { return "Forger"; }

    @Override public long cooldownTicks() { return 40; } // place cooldown

    @Override
    public void activate(ServerPlayerEntity player) {
        ServerWorld world = (ServerWorld) player.getWorld();

        // Raycast ~4 blocks and place the Grand Anvil on top of the hit block (if air above)
        var hit = player.raycast(4.5, 0f, false);
        if (hit.getType() != HitResult.Type.BLOCK) {
            player.sendMessage(Text.literal("Look at a block to place the Grand Anvil."), true);
            return;
        }
        var bhr = (BlockHitResult)hit;
        BlockPos base = bhr.getBlockPos();
        Direction face = bhr.getSide();
        BlockPos placePos = base.offset(face);
        if (!world.getBlockState(placePos).isAir()) {
            // try above instead (common case: top face)
            placePos = base.up();
            if (!world.getBlockState(placePos).isAir()) {
                player.sendMessage(Text.literal("No space to place the Grand Anvil."), true);
                return;
            }
        }

        BlockState state = ModBlocks.GRAND_ANVIL.getDefaultState();
        world.setBlockState(placePos, state);
        player.sendMessage(Text.literal("Grand Anvil placed."), true);
    }

    // UI strings & icons
    @Override public String slotTitle(String slot) {
        return switch (slot) {
            case "primary" -> "Place Grand Anvil";
            default -> "";
        };
    }
    @Override public Identifier iconTexture(String slot) {
        return new Identifier("odd", "textures/gui/abilities/forger_place_anvil.png");
    }

    @Override public String longDescription() {
        return "The Forger crafts boons into gear using the Grand Anvil—timed strikes, sparks, and grit. "
                + "Master the quick-time forge to unlock unique upgrades like the Italian Stompers.";
    }
    @Override public Identifier portraitTexture() {
        return new Identifier("odd", "textures/gui/overview/player_icon.png");
    }
}
