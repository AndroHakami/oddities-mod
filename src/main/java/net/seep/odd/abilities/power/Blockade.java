// src/main/java/net/seep/odd/abilities/power/Blockade.java
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
import net.seep.odd.abilities.blockade.net.BlockadeNet;
import net.seep.odd.block.ModBlocks;
import net.seep.odd.sound.ModSounds;
import net.seep.odd.status.ModStatusEffects;

import java.util.Map;
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
    private static final Set<UUID> ACTIVE = ConcurrentHashMap.newKeySet();
    private static boolean tickRegistered = false;
    private static final int PLACE_INTERVAL_TICKS = 1;
    private static int placeTicker = 0;

    private static final Map<UUID, Long> WARN_UNTIL = new ConcurrentHashMap<>();

    public Blockade() {
        if (!tickRegistered) {
            tickRegistered = true;

            ServerTickEvents.END_SERVER_TICK.register(server -> {
                if ((++placeTicker % PLACE_INTERVAL_TICKS) != 0) return;

                for (UUID id : ACTIVE) {
                    ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
                    if (player == null) continue;

                    // if player no longer has this power, auto-off (and tell client)
                    var currentId = net.seep.odd.abilities.PowerAPI.get(player);
                    if (!id().equals(currentId)) {
                        ACTIVE.remove(id);
                        BlockadeNet.sendActive(player, false);
                        continue;
                    }

                    // POWERLESS: force off + tell client + skip placing
                    if (isPowerless(player)) {
                        if (ACTIVE.remove(id)) {
                            BlockadeNet.sendActive(player, false);
                            warnOncePerSec(player, "§cPowerless: Blockade disabled.");

                        }
                        continue;
                    }

                    tryPlacePlatform3x3(player);
                }
            });
        }
    }

    private static boolean isPowerless(ServerPlayerEntity player) {
        return player != null && player.hasStatusEffect(ModStatusEffects.POWERLESS);
    }

    private static void warnOncePerSec(ServerPlayerEntity p, String msg) {
        long now = p.getWorld().getTime();
        long nextOk = WARN_UNTIL.getOrDefault(p.getUuid(), 0L);
        if (now < nextOk) return;
        WARN_UNTIL.put(p.getUuid(), now + 20);
        p.sendMessage(net.minecraft.text.Text.literal(msg), true);
    }

    @Override
    public void activate(ServerPlayerEntity player) {
        UUID id = player.getUuid();

        // POWERLESS: force OFF and deny enabling
        if (isPowerless(player)) {
            boolean wasOn = ACTIVE.remove(id);
            if (wasOn) {
                BlockadeNet.sendActive(player, false);

            }
            warnOncePerSec(player, "§cYou are powerless.");
            return;
        }

        boolean nowOn;
        if (ACTIVE.contains(id)) {
            ACTIVE.remove(id);
            nowOn = false;
        } else {
            ACTIVE.add(id);
            nowOn = true;
        }

        // tell client for satin overlay
        BlockadeNet.sendActive(player, nowOn);


    }

    private static void tryPlacePlatform3x3(ServerPlayerEntity player) {
        ServerWorld world = (ServerWorld) player.getWorld();

        final int feetY = player.getBlockPos().getY() - 1;
        final int cx = player.getBlockPos().getX();
        final int cz = player.getBlockPos().getZ();

        boolean placedAny = false;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos pos = new BlockPos(cx + dx, feetY, cz + dz);
                BlockState state = world.getBlockState(pos);

                if (state.isOf(ModBlocks.CRAPPY_BLOCK)) continue;
                if (!canReplace(state)) continue;

                world.setBlockState(pos, ModBlocks.CRAPPY_BLOCK.getDefaultState(), 3);
                world.scheduleBlockTick(pos, ModBlocks.CRAPPY_BLOCK, 60);
                placedAny = true;
            }
        }

        if (placedAny) {
            BlockPos soundAt = new BlockPos(cx, feetY, cz);
            world.playSound(null, soundAt, ModSounds.CRAPPY_BLOCK_PLACE, SoundCategory.PLAYERS, 0.8f, 1f);
        }
    }

    private static boolean canReplace(BlockState state) {
        if (state.isIn(net.minecraft.registry.tag.BlockTags.LEAVES)) return false;

        if (state.isAir()) return true;
        if (state.isOf(Blocks.GRASS)) return true;
        if (state.isOf(Blocks.TALL_GRASS)) return true;
        if (state.isIn(BlockTags.FLOWERS)) return true;
        if (state.getBlock() instanceof PlantBlock) return true;
        if (state.isOf(Blocks.SNOW)) return true;

        return false;
    }

    @Override public long cooldownTicks() { return 0; }
    @Override public long secondaryCooldownTicks() { return 0; }
}
