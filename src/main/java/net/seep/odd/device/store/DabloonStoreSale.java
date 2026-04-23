package net.seep.odd.device.store;

import java.util.UUID;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;

public final class DabloonStoreSale {
    public final UUID buyerUuid;
    public final String buyerName;
    public final long soldAt;
    public final String itemName;
    public final String itemId;
    public final int amount;
    public final int totalPrice;

    public DabloonStoreSale(UUID buyerUuid, String buyerName, long soldAt, String itemName, String itemId, int amount, int totalPrice) {
        this.buyerUuid = buyerUuid;
        this.buyerName = buyerName == null ? "Unknown" : buyerName;
        this.soldAt = soldAt;
        this.itemName = itemName == null ? "Item" : itemName;
        this.itemId = itemId == null ? "minecraft:air" : itemId;
        this.amount = Math.max(1, amount);
        this.totalPrice = Math.max(0, totalPrice);
    }

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putUuid("BuyerUuid", buyerUuid);
        nbt.putString("BuyerName", buyerName);
        nbt.putLong("SoldAt", soldAt);
        nbt.putString("ItemName", itemName);
        nbt.putString("ItemId", itemId);
        nbt.putInt("Amount", amount);
        nbt.putInt("TotalPrice", totalPrice);
        return nbt;
    }

    public static DabloonStoreSale fromNbt(NbtCompound nbt) {
        return new DabloonStoreSale(
                nbt.getUuid("BuyerUuid"),
                nbt.getString("BuyerName"),
                nbt.getLong("SoldAt"),
                nbt.getString("ItemName"),
                nbt.getString("ItemId"),
                Math.max(1, nbt.getInt("Amount")),
                Math.max(0, nbt.getInt("TotalPrice"))
        );
    }

    public void write(PacketByteBuf buf) {
        buf.writeUuid(buyerUuid);
        buf.writeString(buyerName, 64);
        buf.writeLong(soldAt);
        buf.writeString(itemName, 80);
        buf.writeString(itemId, 128);
        buf.writeVarInt(amount);
        buf.writeVarInt(totalPrice);
    }

    public static DabloonStoreSale read(PacketByteBuf buf) {
        return new DabloonStoreSale(
                buf.readUuid(),
                buf.readString(64),
                buf.readLong(),
                buf.readString(80),
                buf.readString(128),
                buf.readVarInt(),
                buf.readVarInt()
        );
    }
}
