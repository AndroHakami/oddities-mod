package net.seep.odd.device.social;

import java.util.UUID;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;

public final class SocialReply {
    public final UUID id;
    public final UUID authorUuid;
    public final String authorName;
    public final long createdAt;
    public String body;

    public SocialReply(UUID id, UUID authorUuid, String authorName, long createdAt, String body) {
        this.id = id;
        this.authorUuid = authorUuid;
        this.authorName = authorName;
        this.createdAt = createdAt;
        this.body = body;
    }

    public void write(PacketByteBuf buf) {
        buf.writeUuid(id);
        buf.writeUuid(authorUuid);
        buf.writeString(authorName, 64);
        buf.writeLong(createdAt);
        buf.writeString(body, SocialManager.REPLY_MAX_LEN);
    }

    public static SocialReply read(PacketByteBuf buf) {
        return new SocialReply(
                buf.readUuid(),
                buf.readUuid(),
                buf.readString(64),
                buf.readLong(),
                buf.readString(SocialManager.REPLY_MAX_LEN)
        );
    }

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putUuid("Id", id);
        nbt.putUuid("AuthorUuid", authorUuid);
        nbt.putString("AuthorName", authorName);
        nbt.putLong("CreatedAt", createdAt);
        nbt.putString("Body", body);
        return nbt;
    }

    public static SocialReply fromNbt(NbtCompound nbt) {
        return new SocialReply(
                nbt.getUuid("Id"),
                nbt.getUuid("AuthorUuid"),
                nbt.getString("AuthorName"),
                nbt.getLong("CreatedAt"),
                nbt.getString("Body")
        );
    }
}