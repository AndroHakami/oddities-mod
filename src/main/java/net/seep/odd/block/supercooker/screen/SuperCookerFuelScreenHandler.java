package net.seep.odd.block.supercooker.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.seep.odd.block.supercooker.SuperCookerBlockEntity;
import net.seep.odd.screen.ModScreenHandlers;

public class SuperCookerFuelScreenHandler extends ScreenHandler {
    private final Inventory fuel;

    public SuperCookerFuelScreenHandler(int syncId, PlayerInventory playerInv) {
        this(syncId, playerInv, null);
    }

    public SuperCookerFuelScreenHandler(int syncId, PlayerInventory playerInv, SuperCookerBlockEntity be) {
        super(ModScreenHandlers.SUPER_COOKER_FUEL, syncId);
        this.fuel = (be == null) ? new net.minecraft.inventory.SimpleInventory(1) : be.fuelInventoryView();

        addSlot(new Slot(fuel, 0, 80, 35));

        // player inv
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++)
                addSlot(new Slot(playerInv, c + r * 9 + 9, 8 + c * 18, 84 + r * 18));
        for (int c = 0; c < 9; c++)
            addSlot(new Slot(playerInv, c, 8 + c * 18, 142));
    }

    @Override public boolean canUse(PlayerEntity player) { return true; }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        ItemStack newStack = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot != null && slot.hasStack()) {
            ItemStack original = slot.getStack();
            newStack = original.copy();

            if (index == 0) {
                if (!insertItem(original, 1, slots.size(), true)) return ItemStack.EMPTY;
            } else {
                if (!insertItem(original, 0, 1, false)) return ItemStack.EMPTY;
            }

            if (original.isEmpty()) slot.setStack(ItemStack.EMPTY);
            else slot.markDirty();
        }
        return newStack;
    }
}
