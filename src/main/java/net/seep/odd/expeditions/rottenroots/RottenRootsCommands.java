package net.seep.odd.expeditions.rottenroots;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.seep.odd.expeditions.Expeditions;
import net.seep.odd.expeditions.portal.ExpeditionPortalEntity;

public final class RottenRootsCommands {
    private RottenRootsCommands() {}

    public static void register(CommandDispatcher<ServerCommandSource> d) {
        d.register(CommandManager.literal("rottenroots")
                .requires(src -> src.hasPermissionLevel(2))

                .then(CommandManager.literal("portal")
                        .executes(ctx -> spawnPortal(ctx.getSource(), 10))
                        .then(CommandManager.argument("seconds", IntegerArgumentType.integer(1, 120))
                                .executes(ctx -> spawnPortal(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "seconds")))
                        )
                )

                .then(CommandManager.literal("tp")
                        .executes(ctx -> tpToDimension(ctx.getSource(), null))
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .executes(ctx -> tpToDimension(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player")))
                        )
                )

                .then(CommandManager.literal("return")
                        .executes(ctx -> returnToOverworld(ctx.getSource(), null))
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .executes(ctx -> returnToOverworld(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player")))
                        )
                )

                .then(CommandManager.literal("overworld")
                        .executes(ctx -> returnToOverworld(ctx.getSource(), null))
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .executes(ctx -> returnToOverworld(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player")))
                        )
                )
        );
    }

    public static boolean teleportPlayerToRottenRoots(ServerPlayerEntity p) {
        if (p == null) return false;

        MinecraftServer server = p.getServer();
        if (server == null) {
            p.sendMessage(Text.literal("Server unavailable."), true);
            return false;
        }

        ServerWorld dst = server.getWorld(Expeditions.ROTTEN_ROOTS_WORLD);
        if (dst == null) {
            p.sendMessage(Text.literal("Rotten Roots world is not loaded / missing JSON."), true);
            return false;
        }

        p.stopRiding();
        p.setVelocity(Vec3d.ZERO);
        p.fallDistance = 0f;
        p.teleport(dst, 0.5, 120.0, 0.5, p.getYaw(), p.getPitch());
        return true;
    }

    public static boolean returnPlayerToOverworld(ServerPlayerEntity p) {
        if (p == null) return false;

        MinecraftServer server = p.getServer();
        if (server == null) {
            p.sendMessage(Text.literal("Server unavailable."), true);
            return false;
        }

        ServerWorld overworld = server.getWorld(World.OVERWORLD);
        if (overworld == null) {
            p.sendMessage(Text.literal("Overworld is not loaded."), true);
            return false;
        }

        BlockPos spawn = overworld.getSpawnPos();
        int x = spawn.getX();
        int z = spawn.getZ();

        int y = Math.max(
                spawn.getY(),
                overworld.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z)
        );

        p.stopRiding();
        p.setVelocity(Vec3d.ZERO);
        p.fallDistance = 0f;
        p.teleport(overworld, x + 0.5, y + 0.1, z + 0.5, p.getYaw(), p.getPitch());
        return true;
    }

    private static int tpToDimension(ServerCommandSource src, ServerPlayerEntity target) {
        ServerPlayerEntity p = target != null ? target : src.getPlayer();
        if (p == null) {
            src.sendError(Text.literal("Player-only command."));
            return 0;
        }

        if (!teleportPlayerToRottenRoots(p)) {
            src.sendError(Text.literal("Failed to teleport to Rotten Roots."));
            return 0;
        }

        src.sendFeedback(() -> Text.literal("Teleported " + p.getName().getString() + " to Rotten Roots."), true);
        return 1;
    }

    private static int returnToOverworld(ServerCommandSource src, ServerPlayerEntity target) {
        ServerPlayerEntity p = target != null ? target : src.getPlayer();
        if (p == null) {
            src.sendError(Text.literal("Player-only command."));
            return 0;
        }

        if (!returnPlayerToOverworld(p)) {
            src.sendError(Text.literal("Failed to return to the Overworld."));
            return 0;
        }

        src.sendFeedback(() -> Text.literal("Returned " + p.getName().getString() + " to the Overworld."), true);
        return 1;
    }

    private static int spawnPortal(ServerCommandSource src, int seconds) {
        ServerPlayerEntity p = src.getPlayer();
        if (p == null) {
            src.sendError(Text.literal("Player-only command."));
            return 0;
        }

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
