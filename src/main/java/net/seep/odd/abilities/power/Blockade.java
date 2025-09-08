package net.seep.odd.abilities.power;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PlantBlock;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.seep.odd.block.ModBlocks;
import net.seep.odd.sound.ModSounds;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Blockade implements Power {
    @Override public String id() { return "blockade"; }

    // --- UI ---
    @Override public String displayName() { return "Blockade"; }
    @Override public String description() { return "Drop temporary platforms under your feet on command."; }
    @Override public String longDescription() {
        return """
               You don’t build— you **spawn** ground beneath your stride. Flip the switch, stride forward,
               and leave a vanishing walkway in your wake. Great for chases, escapes, and dramatic exits.""";
    }
    @Override public Identifier iconTexture(String slot) {
        return new Identifier("odd", "textures/gui/abilities/blockade_portrait.png");
    }
    @Override public Identifier portraitTexture() {
        return new Identifier("odd", "textures/gui/overview/crappy_portrait.png");
    }
    @Override public String slotTitle(String slot) {
        return switch (slot) {
            case "primary" -> "Placement Mode";
            default -> Power.super.slotTitle(slot);
        };
    }
    @Override public String slotDescription(String slot) {
        return switch (slot) {
            case "primary" -> "Toggle: while active, conjures temporary blocks under your feet";
            default -> "";
        };
    }
    @Override public String slotLongDescription(String slot) {
        return switch (slot) {
            case "primary" -> "Activate to start laying a path of temporary blocks beneath you. "
                    + "They’ll overwrite grass and plants, but never solid structures. "
                    + "Each block poofs after a few seconds.";
            default -> "";
        };
    }

    // --- Behavior ---

    // players currently in "placement mode"
    private static final Set<UUID> ACTIVE = ConcurrentHashMap.newKeySet();
    private static boolean tickRegistered = false;

    // how often to attempt placement while active
    private static final int PLACE_INTERVAL_TICKS = 1; // every tick
    private static int placeTicker = 0;

    // ahead placement distance while sprinting (in blocks)
    private static final double AHEAD_DIST = 0.45; // small nudge ahead so sprinting feels smooth

    // despawn delay for the temporary block (ticks)
    private static final int DESPAWN_TICKS = 80;

    public Blockade() {
        // register a single server tick handler (once)
        if (!tickRegistered) {
            tickRegistered = true;
            ServerTickEvents.END_SERVER_TICK.register(server -> {
                if ((++placeTicker % PLACE_INTERVAL_TICKS) != 0) return;

                for (UUID id : ACTIVE) {
                    ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
                    if (player == null) continue;

                    // if player no longer has this power, auto-off
                    var currentId = net.seep.odd.abilities.PowerAPI.get(player);
                    if (!id().equals(currentId)) {
                        ACTIVE.remove(id);
                        continue;
                    }

                    tryPlacePlatform3x3(player);
                }
            });
        }
    }

    // Primary ability toggles placement mode on/off
    @Override
    public void activate(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        boolean nowOn;
        if (ACTIVE.contains(id)) {
            ACTIVE.remove(id);
            nowOn = false;
        } else {
            ACTIVE.add(id);
            nowOn = true;
        }
        player.sendMessage(net.minecraft.text.Text.literal(
                nowOn ? "Block Placement: ON" : "Block Placement: OFF"), true);
    }

    /** Place under feet always; while sprinting also place one block ahead in look direction. */
    private void tryPlacePlatform3x3(ServerPlayerEntity player) {
        ServerWorld world = (ServerWorld) player.getWorld();

        // grid Y to build on: exactly one below the player's feet
        final int feetY = player.getBlockPos().getY() - 1;
        final int cx = player.getBlockPos().getX();
        final int cz = player.getBlockPos().getZ();

        boolean placedAny = false;

        // Offsets to cover a 3x3 centered at (cx,cz)
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos pos = new BlockPos(cx + dx, feetY, cz + dz);
                BlockState state = world.getBlockState(pos);

                // Don't overwrite our own tile; only place into allowed cells
                if (state.isOf(ModBlocks.CRAPPY_BLOCK)) continue;
                if (!canReplace(world, pos, state)) continue;

                world.setBlockState(pos, ModBlocks.CRAPPY_BLOCK.getDefaultState(), 3);
                world.scheduleBlockTick(pos, ModBlocks.CRAPPY_BLOCK, 60);
                placedAny = true;
            }
        }

        // Single sound per tick, if at least one block was placed
        if (placedAny) {
            BlockPos soundAt = new BlockPos(cx, feetY, cz);
            world.playSound(null, soundAt, ModSounds.CRAPPY_BLOCK_PLACE, SoundCategory.PLAYERS, 0.8f, 1f);
        }
    }

    /** Place a single tile if allowed; schedule despawn; play sound. */
    private void placeOneIfAllowed(ServerWorld world, BlockPos pos) {
        BlockState current = world.getBlockState(pos);
        if (current.isOf(ModBlocks.CRAPPY_BLOCK)) return;
        if (!canReplace(world, pos, current)) return;

        world.setBlockState(pos, ModBlocks.CRAPPY_BLOCK.getDefaultState(), 3);
        world.scheduleBlockTick(pos, ModBlocks.CRAPPY_BLOCK, 80);
        world.playSound(null, pos, ModSounds.CRAPPY_BLOCK_PLACE, SoundCategory.PLAYERS, 0.8f, 1f);
    }

    private boolean canReplace(ServerWorld world, BlockPos pos, BlockState state) {
        // hard blocks that should never be replaced
        if (state.isIn(net.minecraft.registry.tag.BlockTags.LEAVES)) return false; // <- covers cherry leaves etc.
        // if you also want to avoid paving liquids, uncomment:
        // if (state.isOf(Blocks.WATER) || state.isOf(Blocks.LAVA)) return false;

        // allowed replacements
        if (state.isAir()) return true;
        if (state.isOf(Blocks.GRASS)) return true;        // short grass
        if (state.isOf(Blocks.TALL_GRASS)) return true;   // tall grass
        if (state.isIn(BlockTags.FLOWERS)) return true;   // flowers
        if (state.getBlock() instanceof PlantBlock) return true; // small plants/bushes/saplings
        if (state.isOf(Blocks.SNOW)) return true;         // thin snow layer

        // everything else: NO
        return false;
    }



    // (Optional) small cooldown so toggle isn't spammed in chat
    @Override public long cooldownTicks() { return 0; }
    @Override public long secondaryCooldownTicks() { return 0; }
}
