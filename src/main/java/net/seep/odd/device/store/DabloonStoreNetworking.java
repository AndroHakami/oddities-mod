package net.seep.odd.device.store;

import java.util.List;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.seep.odd.Oddities;
import net.seep.odd.block.DabloonStoreBlockEntity;

public final class DabloonStoreNetworking {
    private DabloonStoreNetworking() {}

    public static final Identifier C2S_REQUEST_BLOCK_SYNC =
            new Identifier(Oddities.MOD_ID, "dabloon_store_request_block_sync");
    public static final Identifier S2C_BLOCK_SYNC =
            new Identifier(Oddities.MOD_ID, "dabloon_store_block_sync");

    public static final Identifier C2S_REQUEST_APP_SYNC =
            new Identifier(Oddities.MOD_ID, "dabloon_store_request_app_sync");
    public static final Identifier S2C_APP_SYNC =
            new Identifier(Oddities.MOD_ID, "dabloon_store_app_sync");

    public static final Identifier C2S_UPDATE_SETTINGS =
            new Identifier(Oddities.MOD_ID, "dabloon_store_update_settings");
    public static final Identifier C2S_ADD_LISTING =
            new Identifier(Oddities.MOD_ID, "dabloon_store_add_listing");
    public static final Identifier C2S_REMOVE_LISTING =
            new Identifier(Oddities.MOD_ID, "dabloon_store_remove_listing");
    public static final Identifier C2S_UPDATE_LISTING =
            new Identifier(Oddities.MOD_ID, "dabloon_store_update_listing");
    public static final Identifier C2S_SET_HOLOGRAM =
            new Identifier(Oddities.MOD_ID, "dabloon_store_set_hologram");
    public static final Identifier C2S_CLEAR_HOLOGRAM =
            new Identifier(Oddities.MOD_ID, "dabloon_store_clear_hologram");
    public static final Identifier C2S_BUY =
            new Identifier(Oddities.MOD_ID, "dabloon_store_buy");

    public static void init() {
        ServerLifecycleEvents.SERVER_STARTED.register(DabloonStoreManager::load);
        ServerLifecycleEvents.SERVER_STOPPING.register(DabloonStoreManager::save);

        ServerPlayNetworking.registerGlobalReceiver(C2S_REQUEST_BLOCK_SYNC, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            server.execute(() -> sendBlockSync(player, pos));
        });

        ServerPlayNetworking.registerGlobalReceiver(C2S_REQUEST_APP_SYNC, (server, player, handler, buf, responseSender) ->
                server.execute(() -> sendAppSync(player)));

        ServerPlayNetworking.registerGlobalReceiver(C2S_UPDATE_SETTINGS, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            String title = buf.readString(64);
            boolean discovery = buf.readBoolean();
            int color = buf.readInt();
            server.execute(() -> mutate(server, player, pos, be -> be.updateSettings(player, title, discovery, color)));
        });

        ServerPlayNetworking.registerGlobalReceiver(C2S_ADD_LISTING, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            int inventorySlot = buf.readVarInt();
            server.execute(() -> mutate(server, player, pos, be -> be.addListingFromInventory(player, inventorySlot)));
        });

        ServerPlayNetworking.registerGlobalReceiver(C2S_REMOVE_LISTING, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            int index = buf.readVarInt();
            server.execute(() -> mutate(server, player, pos, be -> be.removeListing(player, index)));
        });

        ServerPlayNetworking.registerGlobalReceiver(C2S_UPDATE_LISTING, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            int index = buf.readVarInt();
            String title = buf.readString(DabloonStoreEntry.TITLE_MAX_LEN);
            String desc = buf.readString(DabloonStoreEntry.DESC_MAX_LEN);
            int price = buf.readVarInt();
            server.execute(() -> mutate(server, player, pos, be -> be.updateListingMeta(player, index, title, desc, price)));
        });

        ServerPlayNetworking.registerGlobalReceiver(C2S_SET_HOLOGRAM, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            int inventorySlot = buf.readVarInt();
            server.execute(() -> mutate(server, player, pos, be -> be.setHologramFromInventory(player, inventorySlot)));
        });

        ServerPlayNetworking.registerGlobalReceiver(C2S_CLEAR_HOLOGRAM, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            server.execute(() -> mutate(server, player, pos, be -> be.removeHologram(player)));
        });

        ServerPlayNetworking.registerGlobalReceiver(C2S_BUY, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            int index = buf.readVarInt();
            server.execute(() -> mutate(server, player, pos, be -> be.buy(player, index)));
        });
    }

    public static void sendBlockSync(ServerPlayerEntity player, BlockPos pos) {
        if (!(player.getWorld() instanceof ServerWorld serverWorld)) {
            return;
        }
        if (!(serverWorld.getBlockEntity(pos) instanceof DabloonStoreBlockEntity be)) {
            return;
        }

        PacketByteBuf out = PacketByteBufs.create();
        out.writeBlockPos(pos);
        out.writeBoolean(be.isOwner(player));
        be.toSnapshot(serverWorld, pos).write(out);
        ServerPlayNetworking.send(player, S2C_BLOCK_SYNC, out);
    }

    public static void sendAppSync(ServerPlayerEntity player) {
        PacketByteBuf out = PacketByteBufs.create();

        List<DabloonStoreSnapshot> discoverable = DabloonStoreManager.discoverableStores();
        List<DabloonStoreSnapshot> owned = DabloonStoreManager.ownedStores(player.getUuid());

        out.writeVarInt(discoverable.size());
        for (DabloonStoreSnapshot snapshot : discoverable) {
            snapshot.write(out);
        }

        out.writeVarInt(owned.size());
        for (DabloonStoreSnapshot snapshot : owned) {
            snapshot.write(out);
        }

        ServerPlayNetworking.send(player, S2C_APP_SYNC, out);
    }

    public static void broadcastAppSync(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            sendAppSync(player);
        }
    }

    private interface StoreMutation {
        String run(DabloonStoreBlockEntity be);
    }

    private static void mutate(MinecraftServer server, ServerPlayerEntity player, BlockPos pos, StoreMutation mutation) {
        if (!(player.getWorld() instanceof ServerWorld serverWorld)) {
            return;
        }
        if (!(serverWorld.getBlockEntity(pos) instanceof DabloonStoreBlockEntity be)) {
            player.sendMessage(Text.literal("That store no longer exists."), true);
            return;
        }

        String error = mutation.run(be);
        if (error != null && !error.isBlank()) {
            player.sendMessage(Text.literal(error), true);
        }

        sendBlockSync(player, pos);
        broadcastAppSync(server);
    }
}