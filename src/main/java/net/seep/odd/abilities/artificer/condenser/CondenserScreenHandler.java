package net.seep.odd.abilities.artificer.condenser;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.seep.odd.abilities.artificer.item.ArtificerVacuumItem;
import net.seep.odd.abilities.init.ArtificerCondenserRegistry;

public class CondenserScreenHandler extends ScreenHandler {
    private final Inventory inv; // BE inventory: 2 slots

    public CondenserScreenHandler(int syncId, PlayerInventory playerInv, CondenserBlockEntity be) {
        super(ArtificerCondenserRegistry.CONDENSER_SH, syncId);
        this.inv = be;
        // Vacuum slot
        this.addSlot(new Slot(inv, CondenserBlockEntity.SLOT_VACUUM, 44, 31) {
            @Override public boolean canInsert(ItemStack stack) {
                return stack.getItem() instanceof ArtificerVacuumItem;
            }
        });
        // Bucket slot
        this.addSlot(new Slot(inv, CondenserBlockEntity.SLOT_BUCKET, 116, 31) {
            @Override public boolean canInsert(ItemStack stack) {
                return stack.isOf(Items.BUCKET);
            }
        });

        // Player inv (standard)
        int m, l;
        for (m = 0; m < 3; ++m) {
            for (l = 0; l < 9; ++l) {
                this.addSlot(new Slot(playerInv, l + m * 9 + 9, 8 + l * 18, 84 + m * 18));
            }
        }
        for (l = 0; l < 9; ++l) this.addSlot(new Slot(playerInv, l, 8 + l * 18, 142));
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        ItemStack newStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasStack()) return ItemStack.EMPTY;

        ItemStack old = slot.getStack();
        newStack = old.copy();

        // slot index layout: [0..1]=BE (vacuum,bucket), [2..end)=player
        final int BE_START     = 0;
        final int BE_END_EXCL  = 2;                 // exclusive
        final int PLAYER_START = BE_END_EXCL;
        final int PLAYER_END   = this.slots.size(); // exclusive

        if (index < BE_END_EXCL) {
            // BE -> player
            if (!this.insertItem(old, PLAYER_START, PLAYER_END, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // player -> BE
            if (old.getItem() instanceof net.seep.odd.abilities.artificer.item.ArtificerVacuumItem) {
                if (!this.insertItem(old,
                        CondenserBlockEntity.SLOT_VACUUM,
                        CondenserBlockEntity.SLOT_VACUUM + 1,
                        false)) return ItemStack.EMPTY;
            } else if (old.isOf(net.minecraft.item.Items.BUCKET)) {
                if (!this.insertItem(old,
                        CondenserBlockEntity.SLOT_BUCKET,
                        CondenserBlockEntity.SLOT_BUCKET + 1,
                        false)) return ItemStack.EMPTY;
            } else {
                return ItemStack.EMPTY; // reject other items
            }
        }

        if (old.isEmpty()) {
            slot.setStack(ItemStack.EMPTY);
        } else {
            slot.markDirty();
        }

        // optional: if nothing changed, bail out
        if (old.getCount() == newStack.getCount()) return ItemStack.EMPTY;

        return newStack;
    }

    @Override public boolean canUse(PlayerEntity player) { return inv.canPlayerUse(player); }


    // basic shift-click rules

}
