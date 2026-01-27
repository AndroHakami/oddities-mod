// src/main/java/net/seep/odd/block/falseflower/FalseFlowerBlockEntity.java
package net.seep.odd.block.falseflower;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import net.seep.odd.abilities.fairy.FairySpell;
import net.seep.odd.block.ModBlocks;
import net.seep.odd.block.falseflower.spell.FalseFlowerSpellDef;
import net.seep.odd.block.falseflower.spell.FalseFlowerSpellRegistry;
import net.seep.odd.block.falseflower.spell.FalseFlowerSpellRuntime;

import java.util.ArrayList;
import java.util.List;

import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

public class FalseFlowerBlockEntity extends BlockEntity implements GeoBlockEntity {

    public static final float MAX_MANA = 200f;

    /** One-shots require FULL mana to activate. */
    public static final float ONE_SHOT_REQUIRED_MANA = MAX_MANA;

    /** Bigger flowers take longer to arm (power^2 scaling). */
    public static int ONE_SHOT_ARM_BASE_TICKS = 10;
    public static int ONE_SHOT_ARM_PER_POWER_SQ_TICKS = 10;

    private FairySpell spell = FairySpell.NONE;
    private boolean active = false;

    // ✅ NEW: default mana is 0 (must be filled via RECHARGE)
    private float mana = 0f;

    private String customName = "";
    public int trackerId = 0;

    private final Object2LongMap<BlockPos> prisonExpiry = new Object2LongOpenHashMap<>();
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // used by SwitcheranoEffect
    public long nextSwapTick = 0L;

    // ✅ One-shot arming state (for sigil charge visuals)
    private boolean arming = false;
    private long armStartTick = 0L;
    private int armDurationTicks = 0;

