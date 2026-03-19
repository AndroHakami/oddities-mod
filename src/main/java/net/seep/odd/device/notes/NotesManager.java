package net.seep.odd.device.notes;

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
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;

public final class NotesManager {
    private NotesManager() {}

    public static final int NOTE_MAX_LEN = 12000;

    private static final Map<UUID, List<NoteEntry>> PLAYER_NOTES = new HashMap<>();

    public static List<NoteEntry> getNotes(UUID playerUuid) {
        List<NoteEntry> notes = new ArrayList<>(PLAYER_NOTES.getOrDefault(playerUuid, List.of()));
        notes.sort(Comparator.comparingLong((NoteEntry n) -> n.updatedAt).reversed());
        return notes;
    }

    public static void load(MinecraftServer server) {
        PLAYER_NOTES.clear();

        Path file = saveFile(server);
        if (!Files.exists(file)) return;

        try (InputStream in = Files.newInputStream(file)) {
            NbtCompound root = NbtIo.readCompressed(in);
            if (root == null) return;

            NbtList players = root.getList("Players", 10);
            for (int i = 0; i < players.size(); i++) {
                NbtCompound playerTag = (NbtCompound) players.get(i);
                UUID playerUuid = playerTag.getUuid("Player");

                List<NoteEntry> notes = new ArrayList<>();
                NbtList noteList = playerTag.getList("Notes", 10);
                for (int j = 0; j < noteList.size(); j++) {
                    notes.add(NoteEntry.fromNbt((NbtCompound) noteList.get(j)));
                }

                notes.sort(Comparator.comparingLong((NoteEntry n) -> n.updatedAt).reversed());
                PLAYER_NOTES.put(playerUuid, notes);
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
            NbtList players = new NbtList();

            for (Map.Entry<UUID, List<NoteEntry>> entry : PLAYER_NOTES.entrySet()) {
                NbtCompound playerTag = new NbtCompound();
                playerTag.putUuid("Player", entry.getKey());

                NbtList noteList = new NbtList();
                for (NoteEntry note : entry.getValue()) {
                    noteList.add(note.toNbt());
                }

                playerTag.put("Notes", noteList);
                players.add(playerTag);
            }

            root.put("Players", players);

            try (OutputStream out = Files.newOutputStream(saveFile(server))) {
                NbtIo.writeCompressed(root, out);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String saveNote(ServerPlayerEntity player, UUID noteId, String content) {
        content = normalize(content);
        if (content.length() > NOTE_MAX_LEN) {
            return "Note is too long.";
        }

        List<NoteEntry> notes = PLAYER_NOTES.computeIfAbsent(player.getUuid(), k -> new ArrayList<>());
        long now = System.currentTimeMillis();

        if (noteId == null) {
            notes.add(new NoteEntry(UUID.randomUUID(), content, now, now));
        } else {
            NoteEntry note = find(player.getUuid(), noteId);
            if (note == null) return "That note doesn't exist.";

            note.content = content;
            note.updatedAt = now;
        }

        notes.sort(Comparator.comparingLong((NoteEntry n) -> n.updatedAt).reversed());
        save(player.getServer());
        return null;
    }

    public static String deleteNote(ServerPlayerEntity player, UUID noteId) {
        List<NoteEntry> notes = PLAYER_NOTES.get(player.getUuid());
        if (notes == null) return "That note doesn't exist.";

        NoteEntry found = null;
        for (NoteEntry note : notes) {
            if (note.id.equals(noteId)) {
                found = note;
                break;
            }
        }

        if (found == null) return "That note doesn't exist.";

        notes.remove(found);
        save(player.getServer());
        return null;
    }

    private static NoteEntry find(UUID playerUuid, UUID noteId) {
        List<NoteEntry> notes = PLAYER_NOTES.get(playerUuid);
        if (notes == null) return null;

        for (NoteEntry note : notes) {
            if (note.id.equals(noteId)) return note;
        }
        return null;
    }

    private static String normalize(String s) {
        if (s == null) return "";
        return s.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static Path saveDir(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve("odd_notes");
    }

    private static Path saveFile(MinecraftServer server) {
        return saveDir(server).resolve("notes.nbt");
    }
}