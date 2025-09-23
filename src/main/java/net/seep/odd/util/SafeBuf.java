package net.seep.odd.util;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/** Null-safe Identifier IO for packets. */
public final class SafeBuf {
    private SafeBuf() {}

    /** Write optional Identifier with a presence flag. */
    public static void writeId(PacketByteBuf buf, @Nullable Identifier id) {
        buf.writeBoolean(id != null);
        if (id != null) buf.writeIdentifier(id);
    }

    /** Read an Identifier written with writeId (may be null). */
    public static @Nullable Identifier readId(PacketByteBuf buf) {
        return buf.readBoolean() ? buf.readIdentifier() : null;
    }
}
