package net.seep.odd.device.info;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.PacketByteBuf;

public final class InfoPost {
    public final UUID id;
    public final UUID authorUuid;
    public final String authorName;
    public final long createdAt;

    public String source;
    public String title;
    public String body;
    public final List<String> imageUrls = new ArrayList<>();

    public InfoPost(UUID id, UUID authorUuid, String authorName, long createdAt, String source, String title, String body) {
        this.id = id;
        this.authorUuid = authorUuid;
        this.authorName = authorName;
        this.createdAt = createdAt;
        this.source = source == null ? "" : source;
        this.title = title == null ? "" : title;
        this.body = body == null ? "" : body;
    }

    public boolean hasImages() {
        return !imageUrls.isEmpty();
    }

    public void write(PacketByteBuf buf) {
        buf.writeUuid(id);
        buf.writeUuid(authorUuid);
        buf.writeString(authorName, 64);
        buf.writeLong(createdAt);
        buf.writeString(source, InfoManager.SOURCE_MAX_LEN);
        buf.writeString(title, InfoManager.TITLE_MAX_LEN);
        buf.writeString(body, InfoManager.BODY_MAX_LEN);

        buf.writeVarInt(imageUrls.size());
        for (String url : imageUrls) {
            buf.writeString(url, InfoManager.IMAGE_URL_MAX_LEN);
        }
    }

    public static InfoPost read(PacketByteBuf buf) {
        InfoPost post = new InfoPost(
                buf.readUuid(),
                buf.readUuid(),
                buf.readString(64),
                buf.readLong(),
                buf.readString(InfoManager.SOURCE_MAX_LEN),
                buf.readString(InfoManager.TITLE_MAX_LEN),
                buf.readString(InfoManager.BODY_MAX_LEN)
        );

        int imageCount = buf.readVarInt();
        for (int i = 0; i < imageCount; i++) {
            post.imageUrls.add(buf.readString(InfoManager.IMAGE_URL_MAX_LEN));
        }

        return post;
    }

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putUuid("Id", id);
        nbt.putUuid("AuthorUuid", authorUuid);
        nbt.putString("AuthorName", authorName);
        nbt.putLong("CreatedAt", createdAt);
        nbt.putString("Source", source);
        nbt.putString("Title", title);
        nbt.putString("Body", body);

        NbtList images = new NbtList();
        for (String url : imageUrls) {
            images.add(NbtString.of(url));
        }
        nbt.put("Images", images);
        return nbt;
    }

    public static InfoPost fromNbt(NbtCompound nbt) {
        InfoPost post = new InfoPost(
                nbt.getUuid("Id"),
                nbt.getUuid("AuthorUuid"),
                nbt.getString("AuthorName"),
                nbt.getLong("CreatedAt"),
                nbt.getString("Source"),
                nbt.getString("Title"),
                nbt.getString("Body")
        );

        NbtList images = nbt.getList("Images", 8);
        for (int i = 0; i < images.size(); i++) {
            post.imageUrls.add(images.getString(i));
        }

        return post;
    }
}
