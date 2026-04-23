package net.seep.odd.device.guild;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;

public final class GuildTeam {
    public final UUID id;
    public final UUID ownerUuid;
    public final long createdAt;

    public String name;
    public String prefix;
    public String colorId;
    public String notes;
    public final List<GuildMemberData> members = new ArrayList<>();

    public GuildTeam(UUID id, UUID ownerUuid, long createdAt, String name, String prefix, String colorId, String notes) {
        this.id = id;
        this.ownerUuid = ownerUuid;
        this.createdAt = createdAt;
        this.name = name == null ? "" : name;
        this.prefix = prefix == null ? "" : prefix;
        this.colorId = colorId == null ? GuildColorOption.AZURE.id() : colorId;
        this.notes = notes == null ? "" : notes;
    }

    public GuildMemberData findMember(UUID uuid) {
        for (GuildMemberData member : members) {
            if (member.uuid.equals(uuid)) {
                return member;
            }
        }
        return null;
    }

    public boolean isMember(UUID uuid) {
        return findMember(uuid) != null;
    }

    public void write(PacketByteBuf buf) {
        buf.writeUuid(id);
        buf.writeUuid(ownerUuid);
        buf.writeLong(createdAt);
        buf.writeString(name, GuildManager.NAME_MAX_LEN);
        buf.writeString(prefix, GuildManager.PREFIX_MAX_LEN);
        buf.writeString(colorId, 24);
        buf.writeString(notes, GuildManager.NOTES_MAX_LEN);

        buf.writeVarInt(members.size());
        for (GuildMemberData member : members) {
            member.write(buf);
        }
    }

    public static GuildTeam read(PacketByteBuf buf) {
        GuildTeam team = new GuildTeam(
                buf.readUuid(),
                buf.readUuid(),
                buf.readLong(),
                buf.readString(GuildManager.NAME_MAX_LEN),
                buf.readString(GuildManager.PREFIX_MAX_LEN),
                buf.readString(24),
                buf.readString(GuildManager.NOTES_MAX_LEN)
        );

        int count = buf.readVarInt();
        for (int i = 0; i < count; i++) {
            team.members.add(GuildMemberData.read(buf));
        }
        return team;
    }

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putUuid("Id", id);
        nbt.putUuid("OwnerUuid", ownerUuid);
        nbt.putLong("CreatedAt", createdAt);
        nbt.putString("Name", name);
        nbt.putString("Prefix", prefix);
        nbt.putString("ColorId", colorId);
        nbt.putString("Notes", notes);

        NbtList membersTag = new NbtList();
        for (GuildMemberData member : members) {
            membersTag.add(member.toNbt());
        }
        nbt.put("Members", membersTag);
        return nbt;
    }

    public static GuildTeam fromNbt(NbtCompound nbt) {
        GuildTeam team = new GuildTeam(
                nbt.getUuid("Id"),
                nbt.getUuid("OwnerUuid"),
                nbt.getLong("CreatedAt"),
                nbt.getString("Name"),
                nbt.getString("Prefix"),
                nbt.contains("ColorId") ? nbt.getString("ColorId") : GuildColorOption.AZURE.id(),
                nbt.contains("Notes") ? nbt.getString("Notes") : ""
        );

        NbtList membersTag = nbt.getList("Members", 10);
        for (int i = 0; i < membersTag.size(); i++) {
            team.members.add(GuildMemberData.fromNbt((NbtCompound) membersTag.get(i)));
        }
        return team;
    }
}
