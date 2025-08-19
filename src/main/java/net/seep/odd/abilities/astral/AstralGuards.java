package net.seep.odd.abilities.astral;

import net.fabricmc.fabric.api.event.player.*;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import net.seep.odd.item.ghost.GhostHandItem;

public final class AstralGuards {
    private AstralGuards() {}

    // Safe helper (no client cast crash)
    private static boolean isAstral(PlayerEntity p) {
        return p instanceof ServerPlayerEntity sp && AstralInventory.isAstral(sp);
    }

    public static void register() {
        // Block breaking
        PlayerBlockBreakEvents.BEFORE.register((
                World world, PlayerEntity player, BlockPos pos, BlockState state, BlockEntity be
        ) -> !isAstral(player));

        // Attack block
        AttackBlockCallback.EVENT.register((player, world, hand, pos, dir) ->
                isAstral(player) ? ActionResult.FAIL : ActionResult.PASS);

        // Use block: allow Ghost Hand to pass through to item use; block everything else.
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!isAstral(player)) return ActionResult.PASS;

            ItemStack held = player.getStackInHand(hand);
            boolean ghostHand = held.getItem() instanceof GhostHandItem;

            // IMPORTANT: PASS means "don't consume/cancel here";
            // vanilla will then try the held item use next.
            return ghostHand ? ActionResult.PASS : ActionResult.FAIL;
        });

        // Attack entity
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) ->
                isAstral(player) ? ActionResult.FAIL : ActionResult.PASS);

        // Use entity: same whitelist logic as blocks
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (!isAstral(player)) return ActionResult.PASS;

            boolean ghostHand = player.getStackInHand(hand).getItem() instanceof GhostHandItem;
            return ghostHand ? ActionResult.PASS : ActionResult.FAIL;
        });

        // Use item: still block other items while astral; let Ghost Hand proceed to Item#use
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack held = player.getStackInHand(hand);
            boolean ghostHand = held.getItem() instanceof GhostHandItem;

            if (isAstral(player) && !ghostHand) {
                return TypedActionResult.fail(held);
            }
            return TypedActionResult.pass(held);
        });
    }
}
