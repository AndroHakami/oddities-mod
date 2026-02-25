// src/main/java/net/seep/odd/abilities/power/CultistPower.java
package net.seep.odd.abilities.power;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.StopSoundS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.event.GameEvent;

import net.seep.odd.abilities.PowerAPI;
import net.seep.odd.abilities.cultist.CultistNet;
import net.seep.odd.block.ModBlocks;
import net.seep.odd.entity.ModEntities;
import net.seep.odd.entity.cultist.ShyGuyEntity;
import net.seep.odd.entity.cultist.SightseerEntity;
import net.seep.odd.entity.cultist.WeepingAngelEntity;
import net.seep.odd.sound.ModSounds;
import net.seep.odd.status.ModStatusEffects;

import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

public final class CultistPower implements Power {

    @Override public String id() { return "cultist"; }

    @Override
    public boolean hasSlot(String slot) {
        return "primary".equals(slot); // only Divine Touch
    }

    // cooldown is up to you; cast-time is enforced separately
    @Override public long cooldownTicks() { return 12; }

    @Override
    public Identifier iconTexture(String slot) {
        return new Identifier("odd", "textures/gui/abilities/cultist_divine_touch.png");
    }

    @Override
    public String slotLongDescription(String slot) {
        return "Divine Touch: Toggle Divine Protection on players, or bring a built construct to life.";
    }

    @Override
    public String longDescription() {
        return "Cultist: Summon holy constructs. Divine Touch toggles Divine Protection on players and animates built monsters.";
    }

    /* =========================================================
       Cast system (2s channel)
       ========================================================= */

    private static final int CAST_TICKS = 40; // 2 seconds

    // Slowness III while casting
    private static final int CAST_SLOWNESS_AMP = 2;   // Slowness 3
    private static final int CAST_SLOWNESS_TICKS = 6; // refreshed

    private enum CastKind { PLAYER, BLOCK }

    private record Pending(CastKind kind,
                           @Nullable UUID targetPlayer,
                           @Nullable BlockPos targetPos,
                           long finishWorldTick) {}

    private static final Object2ObjectOpenHashMap<UUID, Pending> PENDING = new Object2ObjectOpenHashMap<>();
    private static boolean tickRegistered = false;

    public CultistPower() {
        if (!tickRegistered) {
            tickRegistered = true;
            ServerTickEvents.END_SERVER_TICK.register(server -> {
                for (var it = PENDING.object2ObjectEntrySet().iterator(); it.hasNext(); ) {
                    var e = it.next();
                    UUID casterId = e.getKey();
                    Pending pend = e.getValue();

                    ServerPlayerEntity caster = server.getPlayerManager().getPlayer(casterId);
                    if (caster == null) { it.remove(); continue; }

                    // If power swapped off, or powerless -> cancel immediately
                    if (!"cultist".equals(PowerAPI.get(caster)) || isPowerless(caster)) {
                        cancelCast(caster, "§cDivine Touch canceled.");
                        it.remove();
                        continue;
                    }

                    // while channeling: keep slowness up
                    if (caster.getWorld().getTime() < pend.finishWorldTick) {
                        StatusEffectInstance cur = caster.getStatusEffect(StatusEffects.SLOWNESS);
                        if (cur == null || cur.getAmplifier() < CAST_SLOWNESS_AMP || cur.getDuration() <= 3) {
                            caster.addStatusEffect(new StatusEffectInstance(
                                    StatusEffects.SLOWNESS,
                                    CAST_SLOWNESS_TICKS,
                                    CAST_SLOWNESS_AMP,
                                    true, false, false
                            ));
                        }
                        continue;
                    }

                    // finish cast now
                    it.remove();
                    finishCast(caster, pend);
                }
            });
        }
    }

    /* =================== POWERLESS override =================== */

    private static final Object2LongOpenHashMap<UUID> WARN_UNTIL = new Object2LongOpenHashMap<>();

