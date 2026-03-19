package net.seep.odd.client.device.notes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.seep.odd.device.notes.NoteEntry;
import net.seep.odd.device.notes.NotesNetworking;

@Environment(EnvType.CLIENT)
public final class NotesClientCache {
    private NotesClientCache() {}

    private static final List<NoteEntry> NOTES = new ArrayList<>();

    public static void initClient() {
        ClientPlayNetworking.registerGlobalReceiver(NotesNetworking.S2C_SYNC, (client, handler, buf, sender) -> {
            List<NoteEntry> incoming = new ArrayList<>();
            int count = buf.readVarInt();
            for (int i = 0; i < count; i++) {
                incoming.add(NoteEntry.read(buf));
            }

            incoming.sort(Comparator.comparingLong((NoteEntry n) -> n.updatedAt).reversed());

            client.execute(() -> {
                NOTES.clear();
                NOTES.addAll(incoming);
            });
        });
    }

    public static List<NoteEntry> notes() {
        return NOTES;
    }
}