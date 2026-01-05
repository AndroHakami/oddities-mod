package net.seep.odd.block.falseflower;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import net.seep.odd.abilities.fairy.FairySpell;
import net.seep.odd.block.ModBlocks;

import java.util.ArrayList;
import java.util.List;

public class FalseFlowerBlockEntity extends BlockEntity implements software.bernie.geckolib.animatable.GeoEntity {

    /* ------------------- state ------------------- */

    private FairySpell spell = FairySpell.AURA_LEVITATION;
    private boolean active = false;
    private float mana = 120f; // per flower
    private String customName = "";
    /** Tracker id (server). */
    public int trackerId = 0;

    // positions of temporary stone prisons -> clear at given world time
    private final Object2LongMap<BlockPos> prisonExpiry = new Object2LongOpenHashMap<>();

    public FalseFlowerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.FALSE_FLOWER_BE, pos, state);
    }

    /* ------------------- API (used by tracker/UI) ------------------- */

    public void assignSpell(FairySpell s) {
        this.spell = s;
        if (world != null && !world.isClient) {
            world.setBlockState(pos,
                    getCachedState().with(FalseFlowerBlock.SKIN, FalseFlowerBlock.Skin.fromSpell(s)),
                    Block.NOTIFY_LISTENERS);
            markDirty();
        }
    }

    public boolean isActive() { return active; }
    public void setActive(boolean a) {
        this.active = a;
        if (world != null && !world.isClient) {
            world.setBlockState(pos, getCachedState().with(FalseFlowerBlock.ACTIVE, a), Block.NOTIFY_LISTENERS);
            markDirty();
        }
    }

    public float getMana() { return mana; }
    public void addMana(float v) {
        this.mana = Math.min(200f, mana + v);
        markDirty();
    }

    /** POWER is stored on the blockstate as an int property. */
    public float getPower() {
        return getCachedState().get(FalseFlowerBlock.POWER);
    }

    public void setPower(float p) {
        if (world == null || world.isClient) return;

        // POWER is defined as IntProperty.of("power", 1, 3)
        int ip = MathHelper.clamp(Math.round(p), 1, 3);

        world.setBlockState(pos, getCachedState().with(FalseFlowerBlock.POWER, ip), Block.NOTIFY_LISTENERS);
        markDirty();
    }

    public String getCustomName() { return customName; }
    public void setCustomName(String s) { this.customName = s == null ? "" : s; markDirty(); }

    /* ------------------- helpers ------------------- */

    public int radius() { return 6 * getCachedState().get(FalseFlowerBlock.POWER); }

    private float drainPerTick() {
        float base = switch (spell) {
            case AURA_LEVITATION, AURA_REGEN -> 0.06f;
            case AURA_HEAVY, CROP_GROWTH -> 0.08f;
            case BLACKHOLE -> 0.12f;
            default -> 0.09f;
        };
        float scale = getCachedState().get(FalseFlowerBlock.POWER);
        return base * scale * scale;
    }

    /* ------------------- ticking ------------------- */

    public static void tick(World w, BlockPos pos, BlockState state, FalseFlowerBlockEntity be) {
        if (w.isClient) return;

        // clean up any expiring stone prisons
        be.tickPrisonCleanup((ServerWorld) w);

        // aura ring (cheap fx)
        if (w.getTime() % 15 == 0) {
            double r = be.radius() + 0.25;
            for (int i = 0; i < 16; i++) {
                double a = i / 16.0 * Math.PI * 2.0;
                ((ServerWorld) w).spawnParticles(net.minecraft.particle.ParticleTypes.END_ROD,
                        pos.getX() + 0.5 + Math.cos(a) * r, pos.getY() + 0.1, pos.getZ() + 0.5 + Math.sin(a) * r,
                        1, 0, 0, 0, 0);
            }
        }

        if (!be.active) return;

        // drain mana
        be.mana = Math.max(0f, be.mana - be.drainPerTick());
        if (be.mana <= 0f) {
            be.active = false;
            w.setBlockState(pos, state.with(FalseFlowerBlock.ACTIVE, false), Block.NOTIFY_LISTENERS);
            be.markDirty();
            return;
        }

        final int R = be.radius();
        Box box = new Box(pos).expand(R, R, R);

        switch (be.spell) {
            case AURA_LEVITATION -> {
                for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, box, Entity::isAlive)) {
                    e.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 25, 0, true, false));
                }
            }
            case AURA_HEAVY -> {
                for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, box, Entity::isAlive)) {
                    e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 1, true, false));
                    e.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 40, 0, true, false));
                    if (!e.isOnGround()) e.addVelocity(0, -0.05, 0);
                }
            }
            case AURA_REGEN -> {
                for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, box, Entity::isAlive)) {
                    e.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 40, 0, true, true));
                }
            }
            case CROP_GROWTH -> {
                if (w.getTime() % 10 == 0) {
                    BlockPos.Mutable m = new BlockPos.Mutable();
                    for (int i = 0; i < 30; i++) {
                        m.set(pos.getX() + w.random.nextBetween(-R, R),
                                pos.getY() + w.random.nextBetween(-2, 2),
                                pos.getZ() + w.random.nextBetween(-R, R));
                        BlockState bs = w.getBlockState(m);
                        if (bs.isIn(BlockTags.CROPS)) bs.randomTick((ServerWorld) w, m, w.random);
                    }
                }
            }
            case BLACKHOLE -> {
                Vec3d c = Vec3d.ofCenter(pos);
                for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, box, Entity::isAlive)) {
                    Vec3d pull = c.subtract(e.getPos()).normalize().multiply(0.15);
                    e.addVelocity(pull.x, pull.y * 0.5, pull.z);
                }
            }
            case BUBBLE -> {
                // placeholder: slow + levitate
                for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, box, Entity::isAlive)) {
                    e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 30, 4, true, false));
                    e.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 30, 0, true, false));
                }
            }
            case AREA_MINE -> {
                mineDisk((ServerWorld) w, pos, R);
                be.mana = 0;
                be.active = false;
                w.setBlockState(pos, state.with(FalseFlowerBlock.ACTIVE, false), Block.NOTIFY_LISTENERS);
                be.markDirty();
            }
            case STONE_PRISON -> {
                List<BlockPos> targets = new ArrayList<>();
                for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, box, Entity::isAlive)) {
                    targets.add(e.getBlockPos());
                }
                be.spawnPrisons((ServerWorld) w, targets, 50); // ~2.5s at 20tps
                be.mana = 0;
                be.active = false;
                w.setBlockState(pos, state.with(FalseFlowerBlock.ACTIVE, false), Block.NOTIFY_LISTENERS);
                be.markDirty();
            }
            case RECHARGE -> { /* aura noop; handled on cast */ }
        }
    }

    private static void mineDisk(ServerWorld w, BlockPos center, int r) {
        BlockPos.Mutable m = new BlockPos.Mutable();
        int r2 = r * r;
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            if (dx * dx + dz * dz > r2) continue;
            for (int dy = -1; dy <= 2; dy++) {
                m.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                var bs = w.getBlockState(m);
                if (!bs.isAir() && bs.getHardness(w, m) >= 0 && !bs.isOf(Blocks.BEDROCK)) {
                    w.breakBlock(m, true);
                }
            }
        }
        w.playSound(null, center, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 0.6f, 0.6f);
    }

    /* ----- safe, tick-based stone prison with expiry ----- */

    private void spawnPrisons(ServerWorld w, List<BlockPos> atPositions, int lifetimeTicks) {
        long expireAt = w.getTime() + lifetimeTicks;
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (BlockPos at : atPositions) {
            for (int dx = -1; dx <= 1; dx++) for (int dy = 0; dy <= 2; dy++) for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0 && dy == 1) continue; // head gap
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
                if (w.getBlockState(p).isOf(Blocks.STONE)) {
                    w.breakBlock(p, false);
                }
                toRemove.add(p);
            }
        }
        for (BlockPos p : toRemove) prisonExpiry.removeLong(p);
    }

    /* ------------------- NBT ------------------- */

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putString("Spell", spell.name());
        nbt.putBoolean("Active", active);
        nbt.putFloat("Mana", mana);
        nbt.putString("CustomName", customName);
        nbt.putInt("TrackerId", trackerId);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        try { this.spell = FairySpell.valueOf(nbt.getString("Spell")); } catch (Exception ignored) {}
        this.active = nbt.getBoolean("Active");
        this.mana = nbt.getFloat("Mana");
        this.customName = nbt.getString("CustomName");
        this.trackerId = nbt.getInt("TrackerId");
    }

    /* ------------------- GeckoLib ------------------- */

    private final software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache cache =
            software.bernie.geckolib.util.GeckoLibUtil.createInstanceCache(this);
    @Override public software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
    @Override public void registerControllers(software.bernie.geckolib.core.animation.AnimatableManager.ControllerRegistrar controllers) {}

    /* ------------------- tracker hook ------------------- */

    @Override public void onLoad() {
        super.onLoad();
        if (!getWorld().isClient) FalseFlowerTracker.hook(this);
    }
    @Override public void markRemoved() {
        super.markRemoved();
        if (!getWorld().isClient) FalseFlowerTracker.unhook(this);
    }
}
