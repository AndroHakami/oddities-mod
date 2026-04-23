package net.seep.odd.abilities.core;

import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class CoreLinkManager {
    private CoreLinkManager() {}

    private static final String TAG_PREFIX = "odd_core_pact:";

    public static void setLinkedTarget(ServerPlayerEntity owner, UUID targetUuid) {
        clear(owner);
        owner.addCommandTag(TAG_PREFIX + targetUuid);
    }

    public static Optional<UUID> getLinkedTarget(ServerPlayerEntity owner) {
        for (String tag : owner.getCommandTags()) {
            if (!tag.startsWith(TAG_PREFIX)) continue;
            String raw = tag.substring(TAG_PREFIX.length());
            try {
                return Optional.of(UUID.fromString(raw));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return Optional.empty();
    }

    public static ServerPlayerEntity resolveLinkedPlayer(ServerPlayerEntity owner) {
        Optional<UUID> id = getLinkedTarget(owner);
        if (id.isEmpty() || owner.getServer() == null) return null;
        return owner.getServer().getPlayerManager().getPlayer(id.get());
    }

    public static boolean hasLink(ServerPlayerEntity owner) {
        return getLinkedTarget(owner).isPresent();
    }

    public static void clear(LivingEntity entity) {
        if (entity == null) return;

        Set<String> copy = new HashSet<>(entity.getCommandTags());
        for (String tag : copy) {
            if (tag.startsWith(TAG_PREFIX)) {
                entity.removeScoreboardTag(tag);
            }
        }
    }
}