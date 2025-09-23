package net.seep.odd.abilities.artificer.condenser;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.seep.odd.abilities.artificer.EssenceStorage;
import net.seep.odd.abilities.artificer.EssenceType;
import net.seep.odd.abilities.artificer.item.ArtificerVacuumItem;
import net.seep.odd.abilities.artificer.fluid.ArtificerFluids;
import net.seep.odd.abilities.init.ArtificerCondenserRegistry;

public class CondenserBlockEntity extends BlockEntity implements Inventory {
    public static final int SLOT_VACUUM = 0;
    public static final int SLOT_BUCKET = 1;
    public static final int COST = 500;

    private final DefaultedList<ItemStack> items = DefaultedList.ofSize(2, ItemStack.EMPTY);

    public CondenserBlockEntity(BlockPos pos, BlockState state) {
        super(ArtificerCondenserRegistry.CONDENSER_BE, pos, state);
    }

    public void tryCondense(EssenceType type, PlayerEntity player) {
        if (world == null || world.isClient) return;

        ItemStack vac  = getStack(SLOT_VACUUM);
        ItemStack buck = getStack(SLOT_BUCKET);

        if (!(vac.getItem() instanceof ArtificerVacuumItem)) return;
        if (!buck.isOf(Items.BUCKET)) return;

        // Make sure a bucket item exists for this essence BEFORE we extract any charge.
        var bucketItem = net.seep.odd.abilities.artificer.fluid.ArtificerFluids.bucketFor(type);
        if (bucketItem == null) {
            player.sendMessage(Text.literal("No fluid bucket registered for " + type.key), true);
            return;
        }

        int have = EssenceStorage.get(vac, type);
        if (have < COST) {
            player.sendMessage(Text.literal("Need " + COST + " " + type.key + " essence."), true);
            return;
        }

        // drain after all checks pass
        int drained = EssenceStorage.extract(vac, type, COST);
        if (drained < COST) return;

        ItemStack out = new ItemStack(bucketItem);
        buck.decrement(1);
        if (buck.isEmpty()) {
            setStack(SLOT_BUCKET, out);
        } else if (!player.getInventory().insertStack(out)) {
            player.dropItem(out, false);
        }

        world.playSound(null, pos, SoundEvents.BLOCK_BREWING_STAND_BREW, SoundCategory.BLOCKS, 0.8f, 1.1f);
        markDirty();
    }

    /* ---- Inventory ---- */
    @Override public int size() { return items.size(); }
    @Override public boolean isEmpty() { for (ItemStack s : items) if (!s.isEmpty()) return false; return true; }
    @Override public ItemStack getStack(int slot) { return items.get(slot); }
    @Override public ItemStack removeStack(int slot, int amount) {
        ItemStack res = Inventories.splitStack(items, slot, amount);
        if (!res.isEmpty()) markDirty();
        return res;
    }
    @Override public ItemStack removeStack(int slot) {
        ItemStack res = Inventories.removeStack(items, slot);
        markDirty();
        return res;
    }
    @Override public void setStack(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxCountPerStack()) stack.setCount(getMaxCountPerStack());
        markDirty();
    }
    @Override public boolean canPlayerUse(PlayerEntity player) { return true; }
    @Override public void clear() { items.clear(); markDirty(); }

    /* ---- NBT ---- */
    @Override public void readNbt(NbtCompound nbt) { super.readNbt(nbt); Inventories.readNbt(nbt, items); }
    @Override protected void writeNbt(NbtCompound nbt) { super.writeNbt(nbt); Inventories.writeNbt(nbt, items); }
}
