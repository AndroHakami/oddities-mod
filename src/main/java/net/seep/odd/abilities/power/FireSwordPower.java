// src/main/java/net/seep/odd/abilities/power/FireSwordPower.java
package net.seep.odd.abilities.power;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.block.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;

import net.seep.odd.abilities.PowerAPI;
import net.seep.odd.abilities.firesword.FireSwordNet;
import net.seep.odd.abilities.firesword.item.FireSwordItem;
import net.seep.odd.item.ModItems;
import net.seep.odd.status.ModStatusEffects;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class FireSwordPower implements Power {

    @Override public String id() { return "firesword"; }

    @Override
    public boolean hasSlot(String slot) {
        return "primary".equals(slot) || "secondary".equals(slot);
    }

    @Override public long cooldownTicks() { return 0; }
    @Override public long secondaryCooldownTicks() { return 2 * 20; }

    @Override
    public Identifier iconTexture(String slot) {
        return switch (slot) {
            case "primary"   -> new Identifier("odd", "textures/gui/abilities/firesword_toggle.png");
            case "secondary" -> new Identifier("odd", "textures/gui/abilities/firesword_throw.png");
            default          -> new Identifier("odd", "textures/gui/abilities/ability_default.png");
        };
    }

    @Override
    public String slotLongDescription(String slot) {
        return switch (slot) {
            case "primary" ->
                    "Conjure a Fire Sword by channeling 2s on a fire source (blocks/items/lava) OR on a fire-immune mob (it becomes the sword). " +
                            "You must stay close + keep looking at the target. The sword drops on the ground.";
            case "secondary" ->
                    "Throw your conjured Fire Sword. On impact it ignites and explodes, destroying the sword.";
            default -> "FireSword";
        };
    }

    @Override
    public String longDescription() {
        return "Conjure a fragile but deadly Fire Sword by consuming a fire source (blocks/items/lava) or transmuting a fire-immune creature. "
                + "Strikes ignite targets. Throwing it causes a fiery explosion on impact, destroying it. "
                + "You have permanent Fire Resistance while you have the FireSword power.";
    }

    /* =========================================================
       Permanent Fire Resistance (while you have this power)
       ========================================================= */

    private static void ensureFireResist(ServerPlayerEntity p) {
        if (p == null) return;
        // If POWERLESS, we do NOT refresh passives
        if (isPowerless(p)) return;

        var cur = p.getStatusEffect(StatusEffects.FIRE_RESISTANCE);
        if (cur == null || cur.getDuration() < 60) {
            p.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.FIRE_RESISTANCE,
                    20 * 15, // 15s buffer, constantly refreshed
                    0,
                    true,
                    false,
                    false
            ));
        }
    }

    /** If your core system already calls this per-owner, keep it; otherwise the tick loop below covers it. */
    public static void serverTick(ServerPlayerEntity player) {
        ensureFireResist(player);
    }

    @Override
    public void onAssigned(ServerPlayerEntity player) {
        ensureFireResist(player);
    }

    /* =========================================================
       Cast system (2s channel) + “must keep looking / near”
       ========================================================= */

    private static final int CAST_TICKS = 40; // 2 seconds
    private static final int CAST_SLOWNESS_AMP = 2;   // Slowness III
    private static final int CAST_SLOWNESS_TICKS = 6; // refreshed

    private static final double CAST_RANGE = 6.0;
    private static final double CAST_RANGE_SQ = CAST_RANGE * CAST_RANGE;

    private enum CastKind { BLOCK, ENTITY, ITEM }

    private record Pending(
            CastKind kind,
            @Nullable BlockPos targetPos,
            int targetEntityId,
            long finishWorldTick
    ) {}

    private static final Object2ObjectOpenHashMap<UUID, Pending> PENDING = new Object2ObjectOpenHashMap<>();
    private static boolean tickRegistered = false;

    public FireSwordPower() {
        if (!tickRegistered) {
            tickRegistered = true;

            ServerTickEvents.END_SERVER_TICK.register(server -> {
                // ✅ passive for all FireSword owners
                for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                    if ("firesword".equals(PowerAPI.get(p))) {
                        ensureFireResist(p);
                    }
                }

                // ✅ channel system
                for (var it = PENDING.object2ObjectEntrySet().iterator(); it.hasNext(); ) {
                    var e = it.next();
                    UUID casterId = e.getKey();
                    Pending pend = e.getValue();

                    ServerPlayerEntity caster = server.getPlayerManager().getPlayer(casterId);
                    if (caster == null) { it.remove(); continue; }

                    // power swapped off OR powerless -> cancel
                    if (!"firesword".equals(PowerAPI.get(caster)) || isPowerless(caster)) {
                        // IMPORTANT: do NOT modify PENDING here (iterator owns removal)
                        cancelCast(caster, "§cConjure canceled.", false);
                        it.remove();
                        continue;
                    }

                    ServerWorld sw = (ServerWorld) caster.getWorld();

                    // while channeling
                    if (sw.getTime() < pend.finishWorldTick) {

                        if (!stillValidDuringCast(caster, pend)) {
                            cancelCast(caster, "§cConjure canceled (lost target).", false);
                            it.remove();
                            continue;
                        }

                        applyCastingSlowness(caster);

                        float prog = 1.0f - (float)(pend.finishWorldTick - sw.getTime()) / (float)CAST_TICKS;
                        tickSwirlFx(sw, caster, pend, prog);
                        continue;
                    }

                    // finish
                    it.remove();
                    finishCast(caster, pend);
                }
            });
        }
    }

    private static boolean stillValidDuringCast(ServerPlayerEntity caster, Pending pend) {
        if (!(caster.getWorld() instanceof ServerWorld sw)) return false;

        return switch (pend.kind) {
            case BLOCK -> pend.targetPos != null
                    && isValidFireSource(sw, pend.targetPos)          // source still exists
                    && stillLookingAtBlock(sw, caster, pend.targetPos); // still looking + near
            case ENTITY -> stillLookingAtEntity(sw, caster, pend.targetEntityId);
            case ITEM -> true; // item conjure has no target requirement
        };
    }

    private static boolean stillLookingAtBlock(ServerWorld sw, ServerPlayerEntity caster, BlockPos targetPos) {
        Vec3d c = Vec3d.ofCenter(targetPos);
        if (caster.squaredDistanceTo(c.x, c.y, c.z) > CAST_RANGE_SQ) return false;

        BlockHitResult bhr = raycastBlock(sw, caster, CAST_RANGE);
        if (bhr == null || bhr.getType() != HitResult.Type.BLOCK) return false;

        BlockPos hit = bhr.getBlockPos();
        if (hit.equals(targetPos)) return true;

        // allow adjacent space case
        BlockPos adj = hit.offset(bhr.getSide());
        return adj.equals(targetPos);
    }

    private static boolean stillLookingAtEntity(ServerWorld sw, ServerPlayerEntity caster, int entityId) {
        Entity ent = sw.getEntityById(entityId);
        if (!(ent instanceof LivingEntity le) || !le.isAlive()) return false;

        if (caster.squaredDistanceTo(le) > CAST_RANGE_SQ) return false;

        EntityHitResult ehr = raycastEntity(caster, CAST_RANGE);
        return ehr != null && ehr.getEntity() != null && ehr.getEntity().getId() == entityId;
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

    /**
     * IMPORTANT: iterator safety.
     * - When cancelling from inside the PENDING iterator loop: call with removePending=false, then iterator.remove().
     * - When cancelling from anywhere else: call with removePending=true.
     */
    private static void cancelCast(ServerPlayerEntity caster, String msg, boolean removePending) {
        if (caster == null) return;

        if (removePending) {
            PENDING.remove(caster.getUuid());
        }

        FireSwordNet.sendConjureStop(caster);

    }

    @Override
    public void forceDisable(ServerPlayerEntity player) {
        cancelCast(player, "§cConjure canceled.", true);
    }

    /* =========================================================
       Primary: start conjure (2s)
       (NO inventory “already has sword” restriction)
       ========================================================= */

    @Override
    public void activate(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld sw)) return;

        if (isPowerless(player)) {
            cancelCast(player, "§cYou are powerless.", true);
            warnOncePerSec(player, "§cYou are powerless.");
            return;
        }

        if (PENDING.containsKey(player.getUuid())) {

            return;
        }

        // 1) entity target (fire mobs -> become sword)
        EntityHitResult ehr = raycastEntity(player, CAST_RANGE);
        if (ehr != null && ehr.getEntity() instanceof LivingEntity le && le.isAlive() && isFireMob(le)) {
            startCastEntity(player, le);
            return;
        }

        // 2) block target (fire blocks/surfaces/lava/etc.)
        BlockPos firePos = findFireSourcePos(sw, player, CAST_RANGE);
        if (firePos != null) {
            startCastBlock(player, firePos);
            return;
        }

        // 3) item source fallback
        if (consumeFireProductOneUse(player)) {
            startCastItem(player, player.getBlockPos());
            return;
        }


    }

    /** Secondary: throw the conjured sword. */
    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld sw)) return;

        if (isPowerless(player)) {
            warnOncePerSec(player, "§cYou are powerless.");
            return;
        }

        ItemStack main = player.getMainHandStack();
        if (!isConjuredFireSword(main)) return;

        var proj = new net.seep.odd.abilities.firesword.entity.FireSwordProjectileEntity(
                net.seep.odd.entity.ModEntities.FIRE_SWORD_PROJECTILE, sw, player
        );
        proj.setItem(main.copyWithCount(1));

        Vec3d look = player.getRotationVec(1.0f).normalize();
        proj.setPos(player.getX(), player.getEyeY() - 0.1, player.getZ());
        proj.setVelocity(look.x, look.y, look.z, 1.8f, 0.5f);

        sw.spawnEntity(proj);

        player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
    }

    /* =========================================================
       Cast start/finish
       ========================================================= */

    private static void applyCastingSlowness(ServerPlayerEntity caster) {
        StatusEffectInstance cur = caster.getStatusEffect(StatusEffects.SLOWNESS);
        if (cur == null || cur.getAmplifier() < CAST_SLOWNESS_AMP || cur.getDuration() <= 3) {
            caster.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.SLOWNESS,
                    CAST_SLOWNESS_TICKS,
                    CAST_SLOWNESS_AMP,
                    true, false, false
            ));
        }
    }

    private static void startCastBlock(ServerPlayerEntity caster, BlockPos pos) {
        if (!(caster.getWorld() instanceof ServerWorld sw)) return;

        FireSwordNet.sendConjureStart(caster);
        applyCastingSlowness(caster);

        long finish = sw.getTime() + CAST_TICKS;
        PENDING.put(caster.getUuid(), new Pending(CastKind.BLOCK, pos, -1, finish));

        sw.playSound(null, caster.getBlockPos(), SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 0.7f, 0.95f);

    }

    private static void startCastEntity(ServerPlayerEntity caster, LivingEntity target) {
        if (!(caster.getWorld() instanceof ServerWorld sw)) return;

        FireSwordNet.sendConjureStart(caster);
        applyCastingSlowness(caster);

        long finish = sw.getTime() + CAST_TICKS;
        PENDING.put(caster.getUuid(), new Pending(CastKind.ENTITY, null, target.getId(), finish));

        sw.playSound(null, caster.getBlockPos(), SoundEvents.ENTITY_BLAZE_AMBIENT, SoundCategory.PLAYERS, 0.55f, 1.35f);

    }

    private static void startCastItem(ServerPlayerEntity caster, BlockPos dropPos) {
        if (!(caster.getWorld() instanceof ServerWorld sw)) return;

        FireSwordNet.sendConjureStart(caster);
        applyCastingSlowness(caster);

        long finish = sw.getTime() + CAST_TICKS;
        PENDING.put(caster.getUuid(), new Pending(CastKind.ITEM, dropPos, -1, finish));

        sw.playSound(null, caster.getBlockPos(), SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 0.65f, 1.05f);

    }

    private static void finishCast(ServerPlayerEntity caster, Pending pend) {
        if (!(caster.getWorld() instanceof ServerWorld sw)) return;

        FireSwordNet.sendConjureStop(caster);

        if (isPowerless(caster)) {
            warnOncePerSec(caster, "§cYou are powerless.");
            return;
        }

        // final guard (don’t allow last-tick cheese)
        if (!stillValidDuringCast(caster, pend)) {

            return;
        }

        switch (pend.kind) {
            case BLOCK -> {
                if (pend.targetPos == null) return;

                if (!isValidFireSource(sw, pend.targetPos)) {

                    return;
                }

                consumeFireSource(sw, pend.targetPos);

                Vec3d spawn = Vec3d.ofCenter(pend.targetPos).add(0, 0.15, 0);
                burstFx(sw, spawn);

                dropConjuredSword(sw, spawn);
                sw.playSound(null, pend.targetPos, SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 0.85f, 0.9f);

            }

            case ENTITY -> {
                Entity ent = sw.getEntityById(pend.targetEntityId);
                if (!(ent instanceof LivingEntity le) || !le.isAlive()) {

                    return;
                }

                if (!isFireMob(le)) {

                    return;
                }

                Vec3d pos = le.getPos().add(0, le.getHeight() * 0.5, 0);

                le.damage(sw.getDamageSources().outOfWorld(), 9999f);

                burstFx(sw, pos);
                dropConjuredSword(sw, pos.add(0, 0.15, 0));

                sw.playSound(null, le.getBlockPos(), SoundEvents.ENTITY_BLAZE_DEATH, SoundCategory.PLAYERS, 0.95f, 1.1f);

            }

            case ITEM -> {
                BlockPos bp = pend.targetPos != null ? pend.targetPos : caster.getBlockPos();
                Vec3d spawn = Vec3d.ofCenter(bp).add(0, 0.15, 0);
                burstFx(sw, spawn);

                dropConjuredSword(sw, spawn);
                sw.playSound(null, bp, SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 0.80f, 1.0f);

            }
        }
    }

    /* =========================================================
       Sword drop + conjured detection
       ========================================================= */

    private static void dropConjuredSword(ServerWorld sw, Vec3d pos) {
        ItemStack conjured = new ItemStack(ModItems.FIRE_SWORD);
        conjured.getOrCreateNbt().putBoolean(FireSwordItem.SUMMONED_NBT, true);

        var itemEnt = new net.minecraft.entity.ItemEntity(sw, pos.x, pos.y, pos.z, conjured);
        itemEnt.setPickupDelay(0);
        sw.spawnEntity(itemEnt);
    }

    private static boolean isConjuredFireSword(ItemStack stack) {
        return !stack.isEmpty()
                && stack.isOf(ModItems.FIRE_SWORD)
                && stack.hasNbt()
                && stack.getNbt() != null
                && stack.getNbt().getBoolean(FireSwordItem.SUMMONED_NBT);
    }

    /* =========================================================
       Item sources
       ========================================================= */

    private static boolean consumeFireProductOneUse(ServerPlayerEntity player) {
        if (consumeFromHand(player, Hand.MAIN_HAND)) return true;
        if (consumeFromHand(player, Hand.OFF_HAND)) return true;

        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack s = player.getInventory().getStack(i);
            if (s.isEmpty()) continue;

            if (s.isOf(Items.LAVA_BUCKET)) {
                player.getInventory().setStack(i, new ItemStack(Items.BUCKET));
                return true;
            }

            if (s.isOf(Items.FIRE_CHARGE) || s.isOf(Items.BLAZE_POWDER) || s.isOf(Items.MAGMA_CREAM)) {
                s.decrement(1);
                if (s.isEmpty()) player.getInventory().setStack(i, ItemStack.EMPTY);
                return true;
            }
        }
        return false;
    }

    private static boolean consumeFromHand(ServerPlayerEntity player, Hand hand) {
        ItemStack s = player.getStackInHand(hand);
        if (s.isEmpty()) return false;

        if (s.isOf(Items.LAVA_BUCKET)) {
            player.setStackInHand(hand, new ItemStack(Items.BUCKET));
            return true;
        }

        if (s.isOf(Items.FIRE_CHARGE) || s.isOf(Items.BLAZE_POWDER) || s.isOf(Items.MAGMA_CREAM)) {
            s.decrement(1);
            if (s.isEmpty()) player.setStackInHand(hand, ItemStack.EMPTY);
            return true;
        }

        return false;
    }

    /* =========================================================
       Block sources
       ========================================================= */

    @Nullable
    private static BlockPos findFireSourcePos(ServerWorld sw, ServerPlayerEntity player, double range) {
        BlockHitResult bhr = raycastBlock(sw, player, range);
        if (bhr != null && bhr.getType() == HitResult.Type.BLOCK) {
            BlockPos hit = bhr.getBlockPos();
            if (isValidFireSource(sw, hit)) return hit;

            BlockPos adj = hit.offset(bhr.getSide());
            if (isValidFireSource(sw, adj)) return adj;
        }

        BlockPos base = player.getBlockPos();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                BlockPos p0 = base.add(dx, 0, dz);
                BlockPos p1 = base.add(dx, -1, dz);
                if (isValidFireSource(sw, p0)) return p0;
                if (isValidFireSource(sw, p1)) return p1;
            }
        }
        return null;
    }

    private static BlockHitResult raycastBlock(ServerWorld sw, ServerPlayerEntity player, double range) {
        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVec(1.0f);
        Vec3d end = eye.add(look.multiply(range));

        return sw.raycast(new RaycastContext(
                eye, end,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.ANY,
                player
        ));
    }

    private static boolean isValidFireSource(ServerWorld sw, BlockPos pos) {
        BlockState st = sw.getBlockState(pos);
        Block b = st.getBlock();

        if (st.isOf(Blocks.FIRE) || st.isOf(Blocks.SOUL_FIRE)) return true;

        if (st.isOf(Blocks.TORCH) || st.isOf(Blocks.WALL_TORCH)
                || st.isOf(Blocks.SOUL_TORCH) || st.isOf(Blocks.SOUL_WALL_TORCH)
                || st.isOf(Blocks.REDSTONE_TORCH) || st.isOf(Blocks.REDSTONE_WALL_TORCH)) return true;

        if (b instanceof CampfireBlock && st.contains(CampfireBlock.LIT) && st.get(CampfireBlock.LIT)) return true;

        if ((b instanceof CandleBlock || b instanceof CandleCakeBlock) && st.contains(Properties.LIT) && st.get(Properties.LIT)) return true;

        if (b instanceof AbstractFurnaceBlock && st.contains(AbstractFurnaceBlock.LIT) && st.get(AbstractFurnaceBlock.LIT)) return true;

        FluidState fs = st.getFluidState();
        if (!fs.isEmpty() && fs.isIn(FluidTags.LAVA) && fs.isStill()) return true;

        return st.isOf(Blocks.MAGMA_BLOCK);
    }

    private static void consumeFireSource(ServerWorld sw, BlockPos pos) {
        BlockState st = sw.getBlockState(pos);
        Block b = st.getBlock();

        if (st.isOf(Blocks.FIRE) || st.isOf(Blocks.SOUL_FIRE)) {
            sw.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            sw.playSound(null, pos, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 0.8f, 1.0f);
            return;
        }

        if (st.isOf(Blocks.TORCH) || st.isOf(Blocks.WALL_TORCH)
                || st.isOf(Blocks.SOUL_TORCH) || st.isOf(Blocks.SOUL_WALL_TORCH)
                || st.isOf(Blocks.REDSTONE_TORCH) || st.isOf(Blocks.REDSTONE_WALL_TORCH)) {
            sw.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            sw.playSound(null, pos, SoundEvents.BLOCK_WOOD_BREAK, SoundCategory.BLOCKS, 0.8f, 1.2f);
            return;
        }

        if (b instanceof CampfireBlock && st.contains(CampfireBlock.LIT) && st.get(CampfireBlock.LIT)) {
            sw.setBlockState(pos, st.with(CampfireBlock.LIT, false), Block.NOTIFY_ALL);
            sw.playSound(null, pos, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 0.75f, 0.95f);
            return;
        }

        if ((b instanceof CandleBlock || b instanceof CandleCakeBlock) && st.contains(Properties.LIT) && st.get(Properties.LIT)) {
            sw.setBlockState(pos, st.with(Properties.LIT, false), Block.NOTIFY_ALL);
            sw.playSound(null, pos, SoundEvents.BLOCK_CANDLE_EXTINGUISH, SoundCategory.BLOCKS, 0.7f, 1.0f);
            return;
        }

        if (b instanceof AbstractFurnaceBlock && st.contains(AbstractFurnaceBlock.LIT) && st.get(AbstractFurnaceBlock.LIT)) {
            sw.setBlockState(pos, st.with(AbstractFurnaceBlock.LIT, false), Block.NOTIFY_ALL);
            sw.playSound(null, pos, SoundEvents.BLOCK_FURNACE_FIRE_CRACKLE, SoundCategory.BLOCKS, 0.35f, 0.8f);
            return;
        }

        FluidState fs = st.getFluidState();
        if (!fs.isEmpty() && fs.isIn(FluidTags.LAVA) && fs.isStill()) {
            sw.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            sw.playSound(null, pos, SoundEvents.BLOCK_LAVA_EXTINGUISH, SoundCategory.BLOCKS, 0.9f, 1.0f);
            return;
        }

        if (st.isOf(Blocks.MAGMA_BLOCK)) {
            sw.setBlockState(pos, Blocks.BASALT.getDefaultState(), Block.NOTIFY_ALL);
            sw.playSound(null, pos, SoundEvents.BLOCK_LAVA_EXTINGUISH, SoundCategory.BLOCKS, 0.65f, 1.2f);
        }
    }

    /* =========================================================
       Fire mob detection
       ========================================================= */

    private static boolean isFireMob(LivingEntity e) {
        return e.isFireImmune() && !(e instanceof net.minecraft.entity.player.PlayerEntity);
    }

    /* =========================================================
       FX: swirl during cast + burst on finish
       ========================================================= */

    private static void tickSwirlFx(ServerWorld sw, ServerPlayerEntity caster, Pending pend, float progress01) {
        Vec3d c;

        if (pend.kind == CastKind.BLOCK && pend.targetPos != null) {
            c = Vec3d.ofCenter(pend.targetPos).add(0, 0.15, 0);
        } else if (pend.kind == CastKind.ENTITY) {
            Entity ent = sw.getEntityById(pend.targetEntityId);
            if (ent != null) c = ent.getPos().add(0, ent.getHeight() * 0.5, 0);
            else c = caster.getPos().add(0, 0.8, 0);
        } else {
            c = caster.getPos().add(0, 0.25, 0);
        }

        float a = MathHelper.clamp(progress01, 0f, 1f);
        double r = 0.35 + 0.55 * a;

        int points = 6 + (int)(6 * a);
        double t = (sw.getTime() % 2000) / 20.0;

        for (int i = 0; i < points; i++) {
            double ang = (i / (double) points) * Math.PI * 2.0 + t * (2.2 + 2.4 * a);
            double px = c.x + Math.cos(ang) * r;
            double pz = c.z + Math.sin(ang) * r;
            double py = c.y + 0.05 + 0.25 * Math.sin(ang * 2.0 + t);

            sw.spawnParticles(ParticleTypes.FLAME, px, py, pz, 1, 0.02, 0.02, 0.02, 0.01);
            if ((sw.getTime() & 1) == 0) {
                sw.spawnParticles(ParticleTypes.SMOKE, px, py, pz, 1, 0.02, 0.02, 0.02, 0.01);
            }
        }

        if ((sw.getTime() % 3) == 0) {
            sw.spawnParticles(ParticleTypes.LAVA, c.x, c.y + 0.2, c.z, 1, 0.12, 0.08, 0.12, 0.02);
        }
    }

    private static void burstFx(ServerWorld sw, Vec3d pos) {
        sw.spawnParticles(ParticleTypes.FLAME, pos.x, pos.y + 0.2, pos.z, 80, 0.45, 0.25, 0.45, 0.04);
        sw.spawnParticles(ParticleTypes.LAVA,  pos.x, pos.y + 0.25, pos.z, 18, 0.35, 0.18, 0.35, 0.02);
        sw.spawnParticles(ParticleTypes.SMOKE, pos.x, pos.y + 0.2, pos.z, 28, 0.40, 0.20, 0.40, 0.02);
    }

    /* =========================================================
       Raycast entity
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
                e -> e instanceof LivingEntity le && le.isAlive() && e != player,
                range * range
        );
    }
}