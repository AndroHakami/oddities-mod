package net.seep.odd.device.guild;

import java.util.UUID;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;

public final class GuildMemberData {
    public final UUID uuid;
    public String name;
    public String status;
    public final long joinedAt;
    public boolean online;

    public GuildMemberData(UUID uuid, String name, String status, long joinedAt) {
        this.uuid = uuid;
        this.name = name == null ? "" : name;
        this.status = status == null ? "" : status;
        this.joinedAt = joinedAt;
        this.online = false;
    }

    public void write(PacketByteBuf buf) {
        buf.writeUuid(uuid);
        buf.writeString(name, 64);
        buf.writeString(status, GuildManager.STATUS_MAX_LEN);
        buf.writeLong(joinedAt);
        buf.writeBoolean(online);
    }

    public static GuildMemberData read(PacketByteBuf buf) {
        GuildMemberData member = new GuildMemberData(
                buf.readUuid(),
                buf.readString(64),
                buf.readString(GuildManager.STATUS_MAX_LEN),
                buf.readLong()
        );
        member.online = buf.readBoolean();
        return member;
    }

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putUuid("Uuid", uuid);
        nbt.putString("Name", name);
        nbt.putString("Status", status);
        nbt.putLong("JoinedAt", joinedAt);
        return nbt;
    }

    public static GuildMemberData fromNbt(NbtCompound nbt) {
        return new GuildMemberData(
                nbt.getUuid("Uuid"),
                nbt.getString("Name"),
                nbt.getString("Status"),
                nbt.getLong("JoinedAt")
        );
    }
}
