package net.seep.odd.block.supercooker.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.seep.odd.block.supercooker.SuperCookerBlockEntity;
import net.seep.odd.screen.ModScreenHandlers;

public class SuperCookerFridgeScreenHandler extends ScreenHandler {
    private final Inventory fridge;

    public SuperCookerFridgeScreenHandler(int syncId, PlayerInventory playerInv) {
        this(syncId, playerInv, null);
    }

    public SuperCookerFridgeScreenHandler(int syncId, PlayerInventory playerInv, SuperCookerBlockEntity be) {
        super(ModScreenHandlers.SUPER_COOKER_FRIDGE, syncId);
        this.fridge = (be == null) ? new net.minecraft.inventory.SimpleInventory(20) : be.fridgeInventoryView();

        // 20 slots: 4 rows x 5 cols
        int i = 0;
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 5; col++) {
                int x = 44 + col * 18;
                int y = 18 + row * 18;
                addSlot(new Slot(fridge, i++, x, y));
            }
        }

        // player inv
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++)
                addSlot(new Slot(playerInv, c + r * 9 + 9, 8 + c * 18, 106 + r * 18));
        for (int c = 0; c < 9; c++)
            addSlot(new Slot(playerInv, c, 8 + c * 18, 164));
    }

    @Override public boolean canUse(PlayerEntity player) { return true; }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        ItemStack newStack = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot != null && slot.hasStack()) {
            ItemStack original = slot.getStack();
            newStack = original.copy();

            int fridgeEnd = 20;

            if (index < fridgeEnd) {
                if (!insertItem(original, fridgeEnd, slots.size(), true)) return ItemStack.EMPTY;
            } else {
                if (!insertItem(original, 0, fridgeEnd, false)) return ItemStack.EMPTY;
            }

            if (original.isEmpty()) slot.setStack(ItemStack.EMPTY);
            else slot.markDirty();
        }
        return newStack;
    }
}
