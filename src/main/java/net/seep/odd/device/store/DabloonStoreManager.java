package net.seep.odd.device.store;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.seep.odd.block.DabloonStoreBlockEntity;

public final class DabloonStoreManager {
    private DabloonStoreManager() {}

    private static final Map<String, DabloonStoreSnapshot> STORES = new HashMap<>();

    public static void load(MinecraftServer server) {
        STORES.clear();

        Path file = saveFile(server);
        if (!Files.exists(file)) return;

        try (InputStream in = Files.newInputStream(file)) {
            NbtCompound root = NbtIo.readCompressed(in);
            if (root == null) return;

            NbtList list = root.getList("Stores", 10);
            for (int i = 0; i < list.size(); i++) {
                DabloonStoreSnapshot snapshot = DabloonStoreSnapshot.fromNbt(list.getCompound(i));
                STORES.put(key(snapshot.dimensionId, snapshot.pos), snapshot);
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
            for (DabloonStoreSnapshot snapshot : STORES.values()) {
                list.add(snapshot.toNbt());
            }
            root.put("Stores", list);

            try (OutputStream out = Files.newOutputStream(saveFile(server))) {
                NbtIo.writeCompressed(root, out);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void upsert(ServerWorld world, BlockPos pos, DabloonStoreBlockEntity be) {
        if (world == null || pos == null || be == null) return;
        STORES.put(key(world, pos), be.toSnapshot(world, pos));
        save(world.getServer());
    }

    public static void remove(ServerWorld world, BlockPos pos) {
        if (world == null || pos == null) return;
        STORES.remove(key(world, pos));
        save(world.getServer());
    }

    public static List<DabloonStoreSnapshot> discoverableStores() {
        List<DabloonStoreSnapshot> out = new ArrayList<>();
        for (DabloonStoreSnapshot snapshot : STORES.values()) {
            if (snapshot.discoveryEnabled) {
                out.add(snapshot);
            }
        }
        out.sort(Comparator.comparing((DabloonStoreSnapshot s) -> s.title.toLowerCase()).thenComparing(s -> s.ownerName.toLowerCase()));
        return out;
    }

    public static List<DabloonStoreSnapshot> ownedStores(UUID ownerUuid) {
        List<DabloonStoreSnapshot> out = new ArrayList<>();
        for (DabloonStoreSnapshot snapshot : STORES.values()) {
            if (snapshot.ownerUuid.equals(ownerUuid)) {
                out.add(snapshot);
            }
        }
        out.sort(Comparator.comparing((DabloonStoreSnapshot s) -> s.title.toLowerCase()));
        return out;
    }

    public static DabloonStoreSnapshot get(String dimensionId, BlockPos pos) {
        return STORES.get(key(dimensionId, pos));
    }

    private static String key(ServerWorld world, BlockPos pos) {
        return key(world.getRegistryKey().getValue().toString(), pos);
    }

    private static String key(String dimensionId, BlockPos pos) {
        return dimensionId + "|" + pos.asLong();
    }

    private static Path saveDir(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve("odd_dabloon_store");
    }

    private static Path saveFile(MinecraftServer server) {
        return saveDir(server).resolve("stores.nbt");
    }
}