    private static boolean isPowerless(ServerPlayerEntity p) {
        return p != null && p.hasStatusEffect(ModStatusEffects.POWERLESS);
    }

    private static void warnOncePerSec(ServerPlayerEntity p, String msg) {
        long now = p.getWorld().getTime();
        long nextOk = WARN_UNTIL.getOrDefault(p.getUuid(), 0L);
        if (now < nextOk) return;
        WARN_UNTIL.put(p.getUuid(), now + 20);
        p.sendMessage(Text.literal(msg), true);
    }

    private static Identifier divineTouchSoundId() {
        return Registries.SOUND_EVENT.getId(ModSounds.DIVINE_TOUCH);
    }

    private static void stopDivineTouchSound(ServerPlayerEntity caster) {
        if (caster == null) return;
        // stop only THIS sound (player-only)
        caster.networkHandler.sendPacket(new StopSoundS2CPacket(divineTouchSoundId(), SoundCategory.PLAYERS));
    }

    private static void startDivineTouchSound(ServerPlayerEntity caster) {
        if (caster == null) return;

        // ensure any previous loop is stopped first
        stopDivineTouchSound(caster);

        // play for the caster (if your .ogg is looped, this will loop; stop packet will cancel it)
        caster.playSound(ModSounds.DIVINE_TOUCH, SoundCategory.PLAYERS, 1.0f, 1.0f);
    }

    private static void cancelCast(ServerPlayerEntity caster, String msg) {
        if (caster == null) return;
        PENDING.remove(caster.getUuid());

        // stop CPM + overlay + restore camera
        CultistNet.sendTouchStop(caster);

        // stop the casting sound
        stopDivineTouchSound(caster);

        caster.sendMessage(Text.literal(msg), true);
    }

    @Override
    public void forceDisable(ServerPlayerEntity player) {
        cancelCast(player, "§cDivine Touch canceled.");
    }

    /* =========================================================
       Primary ability: start cast
       ========================================================= */

