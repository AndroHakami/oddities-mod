package net.seep.odd.abilities.power;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PlantBlock;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import net.seep.odd.abilities.blockade.net.BlockadeNet;
import net.seep.odd.block.ModBlocks;
import net.seep.odd.mixin.BlockDisplayEntityAccessor;
import net.seep.odd.sound.ModSounds;
import net.seep.odd.status.ModStatusEffects;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Blockade implements Power {
    private static final Set<UUID> ACTIVE = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, StompState> STOMPING = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> WARN_UNTIL = new ConcurrentHashMap<>();

    private static boolean callbacksRegistered = false;
    private static final int PLACE_INTERVAL_TICKS = 1;
    private static int placeTicker = 0;

    // stomp tuning
    private static final double STOMP_START_DOWNWARD_SPEED = -1.55D;
    private static final double EXTRA_GRAVITY_PER_TICK = 0.34D;
    private static final double MAX_DOWN_SPEED = 4.75D;
    private static final int WIND_SOUND_INTERVAL = 10;

    private static final class StompState {
        double startY;
        double peakY;
        boolean wasAirborne;
        int windSoundCooldown;
        DisplayEntity.BlockDisplayEntity display;
    }

    @Override
    public String id() {
        return "blockade";
    }

    @Override
    public boolean hasSlot(String slot) {
        return "primary".equals(slot) || "secondary".equals(slot);
    }

    @Override
    public String displayName() {
        return "Blockade";
    }


    @Override
    public String description() {
        return "Drop temporary platforms under your feet on command.";
    }

    @Override
    public String longDescription() {
        return """
                The air is your ground, blockade allows you traverse through any situation.
               """;
    }

    @Override
    public Identifier iconTexture(String slot) {
        return switch (slot) {
            case "primary"   -> new Identifier("odd", "textures/gui/abilities/blockade_portrait.png");
            case "secondary" -> new Identifier("odd", "textures/gui/abilities/blockade_stomp.png");
            default          -> new Identifier("odd", "textures/gui/abilities/ability_default.png");
        };
    }

    @Override
    public Identifier portraitTexture() {
        return new Identifier("odd", "textures/gui/overview/crappy_portrait.png");
    }

    @Override
    public String slotTitle(String slot) {
        return switch (slot) {
            case "primary" -> "BLOCK MANIFESATION";
            case "secondary" -> "CRAPPITO STOMPITO";
            default -> Power.super.slotTitle(slot);
        };
    }

    @Override
    public String slotDescription(String slot) {
        return switch (slot) {
            case "primary" -> "Place a bed of temporary blocks under your feet";
            case "secondary" -> "Crash downward with a heavy stomp that scales with fall height";
            default -> "";
        };
    }

    @Override
    public String slotLongDescription(String slot) {
        return switch (slot) {
            case "primary" -> "Place temporary blocks beneath your feet while active.";
            case "secondary" -> "Crash downward with a heavy stomp that scales with fall height.";
            default -> "";
        };
    }

    public Blockade() {
        if (callbacksRegistered) return;
        callbacksRegistered = true;

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if ((++placeTicker % PLACE_INTERVAL_TICKS) == 0) {
                for (UUID id : ACTIVE) {
                    ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
                    if (player == null) {
                        ACTIVE.remove(id);
                        continue;
                    }

                    String currentId = net.seep.odd.abilities.PowerAPI.get(player);
                    if (!id().equals(currentId)) {
                        ACTIVE.remove(id);
                        BlockadeNet.sendActive(player, false);
                        continue;
                    }

                    if (isPowerless(player)) {
                        if (ACTIVE.remove(id)) {
                            BlockadeNet.sendActive(player, false);
                            warnOncePerSec(player, "§cPowerless: Blockade disabled.");
                        }
                        continue;
                    }

                    tryPlacePlatform3x3(player);
                }
            }

            for (Map.Entry<UUID, StompState> entry : STOMPING.entrySet()) {
                UUID id = entry.getKey();
                StompState stomp = entry.getValue();
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);

                if (player == null) {
                    cleanupDisplay(stomp);
                    STOMPING.remove(id);
                    continue;
                }

                String currentId = net.seep.odd.abilities.PowerAPI.get(player);
                if (!id().equals(currentId) || !player.isAlive()) {
                    endStomp(player, stomp);
                    continue;
                }

                if (isPowerless(player)) {
                    endStomp(player, stomp);
                    warnOncePerSec(player, "§cPowerless: Blockade disabled.");
                    continue;
                }

                tickStomp(player, stomp);
            }
        });

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayerEntity player)) return true;
            if (!source.isIn(DamageTypeTags.IS_FALL)) return true;
            return !STOMPING.containsKey(player.getUuid());
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.player;
            UUID id = player.getUuid();

            ACTIVE.remove(id);

            StompState stomp = STOMPING.remove(id);
            if (stomp != null) {
                cleanupDisplay(stomp);
            }

            WARN_UNTIL.remove(id);
        });
    }

    private static boolean isPowerless(ServerPlayerEntity player) {
        return player != null && player.hasStatusEffect(ModStatusEffects.POWERLESS);
    }

    private static void warnOncePerSec(ServerPlayerEntity p, String msg) {
        long now = p.getWorld().getTime();
        long nextOk = WARN_UNTIL.getOrDefault(p.getUuid(), 0L);
        if (now < nextOk) return;
        WARN_UNTIL.put(p.getUuid(), now + 20);
        p.sendMessage(Text.literal(msg), true);
    }

    @Override
    public void activate(ServerPlayerEntity player) {
        UUID id = player.getUuid();

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

        BlockadeNet.sendActive(player, nowOn);
    }

    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        if (isPowerless(player)) {
            StompState stomp = STOMPING.remove(player.getUuid());
            if (stomp != null) cleanupDisplay(stomp);
            warnOncePerSec(player, "§cYou are powerless.");
            return;
        }

        STOMPING.computeIfAbsent(player.getUuid(), id -> {
            StompState stomp = new StompState();
            stomp.startY = player.getY();
            stomp.peakY = player.getY();
            stomp.wasAirborne = !player.isOnGround();
            stomp.windSoundCooldown = 0;

            spawnDisplay(player, stomp);

            Vec3d vel = player.getVelocity();
            double newY = Math.min(vel.y, STOMP_START_DOWNWARD_SPEED);
            player.setVelocity(vel.x, newY, vel.z);
            player.velocityModified = true;
            player.fallDistance = 0.0F;

            return stomp;
        });
    }

    @Override
    public void forceDisable(ServerPlayerEntity player) {
        ACTIVE.remove(player.getUuid());
        BlockadeNet.sendActive(player, false);

        StompState stomp = STOMPING.remove(player.getUuid());
        if (stomp != null) {
            cleanupDisplay(stomp);
        }
    }

    private static void tickStomp(ServerPlayerEntity player, StompState stomp) {
        ensureDisplay(player, stomp);
        updateDisplayPos(player, stomp);

        double y = player.getY();
        if (y > stomp.peakY) {
            stomp.peakY = y;
        }

        boolean airborne = !player.isOnGround();

        if (airborne) {
            stomp.wasAirborne = true;
            applyExtraGravity(player);
            playWind(player, stomp);
            player.fallDistance = 0.0F;
            return;
        }

        if (stomp.wasAirborne) {
            double drop = Math.max(0.0D, stomp.peakY - player.getY());
            doImpact(player, drop);
            player.fallDistance = 0.0F;
            endStomp(player, stomp);
        }
    }

    private static void applyExtraGravity(ServerPlayerEntity player) {
        if (player.hasVehicle()) return;
        if (player.isTouchingWater() || player.isInLava()) return;

        Vec3d vel = player.getVelocity();
        double nextY = vel.y - EXTRA_GRAVITY_PER_TICK;
        if (nextY < -MAX_DOWN_SPEED) nextY = -MAX_DOWN_SPEED;

        player.setVelocity(vel.x, nextY, vel.z);
        player.velocityModified = true;
    }

    private static void playWind(ServerPlayerEntity player, StompState stomp) {
        Vec3d vel = player.getVelocity();

        if (vel.y > -0.55D) {
            stomp.windSoundCooldown = 0;
            return;
        }

        if (stomp.windSoundCooldown > 0) {
            stomp.windSoundCooldown--;
            return;
        }



        stomp.windSoundCooldown = WIND_SOUND_INTERVAL;
    }

    private static void spawnDisplay(ServerPlayerEntity player, StompState stomp) {
        ServerWorld world = (ServerWorld) player.getWorld();

        DisplayEntity.BlockDisplayEntity display =
                new DisplayEntity.BlockDisplayEntity(net.minecraft.entity.EntityType.BLOCK_DISPLAY, world);

        ((BlockDisplayEntityAccessor) display).odd$setBlockState(ModBlocks.CRAPPY_BLOCK.getDefaultState());
        display.setNoGravity(true);
        display.setInvulnerable(true);
        display.setSilent(true);

        // sit properly under the feet, not half inside the player
        double x = player.getX() - 0.5D;
        double y = player.getBoundingBox().minY - 1.01D;
        double z = player.getZ() - 0.5D;
        display.setPosition(x, y, z);

        world.spawnEntity(display);
        stomp.display = display;
    }

    private static void ensureDisplay(ServerPlayerEntity player, StompState stomp) {
        if (stomp.display != null && stomp.display.isAlive()) return;
        spawnDisplay(player, stomp);
    }

    private static void updateDisplayPos(ServerPlayerEntity player, StompState stomp) {
        if (stomp.display == null || !stomp.display.isAlive()) return;

        double x = player.getX() - 0.5D;
        double y = player.getBoundingBox().minY - 1.01D;
        double z = player.getZ() - 0.5D;
        stomp.display.setPosition(x, y, z);
    }

    private static void cleanupDisplay(StompState stomp) {
        if (stomp == null) return;
        if (stomp.display != null && stomp.display.isAlive()) {
            stomp.display.discard();
        }
        stomp.display = null;
    }

    private static void endStomp(ServerPlayerEntity player, StompState stomp) {
        cleanupDisplay(stomp);
        STOMPING.remove(player.getUuid());
        player.fallDistance = 0.0F;
    }

    private static void doImpact(ServerPlayerEntity player, double drop) {
        ServerWorld world = (ServerWorld) player.getWorld();

        double radius = Math.min(8.5D, 2.4D + drop * 0.28D);
        float maxDamage = (float) Math.min(22.0D, 5.0D + drop * 1.0D);

        Box box = player.getBoundingBox().expand(radius, 2.0D, radius);
        for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, box, e -> e.isAlive() && e != player)) {
            double dist = Math.sqrt(target.squaredDistanceTo(player));
            if (dist > radius) continue;

            float scale = (float) (1.0D - (dist / radius));
            float damage = Math.max(1.0F, maxDamage * (0.35F + (0.65F * scale)));

            target.damage(player.getDamageSources().playerAttack(player), damage);

            Vec3d push = target.getPos().subtract(player.getPos());
            double horizontal = Math.sqrt(push.x * push.x + push.z * push.z);
            if (horizontal < 1.0E-4D) horizontal = 1.0D;

            double kb = 0.45D + (scale * 1.2D);
            target.addVelocity(
                    (push.x / horizontal) * kb,
                    0.22D + (scale * 0.35D),
                    (push.z / horizontal) * kb
            );
            target.velocityModified = true;
        }

        spawnImpactParticles(world, player, radius, drop);

        world.playSound(
                null,
                player.getX(), player.getY(), player.getZ(),
                ModSounds.BLOCKADE_STOMP,
                SoundCategory.PLAYERS,
                1.2F + (float) Math.min(0.7D, drop * 0.035D),
                MathHelper.clamp(0.92F - (float) (drop * 0.008D), 0.7F, 1.0F)
        );
    }

    private static void spawnImpactParticles(ServerWorld world, ServerPlayerEntity player, double radius, double drop) {
        BlockState block = ModBlocks.CRAPPY_BLOCK.getDefaultState();
        BlockStateParticleEffect burst = new BlockStateParticleEffect(ParticleTypes.BLOCK, block);
        BlockStateParticleEffect dust = new BlockStateParticleEffect(ParticleTypes.FALLING_DUST, block);

        double baseY = player.getBoundingBox().minY + 0.05D;

        int rings = 3;
        for (int ringIndex = 0; ringIndex < rings; ringIndex++) {
            double ringFrac = (ringIndex + 1) / (double) rings;
            double ringRadius = radius * (0.35D + ringFrac * 0.55D);

            int count = 12 + ringIndex * 10 + Math.min(18, (int) (drop * 0.8D));
            for (int i = 0; i < count; i++) {
                double angle = (Math.PI * 2.0D) * (i / (double) count);
                angle += world.getRandom().nextDouble() * 0.14D;

                double px = player.getX() + Math.cos(angle) * ringRadius * 0.22D;
                double pz = player.getZ() + Math.sin(angle) * ringRadius * 0.22D;

                double outward = 0.18D + ringFrac * 0.24D + Math.min(0.24D, drop * 0.01D);
                double vx = Math.cos(angle) * outward;
                double vz = Math.sin(angle) * outward;
                double vy = 0.16D + world.getRandom().nextDouble() * 0.18D + Math.min(0.28D, drop * 0.012D);

                world.spawnParticles(burst, px, baseY, pz, 1, vx, vy, vz, 0.0D);
            }
        }

        world.spawnParticles(
                burst,
                player.getX(), baseY, player.getZ(),
                Math.min(32, 10 + (int) (drop * 1.1D)),
                0.35D, 0.04D, 0.35D,
                0.15D
        );

        world.spawnParticles(
                dust,
                player.getX(), baseY, player.getZ(),
                Math.min(42, 16 + (int) (drop * 1.7D)),
                radius * 0.35D, 0.10D, radius * 0.35D,
                0.03D
        );
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
        if (state.isIn(BlockTags.LEAVES)) return false;

        if (state.isAir()) return true;
        if (state.isOf(Blocks.GRASS)) return true;
        if (state.isOf(Blocks.TALL_GRASS)) return true;
        if (state.isIn(BlockTags.FLOWERS)) return true;
        if (state.getBlock() instanceof PlantBlock) return true;
        if (state.isOf(Blocks.SNOW)) return true;

        return false;
    }

    @Override
    public long cooldownTicks() {
        return 0;
    }

    @Override
    public long secondaryCooldownTicks() {
        return 200;
    }
}