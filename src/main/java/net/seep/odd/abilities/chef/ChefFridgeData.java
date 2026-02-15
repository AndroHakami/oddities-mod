package net.seep.odd.abilities.chef;

import net.minecraft.inventory.Inventories;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ChefFridgeData extends PersistentState {
    private static final String KEY = "odd_chef_fridge";
    public static final int SIZE = 20;

    private final Map<UUID, DefaultedList<ItemStack>> byPlayer = new HashMap<>();

    public static ChefFridgeData get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(
                ChefFridgeData::fromNbt,
                ChefFridgeData::new,
                KEY
        );
    }

    public DefaultedList<ItemStack> getList(UUID player) {
        return byPlayer.computeIfAbsent(player, u -> DefaultedList.ofSize(SIZE, ItemStack.EMPTY));
    }

    public static ChefFridgeData fromNbt(NbtCompound nbt) {
        ChefFridgeData d = new ChefFridgeData();
        NbtList list = nbt.getList("Players", 10);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound e = list.getCompound(i);
            UUID uuid = e.getUuid("UUID");
            DefaultedList<ItemStack> inv = DefaultedList.ofSize(SIZE, ItemStack.EMPTY);
            Inventories.readNbt(e.getCompound("Inv"), inv);
            d.byPlayer.put(uuid, inv);
        }
        return d;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList list = new NbtList();
        for (var e : byPlayer.entrySet()) {
            NbtCompound c = new NbtCompound();
            c.putUuid("UUID", e.getKey());

            NbtCompound invNbt = new NbtCompound();
            Inventories.writeNbt(invNbt, e.getValue());
            c.put("Inv", invNbt);

            list.add(c);
        }
        nbt.put("Players", list);
        return nbt;
    }
}
