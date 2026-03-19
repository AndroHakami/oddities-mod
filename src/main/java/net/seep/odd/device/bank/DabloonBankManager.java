package net.seep.odd.device.bank;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.seep.odd.Oddities;

public final class DabloonBankManager {
    private DabloonBankManager() {}

    public static final int MAX_PER_ACTION = 999;
    public static final int DEPOSIT_RANGE_FROM_SPAWN = 50;

    private static final Identifier DABLOON_ID = new Identifier(Oddities.MOD_ID, "dabloon");

    private static final Map<UUID, Integer> BALANCES = new HashMap<>();

    public record BankResult(int amount, int balance, String error) {
        public boolean ok() {
            return error == null;
        }
    }

    public static int getBalance(UUID playerUuid) {
        return BALANCES.getOrDefault(playerUuid, 0);
    }

    public static boolean canDepositAtCurrentLocation(ServerPlayerEntity player) {
        ServerWorld currentWorld = player.getServerWorld();
        BlockPos targetSpawn;

        if (player.getSpawnPointPosition() != null && player.getSpawnPointDimension() != null) {
            if (!player.getSpawnPointDimension().equals(currentWorld.getRegistryKey())) {
                return false;
            }
            targetSpawn = player.getSpawnPointPosition();
        } else {
            ServerWorld overworld = player.getServer().getOverworld();
            if (overworld == null || currentWorld != overworld) {
                return false;
            }
            targetSpawn = overworld.getSpawnPos();
        }

        double maxSq = DEPOSIT_RANGE_FROM_SPAWN * DEPOSIT_RANGE_FROM_SPAWN;
        double distSq = player.squaredDistanceTo(
                targetSpawn.getX() + 0.5,
                targetSpawn.getY() + 0.5,
                targetSpawn.getZ() + 0.5
        );

        return distSq <= maxSq;
    }

