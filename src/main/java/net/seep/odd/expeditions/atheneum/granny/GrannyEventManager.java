package net.seep.odd.expeditions.atheneum.granny;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.seep.odd.entity.granny.GrannyEntity;
import net.seep.odd.entity.ModEntities;
import net.seep.odd.sound.ModSounds;

import java.util.*;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class GrannyEventManager {
    public static final RegistryKey<World> ATHENEUM =
            RegistryKey.of(RegistryKeys.WORLD, new Identifier("odd", "atheneum"));

    private static final int CHARGE_TO_START = 15 * 60 * 20;
    private static final int ACTIVE_TICKS = 60 * 20;

    private static boolean prelude = false;
    private static boolean active = false;
    private static int preludeTick = 0;
    private static int activeTicks = 0;
    private static int charge = 0;
    private static int syncTimer = 0;

    private static final Map<UUID, Float> NOISE = new HashMap<>();

    private GrannyEventManager() {}

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(GrannyEventManager::serverTick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> reset());

        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> onNoise(player, 14.0f));
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!world.isClient()) onNoise(player, 7.0f);
            return ActionResult.PASS;
        });
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!world.isClient()) onNoise(player, 2.0f);
            return ActionResult.PASS;
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("granny")
                    .requires(src -> src.hasPermissionLevel(2))
                    .then(literal("start").executes(ctx -> {
                        forceStart(ctx.getSource().getServer());
                        ctx.getSource().sendFeedback(() -> net.minecraft.text.Text.literal("Started Granny event."), true);
                        return 1;
                    }))
                    .then(literal("stop").executes(ctx -> {
                        endEvent(ctx.getSource().getServer());
                        ctx.getSource().sendFeedback(() -> net.minecraft.text.Text.literal("Stopped Granny event."), true);
                        return 1;
                    }))
                    .then(literal("setexit")
                            .then(argument("pos", net.minecraft.command.argument.BlockPosArgumentType.blockPos()).executes(ctx -> {
                                BlockPos pos = net.minecraft.command.argument.BlockPosArgumentType.getBlockPos(ctx, "pos");
                                GrannyPersistentState.get(ctx.getSource().getServer()).setExitPos(pos);
                                ctx.getSource().sendFeedback(() -> net.minecraft.text.Text.literal("Granny exit set to " + pos.toShortString()), true);
                                return 1;
                            })))
                    .then(literal("status").executes(ctx -> {
                        ctx.getSource().sendFeedback(() -> net.minecraft.text.Text.literal(
                                "prelude=" + prelude + ", active=" + active + ", charge=" + charge + "/" + CHARGE_TO_START
                        ), false);
                        return 1;
                    })));
        });
    }

    public static boolean isAtheneum(World world) {
        return world != null && world.getRegistryKey().equals(ATHENEUM);
    }

    public static boolean isEventRunning() {
        return prelude || active;
    }

    public static float getNoise(PlayerEntity player) {
        return NOISE.getOrDefault(player.getUuid(), 0.0f);
    }

    public static void onNoise(PlayerEntity player, float amount) {
        if (player == null || !isAtheneum(player.getWorld())) return;
        NOISE.merge(player.getUuid(), amount, Float::sum);
    }

    public static void onGrannyAlert(GrannyEntity granny, ServerPlayerEntity player) {
        player.playSound(ModSounds.GRANNY_ALERT, SoundCategory.HOSTILE, 1.0f, 1.0f);
    }

    public static void playGrannyAmbient(GrannyEntity granny) {
        if (!(granny.getWorld() instanceof ServerWorld sw)) return;
        sw.playSound(null, granny.getBlockPos(), ModSounds.GRANNY, SoundCategory.HOSTILE, 1.0f, 0.95f + sw.random.nextFloat() * 0.10f);
    }

    public static void catchPlayer(GrannyEntity granny, ServerPlayerEntity player) {
        ServerWorld sw = player.getServerWorld();
        sw.playSound(null, player.getBlockPos(), ModSounds.GRANNY_CAUGHT, SoundCategory.HOSTILE, 1.2f, 1.0f);
        removeHalfDabloons(player);

        MinecraftServer server = player.getServer();
        ServerWorld overworld = server.getOverworld();
        BlockPos exit = GrannyPersistentState.get(server).getExitPos();

        player.teleport(overworld, exit.getX() + 0.5D, exit.getY(), exit.getZ() + 0.5D, player.getYaw(), player.getPitch());
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 60, 0, false, false, true));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 1, false, false, true));

        NOISE.put(player.getUuid(), 0.0f);
    }

    private static void removeHalfDabloons(ServerPlayerEntity player) {
        Item dabloon = Registries.ITEM.get(new Identifier("odd", "dabloon"));
        if (dabloon == net.minecraft.item.Items.AIR) return;

        PlayerInventory inv = player.getInventory();
        int total = 0;
        for (int i = 0; i < inv.size(); i++) {
            if (inv.getStack(i).isOf(dabloon)) {
                total += inv.getStack(i).getCount();
            }
        }

        int toRemove = total / 2;
        if (toRemove <= 0) return;

        for (int i = 0; i < inv.size() && toRemove > 0; i++) {
            if (!inv.getStack(i).isOf(dabloon)) continue;
            int take = Math.min(inv.getStack(i).getCount(), toRemove);
            inv.getStack(i).decrement(take);
            toRemove -= take;
        }
        player.currentScreenHandler.sendContentUpdates();
    }

    public static void forceStart(MinecraftServer server) {
        if (active || prelude) return;
        startPrelude(server);
    }

    private static void startPrelude(MinecraftServer server) {
        prelude = true;
        active = false;
        preludeTick = 0;
        activeTicks = 0;
        syncToPlayers(server, true);
    }

    private static void startActive(MinecraftServer server) {
        prelude = false;
        active = true;
        activeTicks = ACTIVE_TICKS;
        spawnGrannies(server);
        sendSpawnCue(server);
        syncToPlayers(server, true);
    }

    public static void endEvent(MinecraftServer server) {
        prelude = false;
        active = false;
        preludeTick = 0;
        activeTicks = 0;
        charge = 0;
        NOISE.replaceAll((id, v) -> 0.0f);

        ServerWorld atheneum = server.getWorld(ATHENEUM);
        if (atheneum != null) {
            List<GrannyEntity> list = atheneum.getEntitiesByClass(GrannyEntity.class, new Box(-30000000, -128, -30000000, 30000000, 2048, 30000000), g -> true);
            for (GrannyEntity granny : list) granny.discard();
        }

        syncToPlayers(server, false);
    }

    private static void serverTick(MinecraftServer server) {
        ServerWorld atheneum = server.getWorld(ATHENEUM);
        if (atheneum == null) return;

        List<ServerPlayerEntity> players = atheneum.getPlayers(p -> p.isAlive() && !p.isSpectator());

        NOISE.entrySet().removeIf(e -> server.getPlayerManager().getPlayer(e.getKey()) == null);
        NOISE.replaceAll((uuid, value) -> Math.max(0.0f, value * 0.90f - 0.05f));

        for (ServerPlayerEntity player : players) {
            if (player.isSprinting()) onNoise(player, 0.85f);
            else if (player.getVelocity().horizontalLengthSquared() > 0.01D) onNoise(player, 0.16f);

            if (player.fallDistance > 2.5f) onNoise(player, 1.2f);
        }

        if (!players.isEmpty() && !prelude && !active) {
            float totalNoise = 0.0f;
            for (ServerPlayerEntity player : players) totalNoise += getNoise(player);

            charge += 1 + Math.min(8, Math.round(totalNoise * 0.08f));
            if (charge >= CHARGE_TO_START) {
                startPrelude(server);
            }
        }

        if (prelude) {
            handlePrelude(atheneum, players, server);
        } else if (active) {
            handleActive(atheneum, players, server);
        }

        if (++syncTimer >= 20) {
            syncTimer = 0;
            syncToPlayers(server, prelude || active);
        }
    }

    private static void handlePrelude(ServerWorld atheneum, List<ServerPlayerEntity> players, MinecraftServer server) {
        if (preludeTick == 0 || preludeTick == 20 || preludeTick == 40) {
            float pitch = (preludeTick == 40) ? 0.62f : 1.0f;

            for (ServerPlayerEntity player : players) {
                player.playSound(ModSounds.BELL_RING, SoundCategory.MASTER, 1.5f, pitch);
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 24, 0, false, false, true));
            }
        }

        preludeTick++;
        if (preludeTick >= 44) {
            startActive(server);
        }
    }

    private static void handleActive(ServerWorld atheneum, List<ServerPlayerEntity> players, MinecraftServer server) {
        activeTicks--;

        if (activeTicks % 20 == 0) {
            for (ServerPlayerEntity player : players) {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 60, 0, false, false, true));
            }
        }

        if (activeTicks <= 0) {
            endEvent(server);
        }
    }

    private static void spawnGrannies(MinecraftServer server) {
        ServerWorld atheneum = server.getWorld(ATHENEUM);
        if (atheneum == null) return;

        List<ServerPlayerEntity> players = atheneum.getPlayers(p -> p.isAlive() && !p.isSpectator());
        List<ServerPlayerEntity> anchors = new ArrayList<>();

        for (ServerPlayerEntity player : players) {
            boolean tooClose = false;
            for (ServerPlayerEntity chosen : anchors) {
                if (chosen.squaredDistanceTo(player) <= 15.0D * 15.0D) {
                    tooClose = true;
                    break;
                }
            }
            if (!tooClose) anchors.add(player);
        }

        for (ServerPlayerEntity anchor : anchors) {
            BlockPos spawnPos = findSpawnAround(anchor);
            if (spawnPos == null) continue;

            GrannyEntity granny = ModEntities.GRANNY.create(atheneum);
            if (granny == null) continue;

            granny.refreshPositionAndAngles(spawnPos, atheneum.random.nextFloat() * 360.0f, 0.0f);
            atheneum.spawnEntity(granny);
        }
    }

    private static BlockPos findSpawnAround(ServerPlayerEntity anchor) {
        ServerWorld world = anchor.getServerWorld();
        BlockPos base = anchor.getBlockPos();

        for (int i = 0; i < 24; i++) {
            double angle = world.random.nextDouble() * (Math.PI * 2.0D);
            int radius = 8 + world.random.nextInt(7);
            int x = base.getX() + (int) Math.round(Math.cos(angle) * radius);
            int z = base.getZ() + (int) Math.round(Math.sin(angle) * radius);

            BlockPos pos = findGroundedSpawnBelowCeiling(world, x, z, base.getY());
            if (pos != null) {
                return pos;
            }
        }

        return findGroundedSpawnBelowCeiling(world, base.getX(), base.getZ(), base.getY());
    }

    private static BlockPos findGroundedSpawnBelowCeiling(ServerWorld world, int x, int z, int anchorY) {
        int searchTop = Math.min(world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z), anchorY + 8);
        int searchBottom = Math.max(world.getBottomY() + 1, anchorY - 20);

        for (int floorY = searchTop; floorY >= searchBottom; floorY--) {
            BlockPos floor = new BlockPos(x, floorY, z);
            BlockPos spawn = floor.up();
            BlockPos head = spawn.up();

            if (!world.getBlockState(floor).blocksMovement()) continue;
            if (!world.isAir(spawn) || !world.isAir(head)) continue;

            return spawn;
        }

        return null;
    }

    private static void sendSpawnCue(MinecraftServer server) {
        ServerWorld atheneum = server.getWorld(ATHENEUM);
        if (atheneum == null) return;

        for (ServerPlayerEntity player : atheneum.getPlayers(p -> p.isAlive() && !p.isSpectator())) {
            ServerPlayNetworking.send(player, GrannyNetworking.SPAWN_CUE, PacketByteBufs.create());
        }
    }

    private static void syncToPlayers(MinecraftServer server, boolean activeValue) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeBoolean(activeValue);
            ServerPlayNetworking.send(player, GrannyNetworking.EVENT_STATE, buf);
        }
    }

    private static void reset() {
        prelude = false;
        active = false;
        preludeTick = 0;
        activeTicks = 0;
        charge = 0;
        syncTimer = 0;
        NOISE.clear();
    }
}