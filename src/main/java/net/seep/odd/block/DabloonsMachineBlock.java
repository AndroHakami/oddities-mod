package net.seep.odd.block;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.seep.odd.shop.screen.DabloonsMachineScreenHandler;

public final class DabloonsMachineBlock extends Block {

    public DabloonsMachineBlock() {
        super(Settings.create().strength(2.0f).requiresTool());
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos,
                              PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world.isClient) return ActionResult.SUCCESS;

        player.openHandledScreen(new ExtendedScreenHandlerFactory() {
            @Override
            public void writeScreenOpeningData(ServerPlayerEntity sp, PacketByteBuf buf) {
                buf.writeBlockPos(pos);
            }

            @Override
            public Text getDisplayName() {
                return Text.literal("Dabloons Shop");
            }

            @Override
            public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity p) {
                return new DabloonsMachineScreenHandler(syncId, inv, pos);
            }
        });

        return ActionResult.CONSUME;
    }
}
