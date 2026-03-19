package net.seep.odd.device.social;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;

public final class SocialPost {
    public final UUID id;
    public final UUID authorUuid;
    public final String authorName;
    public final long createdAt;

    public String title;
    public String body;

    public String mainImageUrl; // nullable
    public final List<SocialReply> replies = new ArrayList<>();
    public final Map<UUID, Byte> reactions = new HashMap<>(); // 1=like, -1=dislike

    public SocialPost(UUID id, UUID authorUuid, String authorName, long createdAt, String title, String body, String mainImageUrl) {
        this.id = id;
        this.authorUuid = authorUuid;
        this.authorName = authorName;
        this.createdAt = createdAt;
        this.title = title;
        this.body = body;
        this.mainImageUrl = hasText(mainImageUrl) ? mainImageUrl : null;
    }

    public int likes() {
        int out = 0;
        for (byte vote : reactions.values()) {
            if (vote > 0) out++;
        }
        return out;
    }

    public int dislikes() {
        int out = 0;
        for (byte vote : reactions.values()) {
            if (vote < 0) out++;
        }
        return out;
    }

    public byte voteOf(UUID playerUuid) {
        Byte vote = reactions.get(playerUuid);
        return vote == null ? 0 : vote;
    }

    public boolean hasMainImage() {
        return hasText(mainImageUrl);
    }

    public void write(PacketByteBuf buf) {
        buf.writeUuid(id);
        buf.writeUuid(authorUuid);
        buf.writeString(authorName, 64);
        buf.writeLong(createdAt);

        buf.writeString(title, SocialManager.TITLE_MAX_LEN);
        buf.writeString(body, SocialManager.BODY_MAX_LEN);

        buf.writeBoolean(hasMainImage());
        if (hasMainImage()) {
            buf.writeString(mainImageUrl, SocialManager.IMAGE_URL_MAX_LEN);
        }

        buf.writeVarInt(replies.size());
        for (SocialReply reply : replies) {
            reply.write(buf);
        }

        buf.writeVarInt(reactions.size());
        for (Map.Entry<UUID, Byte> entry : reactions.entrySet()) {
            buf.writeUuid(entry.getKey());
            buf.writeByte(entry.getValue());
        }
    }

    public static SocialPost read(PacketByteBuf buf) {
        SocialPost post = new SocialPost(
                buf.readUuid(),
                buf.readUuid(),
                buf.readString(64),
                buf.readLong(),
                buf.readString(SocialManager.TITLE_MAX_LEN),
                buf.readString(SocialManager.BODY_MAX_LEN),
                buf.readBoolean() ? buf.readString(SocialManager.IMAGE_URL_MAX_LEN) : null
        );

        int replyCount = buf.readVarInt();
        for (int i = 0; i < replyCount; i++) {
            post.replies.add(SocialReply.read(buf));
        }

        int reactionCount = buf.readVarInt();
        for (int i = 0; i < reactionCount; i++) {
            post.reactions.put(buf.readUuid(), buf.readByte());
        }

        return post;
    }

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putUuid("Id", id);
        nbt.putUuid("AuthorUuid", authorUuid);
        nbt.putString("AuthorName", authorName);
        nbt.putLong("CreatedAt", createdAt);

        nbt.putString("Title", title);
        nbt.putString("Body", body);

        if (hasMainImage()) {
            nbt.putString("MainImageUrl", mainImageUrl);
        }

        NbtList replyList = new NbtList();
        for (SocialReply reply : replies) {
            replyList.add(reply.toNbt());
        }
        nbt.put("Replies", replyList);

        NbtList reactionList = new NbtList();
        for (Map.Entry<UUID, Byte> entry : reactions.entrySet()) {
            NbtCompound c = new NbtCompound();
            c.putUuid("Player", entry.getKey());
            c.putByte("Vote", entry.getValue());
            reactionList.add(c);
        }
        nbt.put("Reactions", reactionList);

        return nbt;
    }

    public static SocialPost fromNbt(NbtCompound nbt) {
        SocialPost post = new SocialPost(
                nbt.getUuid("Id"),
                nbt.getUuid("AuthorUuid"),
                nbt.getString("AuthorName"),
                nbt.getLong("CreatedAt"),
                nbt.getString("Title"),
                nbt.getString("Body"),
                nbt.contains("MainImageUrl") ? nbt.getString("MainImageUrl") : null
        );

        NbtList replyList = nbt.getList("Replies", 10);
        for (int i = 0; i < replyList.size(); i++) {
            post.replies.add(SocialReply.fromNbt((NbtCompound) replyList.get(i)));
        }

        NbtList reactionList = nbt.getList("Reactions", 10);
        for (int i = 0; i < reactionList.size(); i++) {
            NbtCompound c = (NbtCompound) reactionList.get(i);
            post.reactions.put(c.getUuid("Player"), c.getByte("Vote"));
        }

        return post;
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}