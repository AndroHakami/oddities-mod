package net.seep.odd.device.store;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.BlockPos;

public final class DabloonStoreSnapshot {
    public final String dimensionId;
    public final BlockPos pos;
    public final UUID ownerUuid;
    public final String ownerName;
    public final String title;
    public final boolean discoveryEnabled;
    public final int hologramColor;
    public final String musicId;
    public final ItemStack hologramStack;
    public final List<DabloonStoreEntry> entries;
    public final List<DabloonStoreSale> sales;

    public DabloonStoreSnapshot(String dimensionId,
                                BlockPos pos,
                                UUID ownerUuid,
                                String ownerName,
                                String title,
                                boolean discoveryEnabled,
                                int hologramColor,
                                String musicId,
                                ItemStack hologramStack,
                                List<DabloonStoreEntry> entries,
                                List<DabloonStoreSale> sales) {
        this.dimensionId = dimensionId == null ? "minecraft:overworld" : dimensionId;
        this.pos = pos == null ? BlockPos.ORIGIN : pos.toImmutable();
        this.ownerUuid = ownerUuid == null ? new UUID(0L, 0L) : ownerUuid;
        this.ownerName = ownerName == null ? "Unknown" : ownerName;
        this.title = title == null || title.isBlank() ? "Dabloon Store" : title;
        this.discoveryEnabled = discoveryEnabled;
        this.hologramColor = hologramColor;
        this.musicId = musicId == null ? DabloonStoreMusic.NONE : musicId;
        this.hologramStack = hologramStack == null ? ItemStack.EMPTY : hologramStack.copy();
        this.entries = new ArrayList<>(entries == null ? List.of() : entries);
        this.sales = new ArrayList<>(sales == null ? List.of() : sales);
    }

    public void write(PacketByteBuf buf) {
        buf.writeString(dimensionId, 128);
        buf.writeBlockPos(pos);
        buf.writeUuid(ownerUuid);
        buf.writeString(ownerName, 64);
        buf.writeString(title, 64);
        buf.writeBoolean(discoveryEnabled);
        buf.writeInt(hologramColor);
        buf.writeString(musicId, 128);
        buf.writeItemStack(hologramStack);

        buf.writeVarInt(entries.size());
        for (DabloonStoreEntry entry : entries) {
            entry.write(buf);
        }

        buf.writeVarInt(sales.size());
        for (DabloonStoreSale sale : sales) {
            sale.write(buf);
        }
    }

    public static DabloonStoreSnapshot read(PacketByteBuf buf) {
        String dimensionId = buf.readString(128);
        BlockPos pos = buf.readBlockPos();
        UUID ownerUuid = buf.readUuid();
        String ownerName = buf.readString(64);
        String title = buf.readString(64);
        boolean discovery = buf.readBoolean();
        int color = buf.readInt();
        String musicId = buf.readString(128);
        ItemStack hologramStack = buf.readItemStack();

        int entryCount = buf.readVarInt();
        List<DabloonStoreEntry> entries = new ArrayList<>();
        for (int i = 0; i < entryCount; i++) {
            entries.add(DabloonStoreEntry.read(buf));
        }

        int saleCount = buf.readVarInt();
        List<DabloonStoreSale> sales = new ArrayList<>();
        for (int i = 0; i < saleCount; i++) {
            sales.add(DabloonStoreSale.read(buf));
        }

        return new DabloonStoreSnapshot(dimensionId, pos, ownerUuid, ownerName, title, discovery, color, musicId, hologramStack, entries, sales);
    }

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("DimensionId", dimensionId);
        nbt.putLong("Pos", pos.asLong());
        nbt.putUuid("OwnerUuid", ownerUuid);
        nbt.putString("OwnerName", ownerName);
        nbt.putString("Title", title);
        nbt.putBoolean("DiscoveryEnabled", discoveryEnabled);
        nbt.putInt("HologramColor", hologramColor);
        nbt.putString("MusicId", musicId);
        if (!hologramStack.isEmpty()) {
            nbt.put("HologramStack", hologramStack.writeNbt(new NbtCompound()));
        }

        NbtList entryList = new NbtList();
        for (DabloonStoreEntry entry : entries) {
            entryList.add(entry.toNbt());
        }
        nbt.put("Entries", entryList);

        NbtList saleList = new NbtList();
        for (DabloonStoreSale sale : sales) {
            saleList.add(sale.toNbt());
        }
        nbt.put("Sales", saleList);

        return nbt;
    }

    public static DabloonStoreSnapshot fromNbt(NbtCompound nbt) {
        List<DabloonStoreEntry> entries = new ArrayList<>();
        NbtList entryList = nbt.getList("Entries", 10);
        for (int i = 0; i < entryList.size(); i++) {
            entries.add(DabloonStoreEntry.fromNbt(entryList.getCompound(i)));
        }

        List<DabloonStoreSale> sales = new ArrayList<>();
        NbtList saleList = nbt.getList("Sales", 10);
        for (int i = 0; i < saleList.size(); i++) {
            sales.add(DabloonStoreSale.fromNbt(saleList.getCompound(i)));
        }

        ItemStack hologramStack = nbt.contains("HologramStack") ? ItemStack.fromNbt(nbt.getCompound("HologramStack")) : ItemStack.EMPTY;

        return new DabloonStoreSnapshot(
                nbt.getString("DimensionId"),
                BlockPos.fromLong(nbt.getLong("Pos")),
                nbt.getUuid("OwnerUuid"),
                nbt.getString("OwnerName"),
                nbt.getString("Title"),
                nbt.getBoolean("DiscoveryEnabled"),
                nbt.getInt("HologramColor"),
                nbt.getString("MusicId"),
                hologramStack,
                entries,
                sales
        );
    }
}
