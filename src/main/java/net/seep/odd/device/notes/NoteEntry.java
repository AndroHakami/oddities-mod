package net.seep.odd.device.notes;

import java.util.UUID;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;

public final class NoteEntry {
    public final UUID id;
    public String content;
    public final long createdAt;
    public long updatedAt;

    public NoteEntry(UUID id, String content, long createdAt, long updatedAt) {
        this.id = id;
        this.content = content;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public void write(PacketByteBuf buf) {
        buf.writeUuid(id);
        buf.writeString(content, NotesManager.NOTE_MAX_LEN);
        buf.writeLong(createdAt);
        buf.writeLong(updatedAt);
    }

    public static NoteEntry read(PacketByteBuf buf) {
        return new NoteEntry(
                buf.readUuid(),
                buf.readString(NotesManager.NOTE_MAX_LEN),
                buf.readLong(),
                buf.readLong()
        );
    }

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putUuid("Id", id);
        nbt.putString("Content", content);
        nbt.putLong("CreatedAt", createdAt);
        nbt.putLong("UpdatedAt", updatedAt);
        return nbt;
    }

    public static NoteEntry fromNbt(NbtCompound nbt) {
        return new NoteEntry(
                nbt.getUuid("Id"),
                nbt.getString("Content"),
                nbt.getLong("CreatedAt"),
                nbt.getLong("UpdatedAt")
        );
    }
}