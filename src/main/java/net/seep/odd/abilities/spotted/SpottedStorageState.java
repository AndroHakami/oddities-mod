package net.seep.odd.abilities.spotted;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

import java.util.Map;
import java.util.UUID;

/** Server-world persistent stash for Phantom Buddy contents, keyed by owner UUID. */
public final class SpottedStorageState extends PersistentState {
    private static final String SAVE_KEY = "odd_spotted_buddy";
    private static final String ROOT = "Owners"; // compound<uuid -> NbtList Items>

    private final Object2ObjectOpenHashMap<UUID, NbtList> byOwner = new Object2ObjectOpenHashMap<>();

    public SpottedStorageState() {}

    /** Save the given inventory for this owner (overwrites previous). */
    public void saveInventory(UUID ownerId, Inventory inv) {
        DefaultedList<ItemStack> tmp = DefaultedList.ofSize(inv.size(), ItemStack.EMPTY);
        for (int i = 0; i < inv.size(); i++) tmp.set(i, inv.getStack(i));

        NbtCompound wrap = new NbtCompound();
        Inventories.writeNbt(wrap, tmp);
        NbtList items = wrap.getList("Items", NbtCompound.COMPOUND_TYPE);

        byOwner.put(ownerId, items.copy());
        markDirty();
    }

    /** Loads owner’s saved contents (if any) into inv; clears inv first. */
    public void loadInto(UUID ownerId, Inventory inv) {
        for (int i = 0; i < inv.size(); i++) inv.setStack(i, ItemStack.EMPTY);

        NbtList items = byOwner.get(ownerId);
        if (items == null) return;

        NbtCompound wrap = new NbtCompound();
        wrap.put("Items", items.copy());
        DefaultedList<ItemStack> tmp = DefaultedList.ofSize(inv.size(), ItemStack.EMPTY);
        Inventories.readNbt(wrap, tmp);
        for (int i = 0; i < inv.size() && i < tmp.size(); i++) inv.setStack(i, tmp.get(i));
    }

    /** Remove any saved stash for this owner. */
    public void clear(UUID ownerId) {
        if (byOwner.remove(ownerId) != null) {
            markDirty();
        }
    }

    /* ---------- Persistence ---------- */

    public static SpottedStorageState get(MinecraftServer server) {
        PersistentStateManager mgr = server.getOverworld().getPersistentStateManager();
        return mgr.getOrCreate(SpottedStorageState::fromNbt, SpottedStorageState::new, SAVE_KEY);
    }

    public static SpottedStorageState fromNbt(NbtCompound nbt) {
        SpottedStorageState st = new SpottedStorageState();
        NbtCompound root = nbt.getCompound(ROOT);
        for (String key : root.getKeys()) {
            try {
                UUID id = UUID.fromString(key);
                NbtList list = root.getList(key, NbtCompound.COMPOUND_TYPE);
                st.byOwner.put(id, list.copy());
            } catch (IllegalArgumentException ignored) {}
        }
        return st;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtCompound root = new NbtCompound();
        for (Map.Entry<UUID, NbtList> e : byOwner.entrySet()) {
            root.put(e.getKey().toString(), e.getValue().copy());
        }
        nbt.put(ROOT, root);
        return nbt;
    }
}