    public static void load(MinecraftServer server) {
        BALANCES.clear();

        Path file = saveFile(server);
        if (!Files.exists(file)) return;

        try (InputStream in = Files.newInputStream(file)) {
            NbtCompound root = NbtIo.readCompressed(in);
            if (root == null) return;

            NbtList list = root.getList("Balances", 10);
            for (int i = 0; i < list.size(); i++) {
                NbtCompound tag = (NbtCompound) list.get(i);
                BALANCES.put(tag.getUuid("Player"), tag.getInt("Amount"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void save(MinecraftServer server) {
        try {
            Path dir = saveDir(server);
            Files.createDirectories(dir);

            NbtCompound root = new NbtCompound();
            NbtList list = new NbtList();

            for (Map.Entry<UUID, Integer> entry : BALANCES.entrySet()) {
                NbtCompound tag = new NbtCompound();
                tag.putUuid("Player", entry.getKey());
                tag.putInt("Amount", entry.getValue());
                list.add(tag);
            }

            root.put("Balances", list);

            try (OutputStream out = Files.newOutputStream(saveFile(server))) {
                NbtIo.writeCompressed(root, out);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static BankResult deposit(ServerPlayerEntity player, int requested) {
        if (!canDepositAtCurrentLocation(player)) {
            return new BankResult(0, getBalance(player.getUuid()), "You can only deposit within 50 blocks of your spawn point.");
        }

        Item dabloon = getDabloonItem();
        if (dabloon == null || dabloon == Items.AIR) {
            return new BankResult(0, getBalance(player.getUuid()), "Dabloon item wasn't found.");
        }

        requested = clampRequest(requested);
        if (requested <= 0) {
            return new BankResult(0, getBalance(player.getUuid()), "Enter a valid amount.");
        }

        int onHand = countHeldDabloons(player, dabloon);
        int actual = Math.min(requested, onHand);

        if (actual <= 0) {
            return new BankResult(0, getBalance(player.getUuid()), "You don't have any dabloons to deposit.");
        }

        removeHeldDabloons(player, dabloon, actual);

        int newBalance = getBalance(player.getUuid()) + actual;
        BALANCES.put(player.getUuid(), newBalance);
        save(player.getServer());

        return new BankResult(actual, newBalance, null);
    }

    public static BankResult withdraw(ServerPlayerEntity player, int requested) {
        Item dabloon = getDabloonItem();
        if (dabloon == null || dabloon == Items.AIR) {
            return new BankResult(0, getBalance(player.getUuid()), "Dabloon item wasn't found.");
        }

        requested = clampRequest(requested);
        if (requested <= 0) {
            return new BankResult(0, getBalance(player.getUuid()), "Enter a valid amount.");
        }

        int balance = getBalance(player.getUuid());
        if (balance <= 0) {
            return new BankResult(0, balance, "Your bank is empty.");
        }

        int fit = countMainInventoryCapacity(player, dabloon);
        if (fit <= 0) {
            return new BankResult(0, balance, "You don't have inventory space for dabloons.");
        }

        int actual = Math.min(requested, Math.min(balance, fit));
        int inserted = insertIntoMainInventory(player, dabloon, actual);

        if (inserted <= 0) {
            return new BankResult(0, balance, "Couldn't withdraw any dabloons.");
        }

        int newBalance = balance - inserted;
        BALANCES.put(player.getUuid(), newBalance);
        save(player.getServer());

        return new BankResult(inserted, newBalance, null);
    }

    private static int clampRequest(int requested) {
        return Math.max(0, Math.min(MAX_PER_ACTION, requested));
    }

    private static Item getDabloonItem() {
        return Registries.ITEM.get(DABLOON_ID);
    }

    private static int countHeldDabloons(ServerPlayerEntity player, Item item) {
        int count = 0;

        for (ItemStack stack : player.getInventory().main) {
            if (stack.isOf(item)) {
                count += stack.getCount();
            }
        }

        for (ItemStack stack : player.getInventory().offHand) {
            if (stack.isOf(item)) {
                count += stack.getCount();
            }
        }

        return count;
    }

    private static void removeHeldDabloons(ServerPlayerEntity player, Item item, int amount) {
        int remaining = amount;

        for (int i = 0; i < player.getInventory().main.size() && remaining > 0; i++) {
            ItemStack stack = player.getInventory().main.get(i);
            if (!stack.isOf(item)) continue;

            int take = Math.min(remaining, stack.getCount());
            stack.decrement(take);
            remaining -= take;
        }

        for (int i = 0; i < player.getInventory().offHand.size() && remaining > 0; i++) {
            ItemStack stack = player.getInventory().offHand.get(i);
            if (!stack.isOf(item)) continue;

            int take = Math.min(remaining, stack.getCount());
            stack.decrement(take);
            remaining -= take;
        }

        player.getInventory().markDirty();
    }

    private static int countMainInventoryCapacity(ServerPlayerEntity player, Item item) {
        int capacity = 0;
        int maxStack = item.getMaxCount();

        for (ItemStack stack : player.getInventory().main) {
            if (stack.isEmpty()) {
                capacity += maxStack;
            } else if (stack.isOf(item)) {
                capacity += Math.max(0, maxStack - stack.getCount());
            }
        }

        return capacity;
    }

    private static int insertIntoMainInventory(ServerPlayerEntity player, Item item, int amount) {
        int remaining = amount;
        int maxStack = item.getMaxCount();

        for (int i = 0; i < player.getInventory().main.size() && remaining > 0; i++) {
            ItemStack stack = player.getInventory().main.get(i);
            if (!stack.isOf(item)) continue;

            int room = maxStack - stack.getCount();
            if (room <= 0) continue;

            int add = Math.min(room, remaining);
            stack.increment(add);
            remaining -= add;
        }

        for (int i = 0; i < player.getInventory().main.size() && remaining > 0; i++) {
            ItemStack stack = player.getInventory().main.get(i);
            if (!stack.isEmpty()) continue;

            int add = Math.min(maxStack, remaining);
            player.getInventory().main.set(i, new ItemStack(item, add));
            remaining -= add;
        }

        player.getInventory().markDirty();
        return amount - remaining;
    }

    private static Path saveDir(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve("odd_bank");
    }

    private static Path saveFile(MinecraftServer server) {
        return saveDir(server).resolve("balances.nbt");
    }
}