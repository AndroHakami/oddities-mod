package net.seep.odd.device.notes;

import java.util.List;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;

public final class NotesNetworking {
    private NotesNetworking() {}

    public static final Identifier C2S_REQUEST_SYNC = new Identifier(Oddities.MOD_ID, "notes_request_sync");
    public static final Identifier S2C_SYNC         = new Identifier(Oddities.MOD_ID, "notes_sync");
    public static final Identifier C2S_SAVE_NOTE    = new Identifier(Oddities.MOD_ID, "notes_save_note");
    public static final Identifier C2S_DELETE_NOTE  = new Identifier(Oddities.MOD_ID, "notes_delete_note");

    public static void init() {
        ServerLifecycleEvents.SERVER_STARTED.register(NotesManager::load);
        ServerLifecycleEvents.SERVER_STOPPING.register(NotesManager::save);

        ServerPlayNetworking.registerGlobalReceiver(C2S_REQUEST_SYNC, (server, player, handler, buf, sender) ->
                server.execute(() -> sendSync(player)));

        ServerPlayNetworking.registerGlobalReceiver(C2S_SAVE_NOTE, (server, player, handler, buf, sender) -> {
            UUID noteId = buf.readBoolean() ? buf.readUuid() : null;
            String content = buf.readString(NotesManager.NOTE_MAX_LEN);

            server.execute(() -> {
                String error = NotesManager.saveNote(player, noteId, content);
                if (error != null) {
                    player.sendMessage(Text.literal(error), true);
                }
                sendSync(player);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(C2S_DELETE_NOTE, (server, player, handler, buf, sender) -> {
            UUID noteId = buf.readUuid();

            server.execute(() -> {
                String error = NotesManager.deleteNote(player, noteId);
                if (error != null) {
                    player.sendMessage(Text.literal(error), true);
                }
                sendSync(player);
            });
        });
    }

    public static void sendSync(ServerPlayerEntity player) {
        PacketByteBuf buf = PacketByteBufs.create();
        List<NoteEntry> notes = NotesManager.getNotes(player.getUuid());

        buf.writeVarInt(notes.size());
        for (NoteEntry note : notes) {
            note.write(buf);
        }

        ServerPlayNetworking.send(player, S2C_SYNC, buf);
    }
}