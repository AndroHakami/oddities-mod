// FILE: src/main/java/net/seep/odd/abilities/artificer/vialmatrix/VialMatrixInventory.java
package net.seep.odd.abilities.artificer.vialmatrix;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;

public final class VialMatrixInventory implements Inventory {

    public static final int SIZE = 9;

    private final PlayerEntity owner;
    private final VialMatrixHolder holder;
    private final DefaultedList<ItemStack> items = DefaultedList.ofSize(SIZE, ItemStack.EMPTY);

    private VialMatrixInventory(PlayerEntity owner) {
        this.owner = owner;
        this.holder = (VialMatrixHolder) owner;
        loadFromHolder();
    }

    public static VialMatrixInventory forPlayer(PlayerEntity player) {
        return new VialMatrixInventory(player);
    }

    public static boolean isAllowed(ItemStack stack) {
        if (stack.isEmpty()) return false;

        // ✅ vanilla potions
        if (stack.isOf(Items.POTION) || stack.isOf(Items.SPLASH_POTION) || stack.isOf(Items.LINGERING_POTION)) {
            return true;
        }

        // ✅ exact allowed mod items
        Identifier id = Registries.ITEM.getId(stack.getItem());

        // brews
        if (id.equals(new Identifier("odd", "brew_drinkable"))) return true;
        if (id.equals(new Identifier("odd", "brew_throwable"))) return true;

        // essence buckets
        if (id.equals(new Identifier("odd", "bucket_gaia")))  return true;
        if (id.equals(new Identifier("odd", "bucket_hot")))   return true;
        if (id.equals(new Identifier("odd", "bucket_cold")))  return true;
        if (id.equals(new Identifier("odd", "bucket_life")))  return true;
        if (id.equals(new Identifier("odd", "bucket_death"))) return true;
        if (id.equals(new Identifier("odd", "bucket_light"))) return true;

        return false;
    }

    private void loadFromHolder() {
        items.clear();
        NbtCompound data = holder.odd$getVialMatrixData();
        Inventories.readNbt(data, items);
    }

    private void saveToHolder() {
        NbtCompound out = new NbtCompound();
        Inventories.writeNbt(out, items);
        holder.odd$setVialMatrixData(out);
    }

    @Override public int size() { return SIZE; }

    @Override
    public boolean isEmpty() {
        for (ItemStack s : items) if (!s.isEmpty()) return false;
        return true;
    }

    @Override public ItemStack getStack(int slot) { return items.get(slot); }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        ItemStack result = Inventories.splitStack(items, slot, amount);
        if (!result.isEmpty()) markDirty();
        return result;
    }

    @Override
    public ItemStack removeStack(int slot) {
        ItemStack result = Inventories.removeStack(items, slot);
        if (!result.isEmpty()) markDirty();
        return result;
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        if (!stack.isEmpty() && !isAllowed(stack)) {
            return; // hard reject
        }
        items.set(slot, stack);
        markDirty();
    }


    @Override public void clear() { items.clear(); markDirty(); }

    @Override
    public void markDirty() {
        saveToHolder();
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return player == owner;
    }
}