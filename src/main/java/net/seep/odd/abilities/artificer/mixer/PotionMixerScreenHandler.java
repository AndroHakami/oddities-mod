package net.seep.odd.abilities.artificer.mixer;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.math.BlockPos;

public class PotionMixerScreenHandler extends ScreenHandler {
    public static ScreenHandlerType<PotionMixerScreenHandler> TYPE; // set in your existing registry spot
    public final BlockPos pos;

    public PotionMixerScreenHandler(int syncId, PlayerInventory inv, BlockPos pos) {
        super(TYPE, syncId);
        this.pos = pos;
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        return null;
    }

    @Override public boolean canUse(PlayerEntity player) {
        return player.getWorld().getBlockEntity(pos) instanceof PotionMixerBlockEntity;
    }

    // Factory for registration:
    public static ExtendedScreenHandlerType.ScreenHandlerFactory<PotionMixerScreenHandler> factory() {
        return (syncId, inv, buf) -> new PotionMixerScreenHandler(syncId, inv, buf.readBlockPos());
    }
}