    public FalseFlowerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.FALSE_FLOWER_BE, pos, state);
    }

    public FairySpell getSpell() { return spell; }
    public String getSpellKey() { return spell.textureKey(); }
    public boolean isMagical() { return spell != FairySpell.NONE; }

    public void cleanse() {
        this.spell = FairySpell.NONE;
        this.active = false;
        this.arming = false;
        this.armStartTick = 0L;
        this.armDurationTicks = 0;

        // ✅ back to empty mana (new flowers start empty too)
        this.mana = 0f;

        if (world != null && !world.isClient) {
            world.setBlockState(pos,
                    getCachedState()
                            .with(FalseFlowerBlock.SKIN, FalseFlowerBlock.Skin.NONE)
                            .with(FalseFlowerBlock.ACTIVE, false),
                    Block.NOTIFY_LISTENERS);
        }
        markDirty();
    }

    public void assignSpell(FairySpell s) {
        this.spell = (s == null ? FairySpell.NONE : s);

        // when assigning a spell, always stop any active/arming state
        this.active = false;
        this.arming = false;
        this.armStartTick = 0L;
        this.armDurationTicks = 0;

        if (world != null && !world.isClient) {
            world.setBlockState(pos,
                    getCachedState().with(FalseFlowerBlock.SKIN, FalseFlowerBlock.Skin.fromSpell(this.spell)),
                    Block.NOTIFY_LISTENERS);
            world.setBlockState(pos, getCachedState().with(FalseFlowerBlock.ACTIVE, false), Block.NOTIFY_LISTENERS);
            markDirty();
        }
    }

    public boolean isActive() { return active; }

    public float getMana() { return mana; }

    public void setMana(float v) {
        this.mana = MathHelper.clamp(v, 0f, MAX_MANA);
        markDirty();
    }

    public void addMana(float v) {
        this.mana = MathHelper.clamp(this.mana + v, 0f, MAX_MANA);
        markDirty();
    }

    public boolean isFullyCharged() {
        return this.mana >= (ONE_SHOT_REQUIRED_MANA - 0.001f);
    }

    public float getPower() { return getCachedState().get(FalseFlowerBlock.POWER); }

    public void setPower(float p) {
        if (world == null || world.isClient) return;

        // ✅ cannot change range while a one-shot is arming
        if (world instanceof ServerWorld sw && isArming(sw)) return;

        int ip = MathHelper.clamp(Math.round(p), 1, 3);
        world.setBlockState(pos, getCachedState().with(FalseFlowerBlock.POWER, ip), Block.NOTIFY_LISTENERS);
        markDirty();
    }

    public String getCustomName() { return customName; }
    public void setCustomName(String s) { this.customName = (s == null ? "" : s); markDirty(); }

    /** ✅ radius only scales with power */
    public int radius() { return 6 * getCachedState().get(FalseFlowerBlock.POWER); }

    private float drainPerTick(float baseDrain) {
        float scale = getCachedState().get(FalseFlowerBlock.POWER); // 1..3
        return baseDrain * scale * scale; // bigger radius drains much more
    }

    /* ================= One-shot arming helpers ================= */

    private int computeArmDurationTicks() {
        int pow = getCachedState().get(FalseFlowerBlock.POWER); // 1..3
        return ONE_SHOT_ARM_BASE_TICKS + (ONE_SHOT_ARM_PER_POWER_SQ_TICKS * pow * pow);
    }

    private void startArming(ServerWorld w) {
        this.arming = true;
        this.armStartTick = w.getTime();
        this.armDurationTicks = computeArmDurationTicks();
        markDirty();
    }

    /** Used by snapshot for renderer timing. */
    public float getArmProgress(ServerWorld w) {
        if (!active) return 0f;
        if (spell == null || !spell.isOneShot()) return 0f;
        if (!arming) return 0f;
        if (armDurationTicks <= 0) return 0f;

        float t = (w.getTime() - armStartTick) / (float) armDurationTicks;
        return MathHelper.clamp(t, 0f, 1f);
    }

    /** Used by snapshot for renderer timing. */
    public int getArmDurationTicks() {
        return armDurationTicks;
    }

    /** Server-side rule for “can’t adjust while activating”. */
    public boolean isArming(ServerWorld w) {
        return active && spell != null && spell.isOneShot() && arming && getArmProgress(w) < 1f;
    }

    public void setActive(boolean a) {
        // ✅ One-shots: only allow activating if fully charged
        if (a && spell != null && spell.isOneShot()) {
            if (!isFullyCharged()) {
                // refuse activation (stay off)
                this.active = false;
                this.arming = false;
                this.armStartTick = 0L;
                this.armDurationTicks = 0;

                if (world != null && !world.isClient) {
                    world.setBlockState(pos, getCachedState().with(FalseFlowerBlock.ACTIVE, false), Block.NOTIFY_LISTENERS);
                    markDirty();
                }
                return;
            }
        }

        this.active = a;

        // turning off always clears arming
        if (!a) {
            this.arming = false;
            this.armStartTick = 0L;
            this.armDurationTicks = 0;
        } else {
            // turning on a one-shot starts arming (server tick will run it too, but do it immediately)
            if (world instanceof ServerWorld sw && spell != null && spell.isOneShot()) {
                if (!this.arming) startArming(sw);
            }
        }

        if (world != null && !world.isClient) {
            world.setBlockState(pos, getCachedState().with(FalseFlowerBlock.ACTIVE, a), Block.NOTIFY_LISTENERS);
            markDirty();
        }
    }

    public static void tick(World w, BlockPos pos, BlockState state, FalseFlowerBlockEntity be) {
        if (w.isClient) return;
        ServerWorld sw = (ServerWorld) w;

        be.tickPrisonCleanup(sw);
        FalseFlowerSpellRuntime.tick(sw);

        if (!be.active) return;
        if (be.spell == null || be.spell == FairySpell.NONE) return;

        FalseFlowerSpellDef def = FalseFlowerSpellRegistry.get(be.spell);
        if (def == null || def.effect() == null) return;

        final int R = be.radius();
        final Box box = new Box(pos).expand(R, R, R);

        if (def.oneShot()) {
            // ✅ One-shots arm first, then fire once.
            if (!be.isFullyCharged()) {
                // if somehow not full anymore, cancel
                be.setActive(false);
                return;
            }

            if (!be.arming) be.startArming(sw);

            float prog = be.getArmProgress(sw);
            if (prog < 1f) {
                return; // still charging
            }

            // Fire the one-shot effect once
            def.effect().tick(sw, pos, state, be, R, box);

            // ✅ Light “explosion” visual burst on activation (server particles)
            Vec3d c = Vec3d.ofCenter(pos);
            sw.spawnParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
            sw.spawnParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 60, R * 0.12, 0.35, R * 0.12, 0.02);
            sw.spawnParticles(ParticleTypes.GLOW, c.x, c.y, c.z, 30, R * 0.10, 0.25, R * 0.10, 0.01);

            sw.playSound(null, pos, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 0.6f, 1.8f);
            sw.playSound(null, pos, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 0.9f, 1.2f);

            // consume all mana + turn off
            be.mana = 0f;
            be.setActive(false);
            be.markDirty();
            return;
        }

        // Auras drain mana continuously
        be.mana = Math.max(0f, be.mana - be.drainPerTick(def.baseDrainPerTick()));
        if (be.mana <= 0f) {
            be.mana = 0f;
            be.setActive(false);
            be.markDirty();
            return;
        }

        def.effect().tick(sw, pos, state, be, R, box);
    }

    /* ====== called by StonePrisonEffect ====== */
    public void spawnStonePrisons(ServerWorld w, List<BlockPos> atPositions, int lifetimeTicks) {
        long expireAt = w.getTime() + lifetimeTicks;
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (BlockPos at : atPositions) {
            for (int dx = -1; dx <= 1; dx++) for (int dy = 0; dy <= 2; dy++) for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0 && dy == 1) continue;
                m.set(at.getX() + dx, at.getY() + dy, at.getZ() + dz);
                if (w.getBlockState(m).isAir()) {
                    w.setBlockState(m, Blocks.STONE.getDefaultState());
                    prisonExpiry.put(m.toImmutable(), expireAt);
                }
            }
        }
    }

    private void tickPrisonCleanup(ServerWorld w) {
        if (prisonExpiry.isEmpty()) return;
        long now = w.getTime();
        List<BlockPos> toRemove = new ArrayList<>();
        for (Object2LongMap.Entry<BlockPos> e : prisonExpiry.object2LongEntrySet()) {
            if (now >= e.getLongValue()) {
                BlockPos p = e.getKey();
                if (w.getBlockState(p).isOf(Blocks.STONE)) w.breakBlock(p, false);
                toRemove.add(p);
            }
        }
        for (BlockPos p : toRemove) prisonExpiry.removeLong(p);
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putString("Spell", spell.name());
        nbt.putBoolean("Active", active);
        nbt.putFloat("Mana", mana);
        nbt.putString("CustomName", customName);
        nbt.putInt("TrackerId", trackerId);
        nbt.putLong("NextSwap", nextSwapTick);

        // one-shot arming persist (so reloads don’t break visuals / timing)
        nbt.putBoolean("Arming", arming);
        nbt.putLong("ArmStart", armStartTick);
        nbt.putInt("ArmDur", armDurationTicks);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        try { this.spell = FairySpell.valueOf(nbt.getString("Spell")); }
        catch (Exception ignored) { this.spell = FairySpell.NONE; }

        this.active = nbt.getBoolean("Active");

        // ✅ if missing, defaults to 0 anyway
        this.mana = MathHelper.clamp(nbt.getFloat("Mana"), 0f, MAX_MANA);

        this.customName = nbt.getString("CustomName");
        this.trackerId = nbt.getInt("TrackerId");
        this.nextSwapTick = nbt.getLong("NextSwap");

        this.arming = nbt.getBoolean("Arming");
        this.armStartTick = nbt.getLong("ArmStart");
        this.armDurationTicks = nbt.getInt("ArmDur");
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}

    @Override public void onLoad() {
        super.onLoad();
        if (!getWorld().isClient) FalseFlowerTracker.hook(this);
    }

    @Override public void markRemoved() {
        super.markRemoved();
        if (!getWorld().isClient) FalseFlowerTracker.unhook(this);
    }
}
