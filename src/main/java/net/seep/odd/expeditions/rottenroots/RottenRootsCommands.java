package net.seep.odd.expeditions.rottenroots;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.seep.odd.expeditions.Expeditions;
import net.seep.odd.expeditions.portal.ExpeditionPortalEntity;

public final class RottenRootsCommands {
    private RottenRootsCommands() {}

    public static void register(CommandDispatcher<ServerCommandSource> d) {
        d.register(CommandManager.literal("rottenroots")
                .requires(src -> src.hasPermissionLevel(2))

                // /rottenroots portal [seconds]
                .then(CommandManager.literal("portal")
                        .executes(ctx -> spawnPortal(ctx.getSource(), 10))
                        .then(CommandManager.argument("seconds", IntegerArgumentType.integer(1, 120))
                                .executes(ctx -> spawnPortal(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "seconds")))
                        )
                )

                // /rottenroots tp
                .then(CommandManager.literal("tp")
                        .executes(ctx -> tpToDimension(ctx.getSource()))
                )
        );
    }

    private static int tpToDimension(ServerCommandSource src) {
        ServerPlayerEntity p = src.getPlayer();
        if (p == null) { src.sendError(Text.literal("Player-only command.")); return 0; }

        MinecraftServer server = p.getServer();
        ServerWorld dst = server.getWorld(Expeditions.ROTTEN_ROOTS_WORLD);
        if (dst == null) {
            src.sendError(Text.literal("Rotten Roots world is not loaded / missing JSON."));
            return 0;
        }

        // safe-ish spawn: mid-air among roots
        p.teleport(dst, 0.5, 120.0, 0.5, p.getYaw(), p.getPitch());
        src.sendFeedback(() -> Text.literal("Teleported to Rotten Roots."), true);
        return 1;
    }

    private static int spawnPortal(ServerCommandSource src, int seconds) {
        ServerPlayerEntity p = src.getPlayer();
        if (p == null) { src.sendError(Text.literal("Player-only command.")); return 0; }

        var world = p.getWorld();
        Vec3d eye = p.getEyePos();
        Vec3d look = p.getRotationVec(1.0f).normalize();
        Vec3d pos = eye.add(look.multiply(2.0));

        ExpeditionPortalEntity portal = new ExpeditionPortalEntity(ExpeditionPortalEntity.TYPE, world);
        portal.setPosition(pos.x, pos.y, pos.z);


        if (world.spawnEntity(portal)) {
            src.sendFeedback(() -> Text.literal("Spawned portal for " + seconds + "s."), true);
            return 1;
        } else {
            src.sendError(Text.literal("Failed to spawn portal."));
            return 0;
        }
    }
}
