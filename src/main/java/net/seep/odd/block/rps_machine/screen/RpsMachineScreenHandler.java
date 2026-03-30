package net.seep.odd.block.rps_machine.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.math.BlockPos;
import net.seep.odd.block.rps_machine.RpsMachineBlockEntity;
import net.seep.odd.block.rps_machine.game.RpsEnemyType;
import net.seep.odd.block.rps_machine.game.RpsMove;
import net.seep.odd.block.rps_machine.game.RpsPhase;
import net.seep.odd.block.rps_machine.game.RpsRoundResult;

public class RpsMachineScreenHandler extends ScreenHandler {
    private final RpsMachineBlockEntity blockEntity;
    private final BlockPos pos;
    private final PropertyDelegate properties;

    public RpsMachineScreenHandler(int syncId, PlayerInventory playerInventory, PacketByteBuf buf) {
        this(syncId, playerInventory,
                getMachine(playerInventory, buf.readBlockPos()),
                new ArrayPropertyDelegate(RpsMachineBlockEntity.PROPERTY_COUNT));
    }

    public RpsMachineScreenHandler(int syncId, PlayerInventory playerInventory, RpsMachineBlockEntity blockEntity) {
        this(syncId, playerInventory,
                blockEntity,
                blockEntity != null ? blockEntity.getPropertyDelegate() : new ArrayPropertyDelegate(RpsMachineBlockEntity.PROPERTY_COUNT));
    }

    private RpsMachineScreenHandler(int syncId, PlayerInventory playerInventory,
                                    RpsMachineBlockEntity blockEntity, PropertyDelegate properties) {
        super(RpsMachineScreenHandlers.RPS_MACHINE, syncId);
        this.blockEntity = blockEntity;
        this.pos = blockEntity != null ? blockEntity.getPos() : BlockPos.ORIGIN;
        this.properties = properties;
        this.addProperties(properties);
    }

    private static RpsMachineBlockEntity getMachine(PlayerInventory inv, BlockPos pos) {
        if (inv.player.getWorld().getBlockEntity(pos) instanceof RpsMachineBlockEntity be) {
            return be;
        }
        return null;
    }

    public RpsPhase getPhase() {
        return RpsPhase.byId(properties.get(0));
    }

    public int getPlayerHp() {
        return properties.get(1);
    }

    public int getPlayerMaxHp() {
        return properties.get(2);
    }

    public int getEnemyHp() {
        return properties.get(3);
    }

    public int getEnemyMaxHp() {
        return properties.get(4);
    }

    public int getRoundNumber() {
        return properties.get(5);
    }

    public RpsMove getLastPlayerMove() {
        return RpsMove.byId(properties.get(6));
    }

    public RpsMove getLastEnemyMove() {
        return RpsMove.byId(properties.get(7));
    }

    public RpsRoundResult getLastRoundResult() {
        return RpsRoundResult.byId(properties.get(8));
    }

    public RpsEnemyType getEnemyType() {
        return RpsEnemyType.byId(properties.get(9));
    }

    public boolean isMachineActive() {
        return getPhase() != RpsPhase.IDLE;
    }

    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        if (blockEntity == null) return false;
        RpsMove move = RpsMove.fromButtonId(id);
        if (move == null) return false;

        blockEntity.playRound(player, move);
        return true;
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return player.squaredDistanceTo(
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5
        ) <= 64.0;
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);

        if (!player.getWorld().isClient && blockEntity != null) {
            blockEntity.stopRun();
        }
    }
}