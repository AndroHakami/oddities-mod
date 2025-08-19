package net.seep.odd.abilities.power;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PlantBlock;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
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
        return new Identifier("odd", "textures/gui/abilities/blockade_portrait.png"); // add these PNGs
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
            case "primary" -> "Toggle: while active, drops temporary blocks under you that replace grass/plants.";
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
    private static final int PLACE_INTERVAL_TICKS = 1; // every tick; bump to 2 if too dense
    private static int placeTicker = 0;

    public Blockade() {
        // register a single server tick handler (once)
        if (!tickRegistered) {
            tickRegistered = true;
            ServerTickEvents.END_SERVER_TICK.register(server -> {
                placeTicker++;
                if (placeTicker % PLACE_INTERVAL_TICKS != 0) return;

                for (UUID id : ACTIVE) {
                    ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
                    if (player == null) continue;

                    // if player no longer has this power, auto-off
                    var currentId = net.seep.odd.abilities.PowerAPI.get(player);
                    if (!id().equals(currentId)) {
                        ACTIVE.remove(id);
                        continue;
                    }

                    tryPlaceUnder(player);
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

    private void tryPlaceUnder(ServerPlayerEntity player) {
        ServerWorld world = (ServerWorld) player.getWorld();

        // Block directly beneath the player’s feet
        BlockPos pos = player.getBlockPos().down();
        BlockState current = world.getBlockState(pos);

        // Don't spam the same block
        if (current.isOf(ModBlocks.CRAPPY_BLOCK)) return;

        // Only replace air, grass block, plants, tall/short grass, replaceable plants, snow layers
        if (!canReplace(current)) return;

        // Place the block
        world.setBlockState(pos, ModBlocks.CRAPPY_BLOCK.getDefaultState(), 3);

        // (CrappyBlock schedules its own removal in onBlockAdded, but we can be explicit too)
        world.scheduleBlockTick(pos, ModBlocks.CRAPPY_BLOCK, 80);

        // Goofy placement sound
        world.playSound(
                null, pos,
                ModSounds.CRAPPY_BLOCK_PLACE, // goofy squish :)
                SoundCategory.PLAYERS,
                0.8f, 1f
        );
    }

    private boolean canReplace(BlockState state) {
        if (state.isAir()) return true;
        // grass block itself
        if (state.isOf(Blocks.GRASS)) return true;
        // 1-high grass & 2-high tall grass (1.20.1)
        if (state.isOf(Blocks.GRASS) || state.isOf(Blocks.TALL_GRASS)) return true;
        // any plant/flower/bush that’s replaceable
        if (state.isIn(BlockTags.FLOWERS)) return true;
        if (state.getBlock() instanceof PlantBlock) return true;
        // thin snow layers are okay to overwrite
        if (state.isOf(Blocks.SNOW)) return true;

        return false; // don't replace anything else
    }

    // (Optional) small cooldown so toggle isn't spammed in chat
    @Override public long cooldownTicks() { return 0; }
    @Override public long secondaryCooldownTicks() { return 0; }
}