package net.seep.odd.block;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.seep.odd.Oddities;
import net.seep.odd.device.bank.DabloonBankManager;
import net.seep.odd.device.store.DabloonStoreEntry;
import net.seep.odd.device.store.DabloonStoreManager;
import net.seep.odd.device.store.DabloonStoreMusic;
import net.seep.odd.device.store.DabloonStoreSale;
import net.seep.odd.device.store.DabloonStoreSnapshot;

public final class DabloonStoreBlockEntity extends BlockEntity {
    public static final int MAX_LISTINGS = 9;
    public static final int MAX_SALES = 128;

    private static final double SHOW_DISTANCE = 4.5D;
    private static final Identifier DEFAULT_HOLOGRAM_ID = new Identifier(Oddities.MOD_ID, "dabloon");

    private UUID ownerUuid = new UUID(0L, 0L);
    private String ownerName = "";
    private String storeTitle = "Dabloon Store";
    private boolean discoveryEnabled = false;
    private int hologramColor = 0x72F4FF;
    private String musicId = DabloonStoreMusic.NONE;
    private ItemStack hologramStack = ItemStack.EMPTY;

    private final List<DabloonStoreEntry> listings = new ArrayList<>();
    private final List<DabloonStoreSale> sales = new ArrayList<>();

    private float hologramProgress = 0.0f;
    private float prevHologramProgress = 0.0f;
    private float spinDegrees = 0.0f;
    private float prevSpinDegrees = 0.0f;