    @Override
    public void activate(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld sw)) return;

        // POWERLESS: cancel if somehow casting, deny starting
        if (isPowerless(player)) {
            cancelCast(player, "§cYou are powerless.");
            warnOncePerSec(player, "§cYou are powerless.");
            return;
        }

        // already casting
        if (PENDING.containsKey(player.getUuid())) {
            player.sendMessage(Text.literal("Divine Touch: channeling..."), true);
            return;
        }

        // 1) Entity hit first (players)
        EntityHitResult ehr = raycastEntity(player, 6.0);
        if (ehr != null && ehr.getEntity() instanceof PlayerEntity targetPlayer) {
            startCastPlayer(player, targetPlayer);
            return;
        }

        // 2) Block hit (structures)
        BlockHitResult bhr = raycastBlock(player, 6.0);
        if (bhr.getType() != HitResult.Type.BLOCK) {
            divineTouchFx(sw, player.getPos().add(0, player.getStandingEyeHeight() * 0.6, 0), 8, 4);
            player.sendMessage(Text.literal("Divine Touch: nothing to bless."), true);
            return;
        }

        startCastBlock(player, bhr.getBlockPos());
    }

    private static void applyCastingSlowness(ServerPlayerEntity caster) {
        caster.addStatusEffect(new StatusEffectInstance(
                StatusEffects.SLOWNESS,
                CAST_SLOWNESS_TICKS,
                CAST_SLOWNESS_AMP,
                true, false, false
        ));
    }

    private static void startCastPlayer(ServerPlayerEntity caster, PlayerEntity target) {
        if (!(caster.getWorld() instanceof ServerWorld sw)) return;

        // CPM + camera + overlay (caster only)
        CultistNet.sendTouchStart(caster);

        // casting sound (caster only)
        startDivineTouchSound(caster);

        applyCastingSlowness(caster);

        long finish = sw.getTime() + CAST_TICKS;
        PENDING.put(caster.getUuid(), new Pending(CastKind.PLAYER, target.getUuid(), null, finish));

        Vec3d fxPos = caster.getPos().add(0, caster.getStandingEyeHeight() * 0.55, 0);
        divineTouchFx(sw, fxPos, 10, 5);
        sw.playSound(null, caster.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_HIT, SoundCategory.PLAYERS, 0.7f, 1.6f);

        caster.sendMessage(Text.literal("Divine Touch: channeling..."), true);
    }

    private static void startCastBlock(ServerPlayerEntity caster, BlockPos hit) {
        if (!(caster.getWorld() instanceof ServerWorld sw)) return;

        CultistNet.sendTouchStart(caster);

        // casting sound (caster only)
        startDivineTouchSound(caster);

        applyCastingSlowness(caster);

        long finish = sw.getTime() + CAST_TICKS;
        PENDING.put(caster.getUuid(), new Pending(CastKind.BLOCK, null, hit, finish));

        Vec3d fxPos = Vec3d.ofCenter(hit).add(0, 0.6, 0);
        divineTouchFx(sw, fxPos, 12, 6);
        sw.playSound(null, hit, SoundEvents.BLOCK_AMETHYST_BLOCK_HIT, SoundCategory.PLAYERS, 0.7f, 1.4f);

        caster.sendMessage(Text.literal("Divine Touch: channeling..."), true);
    }

    private static void finishCast(ServerPlayerEntity caster, Pending pend) {
        if (!(caster.getWorld() instanceof ServerWorld sw)) return;

        // stop CPM + overlay + restore camera (always)
        CultistNet.sendTouchStop(caster);

        // stop the casting sound whether success OR fail (you asked it to cancel if it doesn't go through)
        stopDivineTouchSound(caster);

        // final POWERLESS guard
        if (isPowerless(caster)) {
            warnOncePerSec(caster, "§cYou are powerless.");
            return;
        }

        if (pend.kind == CastKind.PLAYER && pend.targetPlayer != null) {
            ServerPlayerEntity target = sw.getServer().getPlayerManager().getPlayer(pend.targetPlayer);
            if (target == null || target.getWorld() != caster.getWorld()) {
                caster.sendMessage(Text.literal("Divine Touch failed (target gone)."), true);
                return;
            }

            // Must still be near at the final moment
            if (caster.squaredDistanceTo(target) > (6.0 * 6.0)) {
                caster.sendMessage(Text.literal("Divine Touch failed (too far)."), true);
                return;
            }

            toggleDivineProtection(caster, target);
            return;
        }

        if (pend.kind == CastKind.BLOCK && pend.targetPos != null) {
            BlockPos hit = pend.targetPos;

            // Must still be near the structure at the final moment
            Vec3d center = Vec3d.ofCenter(hit);
            if (caster.squaredDistanceTo(center.x, center.y, center.z) > (6.0 * 6.0)) {
                caster.sendMessage(Text.literal("Divine Touch failed (too far)."), true);
                return;
            }

            BlockState at = sw.getBlockState(hit);
            if (at.isAir()) {
                caster.sendMessage(Text.literal("Divine Touch failed (no construct)."), true);
                return;
            }

            if (trySpawnSightseerFromBuild(sw, hit)) {
                divineTouchFx(sw, Vec3d.ofCenter(hit).add(0, 0.6, 0), 16, 8);
                sw.playSound(null, hit, SoundEvents.BLOCK_SOUL_SAND_BREAK, SoundCategory.PLAYERS, 1.0f, 0.7f);
                sw.emitGameEvent(caster, GameEvent.ENTITY_PLACE, hit);
                caster.sendMessage(Text.literal("Divine Touch: Sightseer awakened."), true);
                return;
            }

            if (trySpawnShyGuyFromBuild(sw, hit)) {
                divineTouchFx(sw, Vec3d.ofCenter(hit).add(0, 0.6, 0), 16, 8);
                sw.playSound(null, hit, SoundEvents.BLOCK_SOUL_SAND_BREAK, SoundCategory.PLAYERS, 1.0f, 0.7f);
                sw.emitGameEvent(caster, GameEvent.ENTITY_PLACE, hit);
                caster.sendMessage(Text.literal("Divine Touch: Shy Guy awakened."), true);
                return;
            }

            if (trySpawnWeepingAngelFromBuild(sw, hit)) {
                divineTouchFx(sw, Vec3d.ofCenter(hit).add(0, 0.6, 0), 14, 7);
                sw.playSound(null, hit, SoundEvents.BLOCK_SOUL_SAND_BREAK, SoundCategory.PLAYERS, 1.0f, 0.7f);
                sw.emitGameEvent(caster, GameEvent.ENTITY_PLACE, hit);
                caster.sendMessage(Text.literal("Divine Touch: Weeping Angel awakened."), true);
                return;
            }

            if (tryTransformCentipedeSpawn(sw, hit)) {
                divineTouchFx(sw, Vec3d.ofCenter(hit).add(0, 0.6, 0), 16, 8);
                sw.playSound(null, hit, SoundEvents.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, SoundCategory.PLAYERS, 1.0f, 0.7f);
                sw.emitGameEvent(caster, GameEvent.BLOCK_CHANGE, hit);
                caster.sendMessage(Text.literal("Divine Touch: Centipede nest awakened."), true);
                return;
            }

            divineTouchFx(sw, Vec3d.ofCenter(hit).add(0, 0.6, 0), 10, 5);
            caster.sendMessage(Text.literal("Divine Touch: no valid construct found."), true);
        }
    }

    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        // none
    }

    /* =========================================================
       Divine Protection toggle
       ========================================================= */

    private static final int FOREVER = 20 * 60 * 60 * 24 * 365;

    private static void toggleDivineProtection(ServerPlayerEntity caster, PlayerEntity target) {
        if (!(caster.getWorld() instanceof ServerWorld sw)) return;

        Vec3d fxPos = target.getPos().add(0, target.getStandingEyeHeight() * 0.55, 0);

        if (target.hasStatusEffect(ModStatusEffects.DIVINE_PROTECTION)) {
            target.removeStatusEffect(ModStatusEffects.DIVINE_PROTECTION);

            divineTouchFx(sw, fxPos, 16, 8);

            sw.playSound(null, target.getBlockPos(),
                    SoundEvents.BLOCK_AMETHYST_BLOCK_BREAK, SoundCategory.PLAYERS, 0.8f, 1.4f);

            if (target instanceof ServerPlayerEntity st)
                st.sendMessage(Text.literal("Divine Protection removed."), true);

            caster.sendMessage(Text.literal("Divine Touch: removed protection."), true);
            return;
        }

        target.addStatusEffect(new StatusEffectInstance(
                ModStatusEffects.DIVINE_PROTECTION,
                FOREVER,
                0,
                true,
                false,
                true
        ));

        divineTouchFx(sw, fxPos, 18, 9);

        sw.playSound(null, target.getBlockPos(),
                SoundEvents.BLOCK_AMETHYST_BLOCK_PLACE, SoundCategory.PLAYERS, 0.9f, 1.2f);

        if (target instanceof ServerPlayerEntity st)
            st.sendMessage(Text.literal("Divine Protection granted."), true);

        caster.sendMessage(Text.literal("Divine Touch: granted protection."), true);
    }

    /* =========================================================
       Sightseer structure
       ========================================================= */

    private static boolean trySpawnSightseerFromBuild(ServerWorld sw, BlockPos hit) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos base = hit.add(dx, dy, dz);

                    if (matchesSightseer(sw, base, true)) {
                        consumeAndSpawnSightseer(sw, base, true);
                        return true;
                    }
                    if (matchesSightseer(sw, base, false)) {
                        consumeAndSpawnSightseer(sw, base, false);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean matchesSightseer(ServerWorld sw, BlockPos base, boolean axisX) {
        if (!isSoul(sw.getBlockState(base))) return false;
        if (!isSoul(sw.getBlockState(base.up(1)))) return false;

        BlockPos armA = axisX ? base.up(1).west() : base.up(1).north();
        BlockPos armB = axisX ? base.up(1).east() : base.up(1).south();

        if (!isSoul(sw.getBlockState(armA))) return false;
        if (!isSoul(sw.getBlockState(armB))) return false;

        BlockPos skullA = armA.up(1);
        BlockPos skullB = armB.up(1);

        if (!isSkeletonSkull(sw.getBlockState(skullA))) return false;
        if (!isSkeletonSkull(sw.getBlockState(skullB))) return false;

        return sw.getBlockState(base.up(2)).isAir();
    }

    private static void consumeAndSpawnSightseer(ServerWorld sw, BlockPos base, boolean axisX) {
        BlockPos armA = axisX ? base.up(1).west() : base.up(1).north();
        BlockPos armB = axisX ? base.up(1).east() : base.up(1).south();

        BlockPos skullA = armA.up(1);
        BlockPos skullB = armB.up(1);

        setAir(sw, skullA);
        setAir(sw, skullB);
        setAir(sw, armA);
        setAir(sw, armB);
        setAir(sw, base.up(1));
        setAir(sw, base);

        SightseerEntity sightseer = ModEntities.SIGHTSEER.create(sw);
        if (sightseer == null) return;

        sightseer.setHomePos(base);
        sightseer.refreshPositionAndAngles(base.getX() + 0.5, base.getY() + 0.05, base.getZ() + 0.5, 0.0f, 0.0f);
        sightseer.setPersistent();

        sw.spawnEntity(sightseer);

        divineTouchFx(sw, Vec3d.ofCenter(base).add(0, 0.9, 0), 20, 10);
        sw.playSound(null, base, SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 0.6f, 1.6f);
    }

    /* =========================================================
       Shy Guy structure
       ========================================================= */

    private static boolean trySpawnShyGuyFromBuild(ServerWorld sw, BlockPos hit) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos base = hit.add(dx, dy, dz);
                    if (matchesShyGuy(sw, base)) {
                        consumeAndSpawnShyGuy(sw, base);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean matchesShyGuy(ServerWorld sw, BlockPos base) {
        if (!isSoul(sw.getBlockState(base))) return false;
        if (!isSoul(sw.getBlockState(base.up(1)))) return false;
        if (!isSoul(sw.getBlockState(base.up(2)))) return false;

        BlockState skull = sw.getBlockState(base.up(3));
        return isWitherSkull(skull);
    }

    private static void consumeAndSpawnShyGuy(ServerWorld sw, BlockPos base) {
        setAir(sw, base.up(3));
        setAir(sw, base.up(2));
        setAir(sw, base.up(1));
        setAir(sw, base);

        ShyGuyEntity shy = ModEntities.SHY_GUY.create(sw);
        if (shy == null) return;

        shy.setHomePos(base);
        shy.refreshPositionAndAngles(base.getX() + 0.5, base.getY() + 0.05, base.getZ() + 0.5, 0.0f, 0.0f);
        shy.setPersistent();

        sw.spawnEntity(shy);

        divineTouchFx(sw, Vec3d.ofCenter(base).add(0, 1.0, 0), 20, 10);
        sw.playSound(null, base, SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 0.55f, 0.9f);
    }

    /* =========================================================
       Weeping Angel structure
       ========================================================= */

    private static boolean trySpawnWeepingAngelFromBuild(ServerWorld sw, BlockPos hit) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos base = hit.add(dx, dy, dz);
                    if (matchesWeepingAngel(sw, base)) {
                        consumeAndSpawnWeepingAngel(sw, base);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean matchesWeepingAngel(ServerWorld sw, BlockPos base) {
        if (!isSoul(sw.getBlockState(base))) return false;
        return isCreeperHead(sw.getBlockState(base.up(1)));
    }

    private static boolean isCreeperHead(BlockState s) {
        return s.isOf(Blocks.CREEPER_HEAD) || s.isOf(Blocks.CREEPER_WALL_HEAD);
    }

    // Centipede Spawner: Respawn Anchor -> Centipede Spawn block
    private static boolean tryTransformCentipedeSpawn(ServerWorld sw, BlockPos hit) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos p = hit.add(dx, dy, dz);
                    if (sw.getBlockState(p).isOf(Blocks.RESPAWN_ANCHOR)) {
                        sw.setBlockState(p, ModBlocks.CENTIPEDE_SPAWN.getDefaultState(), Block.NOTIFY_ALL);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static void consumeAndSpawnWeepingAngel(ServerWorld sw, BlockPos base) {
        setAir(sw, base.up(1));
        setAir(sw, base);

        WeepingAngelEntity angel = ModEntities.WEEPING_ANGEL.create(sw);
        if (angel == null) return;

        angel.setHomePos(base);
        angel.initCreationDisguise(sw, base);
        angel.refreshPositionAndAngles(base.getX() + 0.5, base.getY() + 0.05, base.getZ() + 0.5, 0.0f, 0.0f);
        angel.setPersistent();

        sw.spawnEntity(angel);

        divineTouchFx(sw, Vec3d.ofCenter(base).add(0, 0.75, 0), 18, 9);
        sw.playSound(null, base, SoundEvents.BLOCK_SCULK_SENSOR_CLICKING, SoundCategory.HOSTILE, 0.9f, 0.9f);
    }

    /* =========================================================
       Shared helpers
       ========================================================= */

    private static void setAir(ServerWorld sw, BlockPos pos) {
        sw.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
    }

    private static boolean isSoul(BlockState s) {
        return s.isOf(Blocks.SOUL_SAND);
    }

    private static boolean isSkeletonSkull(BlockState s) {
        return s.isOf(Blocks.SKELETON_SKULL)
                || s.isOf(Blocks.SKELETON_WALL_SKULL)
                || s.isOf(Blocks.WITHER_SKELETON_SKULL)
                || s.isOf(Blocks.WITHER_SKELETON_WALL_SKULL);
    }

    private static boolean isWitherSkull(BlockState s) {
        return s.isOf(Blocks.WITHER_SKELETON_SKULL) || s.isOf(Blocks.WITHER_SKELETON_WALL_SKULL);
    }

    private static void divineTouchFx(ServerWorld sw, Vec3d pos, int enchantCount, int portalCount) {
        sw.spawnParticles(ParticleTypes.ENCHANT,
                pos.x, pos.y, pos.z,
                enchantCount,
                0.32, 0.42, 0.32,
                0.07);

        sw.spawnParticles(ParticleTypes.PORTAL,
                pos.x, pos.y + 0.20, pos.z,
                portalCount,
                0.28, 0.32, 0.28,
                0.03);
    }

    /* =========================================================
       Raycasts (server-safe)
       ========================================================= */

    @Nullable
    private static EntityHitResult raycastEntity(ServerPlayerEntity player, double range) {
        Vec3d start = player.getCameraPosVec(1.0F);
        Vec3d look = player.getRotationVec(1.0F);
        Vec3d end = start.add(look.multiply(range));

        return net.minecraft.entity.projectile.ProjectileUtil.raycast(
                player,
                start,
                end,
                player.getBoundingBox().stretch(look.multiply(range)).expand(1.0),
                e -> e instanceof PlayerEntity && e.isAlive() && e != player,
                range * range
        );
    }

    private static BlockHitResult raycastBlock(ServerPlayerEntity player, double range) {
        Vec3d start = player.getCameraPosVec(1.0F);
        Vec3d look = player.getRotationVec(1.0F);
        Vec3d end = start.add(look.multiply(range));

        return player.getWorld().raycast(new RaycastContext(
                start,
                end,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                player
        ));
    }
}