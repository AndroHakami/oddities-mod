package net.seep.odd.abilities.buddymorph;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.CommandOutput;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

/** Runs Identity commands silently. */
public final class IdentityCompat {
    private IdentityCompat() {}

    private static final boolean HAS_IDENTITY =
            FabricLoader.getInstance().isModLoaded("identity")
                    || FabricLoader.getInstance().isModLoaded("identityfix")
                    || FabricLoader.getInstance().isModLoaded("identity-fix");

    public static <T extends LivingEntity> boolean morph(ServerPlayerEntity sp, net.minecraft.entity.EntityType<T> type) {
        if (!HAS_IDENTITY) return false;
        String id = Registries.ENTITY_TYPE.getId(type).toString();
        // EXACT command requested:
        // /identity equip @p <namespace:id>
        return runAsPlayerSilently(sp, "identity equip @p " + id);
    }

    public static boolean unmorph(ServerPlayerEntity sp) {
        if (!HAS_IDENTITY) return false;
        // EXACT revert command:
        // /identity unequip @p
        return runAsPlayerSilently(sp, "identity unequip @p");
    }

    private static boolean runAsPlayerSilently(ServerPlayerEntity sp, String cmd) {
        try {
            var srv = sp.getServer();
            ServerCommandSource src = new ServerCommandSource(
                    CommandOutput.DUMMY, sp.getPos(), sp.getRotationClient(),
                    sp.getServerWorld(), 4,
                    sp.getName().getString(), sp.getDisplayName(), srv, sp
            ).withSilent(); // suppress feedback
            srv.getCommandManager().executeWithPrefix(src, cmd);
            return true;
        } catch (Throwable ignored) {
            try {
                sp.server.getCommandManager().executeWithPrefix(sp.getCommandSource().withSilent(), cmd);
                return true;
            } catch (Throwable ignored2) {
                return false;
            }
        }
    }
}