    public DabloonStoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.DABLOON_STORE_BE, pos, state);
    }

    public static void clientTick(World world, BlockPos pos, BlockState state, DabloonStoreBlockEntity be) {
        be.prevHologramProgress = be.hologramProgress;
        be.prevSpinDegrees = be.spinDegrees;

        PlayerEntity nearest = world.getClosestPlayer(
                pos.getX() + 0.5D,
                pos.getY() + 0.5D,
                pos.getZ() + 0.5D,
                SHOW_DISTANCE,
                false
        );

        float target = nearest != null ? 1.0f : 0.0f;
        float speed = target > be.hologramProgress ? 0.12f : 0.08f;
        be.hologramProgress = approach(be.hologramProgress, target, speed);

        if (be.hologramProgress > 0.92f) {
            be.spinDegrees = (be.spinDegrees + 0.8f) % 360.0f;
        }
    }

    public void setOwner(PlayerEntity player) {
        if (player == null) return;
        this.ownerUuid = player.getUuid();
        this.ownerName = player.getName().getString();
        markDirtyAndUpdate();
    }

    public boolean isOwner(PlayerEntity player) {
        return player != null && ownerUuid.equals(player.getUuid());
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public String getStoreTitle() {
        return storeTitle;
    }

    public boolean isDiscoveryEnabled() {
        return discoveryEnabled;
    }

    public int getHologramColor() {
        return hologramColor;
    }

    public String getMusicId() {
        return musicId;
    }

    public ItemStack getDisplayHologramStack() {
        if (!hologramStack.isEmpty()) {
            return hologramStack.copy();
        }
        if (Registries.ITEM.containsId(DEFAULT_HOLOGRAM_ID)) {
            return new ItemStack(Registries.ITEM.get(DEFAULT_HOLOGRAM_ID));
        }
        return ItemStack.EMPTY;
    }

    public List<DabloonStoreEntry> getListingsCopy() {
        List<DabloonStoreEntry> out = new ArrayList<>();
        for (DabloonStoreEntry entry : listings) {
            out.add(new DabloonStoreEntry(
                    entry.stock().copy(),
                    entry.title(),
                    entry.description(),
                    entry.pricePerItem()
            ));
        }
        return out;
    }

    public List<DabloonStoreSale> getSalesCopy() {
        return new ArrayList<>(sales);
    }

    public String updateSettings(ServerPlayerEntity actor, String title, boolean discoveryEnabled, int hologramColor) {
        if (!isOwner(actor)) {
            return "Only the owner can edit this store.";
        }

        this.storeTitle = sanitizeTitle(title);
        this.discoveryEnabled = discoveryEnabled;
        this.hologramColor = MathHelper.clamp(hologramColor, 0, 0xFFFFFF);
        this.musicId = DabloonStoreMusic.NONE;
        markDirtyAndUpdate();
        return null;
    }

    public String addListingFromInventory(ServerPlayerEntity actor, int inventorySlot) {
        if (!isOwner(actor)) {
            return "Only the owner can edit this store.";
        }
        if (listings.size() >= MAX_LISTINGS) {
            return "This store already has 9 listings.";
        }
        if (inventorySlot < 0 || inventorySlot >= actor.getInventory().size()) {
            return "That inventory slot is invalid.";
        }

        ItemStack stack = actor.getInventory().getStack(inventorySlot);
        if (stack.isEmpty()) {
            return "Pick a filled inventory slot.";
        }

        ItemStack stored = stack.copy();
        actor.getInventory().setStack(inventorySlot, ItemStack.EMPTY);
        actor.getInventory().markDirty();

        listings.add(new DabloonStoreEntry(stored, stored.getName().getString(), "", 1));
        markDirtyAndUpdate();
        return null;
    }

    public String removeListing(ServerPlayerEntity actor, int index) {
        if (!isOwner(actor)) {
            return "Only the owner can edit this store.";
        }
        if (index < 0 || index >= listings.size()) {
            return "That listing doesn't exist.";
        }

        DabloonStoreEntry removed = listings.remove(index);
        giveOrDrop(actor, removed.stock());
        markDirtyAndUpdate();
        return null;
    }

    public String updateListingMeta(ServerPlayerEntity actor, int index, String title, String description, int pricePerItem) {
        if (!isOwner(actor)) {
            return "Only the owner can edit this store.";
        }
        if (index < 0 || index >= listings.size()) {
            return "That listing doesn't exist.";
        }

        listings.get(index).setMeta(title, description, Math.max(1, pricePerItem));
        markDirtyAndUpdate();
        return null;
    }

    public String setHologramFromInventory(ServerPlayerEntity actor, int inventorySlot) {
        if (!isOwner(actor)) {
            return "Only the owner can edit this store.";
        }
        if (inventorySlot < 0 || inventorySlot >= actor.getInventory().size()) {
            return "That inventory slot is invalid.";
        }

        ItemStack stack = actor.getInventory().getStack(inventorySlot);
        if (stack.isEmpty()) {
            return "Pick a filled inventory slot.";
        }

        if (!hologramStack.isEmpty()) {
            giveOrDrop(actor, hologramStack.copy());
            hologramStack = ItemStack.EMPTY;
        }

        ItemStack one = stack.split(1);
        this.hologramStack = one.copy();
        actor.getInventory().markDirty();
        markDirtyAndUpdate();
        return null;
    }

    public String removeHologram(ServerPlayerEntity actor) {
        if (!isOwner(actor)) {
            return "Only the owner can edit this store.";
        }
        if (hologramStack.isEmpty()) {
            return null;
        }

        giveOrDrop(actor, hologramStack.copy());
        hologramStack = ItemStack.EMPTY;
        markDirtyAndUpdate();
        return null;
    }

    public String buy(ServerPlayerEntity buyer, int listingIndex) {
        if (listingIndex < 0 || listingIndex >= listings.size()) {
            return "That listing doesn't exist.";
        }

        DabloonStoreEntry entry = listings.get(listingIndex);
        if (entry.isEmpty()) {
            listings.remove(listingIndex);
            markDirtyAndUpdate();
            return "That listing is sold out.";
        }

        DabloonBankManager.SpendResult spend = DabloonBankManager.spend(buyer, entry.pricePerItem());
        if (!spend.ok()) {
            return spend.error();
        }

        ItemStack purchased = entry.takeOne();
        if (!purchased.isEmpty()) {
            if (!buyer.getInventory().insertStack(purchased.copy())) {
                buyer.dropItem(purchased.copy(), false);
            }
        }

        DabloonBankManager.addBalance(buyer.getServer(), ownerUuid, entry.pricePerItem());

        sales.add(0, new DabloonStoreSale(
                buyer.getUuid(),
                buyer.getName().getString(),
                System.currentTimeMillis(),
                entry.title(),
                purchased.isEmpty() ? "minecraft:air" : Registries.ITEM.getId(purchased.getItem()).toString(),
                1,
                entry.pricePerItem()
        ));

        while (sales.size() > MAX_SALES) {
            sales.remove(sales.size() - 1);
        }

        if (entry.isEmpty()) {
            listings.remove(listingIndex);
        }

        markDirtyAndUpdate();
        buyer.sendMessage(Text.literal("Bought " + entry.title() + " for " + entry.pricePerItem() + " dabloons."), true);
        return null;
    }

    public void dropContents(ServerWorld world) {
        for (DabloonStoreEntry entry : listings) {
            if (!entry.stock().isEmpty()) {
                ItemEntity entity = new ItemEntity(
                        world,
                        pos.getX() + 0.5D,
                        pos.getY() + 1.0D,
                        pos.getZ() + 0.5D,
                        entry.stock().copy()
                );
                world.spawnEntity(entity);
            }
        }
        listings.clear();

        if (!hologramStack.isEmpty()) {
            ItemEntity entity = new ItemEntity(
                    world,
                    pos.getX() + 0.5D,
                    pos.getY() + 1.0D,
                    pos.getZ() + 0.5D,
                    hologramStack.copy()
            );
            world.spawnEntity(entity);
            hologramStack = ItemStack.EMPTY;
        }
    }

    public DabloonStoreSnapshot toSnapshot(ServerWorld world, BlockPos pos) {
        return new DabloonStoreSnapshot(
                world.getRegistryKey().getValue().toString(),
                pos,
                ownerUuid,
                ownerName,
                storeTitle,
                discoveryEnabled,
                hologramColor,
                musicId,
                getDisplayHologramStack(),
                getListingsCopy(),
                getSalesCopy()
        );
    }

    public float getHologramProgress(float tickDelta) {
        return MathHelper.lerp(tickDelta, prevHologramProgress, hologramProgress);
    }

    public float getSpinDegrees(float tickDelta) {
        return MathHelper.lerp(tickDelta, prevSpinDegrees, spinDegrees);
    }

    @Override
    public void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);

        nbt.putUuid("OwnerUuid", ownerUuid);
        nbt.putString("OwnerName", ownerName);
        nbt.putString("StoreTitle", storeTitle);
        nbt.putBoolean("DiscoveryEnabled", discoveryEnabled);
        nbt.putInt("HologramColor", hologramColor);
        nbt.putString("MusicId", musicId);

        if (!hologramStack.isEmpty()) {
            nbt.put("HologramStack", hologramStack.writeNbt(new NbtCompound()));
        }

        NbtList listingList = new NbtList();
        for (DabloonStoreEntry entry : listings) {
            listingList.add(entry.toNbt());
        }
        nbt.put("Listings", listingList);

        NbtList saleList = new NbtList();
        for (DabloonStoreSale sale : sales) {
            saleList.add(sale.toNbt());
        }
        nbt.put("Sales", saleList);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);

        ownerUuid = nbt.containsUuid("OwnerUuid") ? nbt.getUuid("OwnerUuid") : new UUID(0L, 0L);
        ownerName = nbt.getString("OwnerName");
        storeTitle = sanitizeTitle(nbt.getString("StoreTitle"));
        discoveryEnabled = nbt.getBoolean("DiscoveryEnabled");
        hologramColor = MathHelper.clamp(nbt.getInt("HologramColor"), 0, 0xFFFFFF);
        musicId = DabloonStoreMusic.NONE;
        hologramStack = nbt.contains("HologramStack")
                ? ItemStack.fromNbt(nbt.getCompound("HologramStack"))
                : ItemStack.EMPTY;

        listings.clear();
        NbtList listingList = nbt.getList("Listings", 10);
        for (int i = 0; i < listingList.size() && listings.size() < MAX_LISTINGS; i++) {
            DabloonStoreEntry entry = DabloonStoreEntry.fromNbt(listingList.getCompound(i));
            if (!entry.isEmpty()) {
                listings.add(entry);
            }
        }

        sales.clear();
        NbtList saleList = nbt.getList("Sales", 10);
        for (int i = 0; i < saleList.size(); i++) {
            sales.add(DabloonStoreSale.fromNbt(saleList.getCompound(i)));
        }
    }

    @Override
    public NbtCompound toInitialChunkDataNbt() {
        NbtCompound nbt = new NbtCompound();
        writeNbt(nbt);
        return nbt;
    }

    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    private void markDirtyAndUpdate() {
        markDirty();

        if (world instanceof ServerWorld serverWorld) {
            DabloonStoreManager.upsert(serverWorld, pos, this);

            BlockState state = getCachedState();
            serverWorld.updateListeners(pos, state, state, Block.NOTIFY_LISTENERS);
            serverWorld.getChunkManager().markForUpdate(pos);
        }
    }

    private static float approach(float current, float target, float amount) {
        if (current < target) {
            return Math.min(current + amount, target);
        }
        return Math.max(current - amount, target);
    }

    private static String sanitizeTitle(String title) {
        String value = title == null ? "" : title.trim();
        if (value.isBlank()) value = "Dabloon Store";
        if (value.length() > 64) {
            value = value.substring(0, 64);
        }
        return value;
    }

    private static void giveOrDrop(ServerPlayerEntity actor, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;

        if (!actor.getInventory().insertStack(stack.copy())) {
            actor.dropItem(stack.copy(), false);
        }
    }
}