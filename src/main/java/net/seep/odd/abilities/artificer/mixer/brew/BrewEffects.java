package net.seep.odd.abilities.artificer.mixer.brew;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Central registry for Artificer brew effects.
 * DRINK applies to the user (LivingEntity), THROW applies in-world at a position.
 * THROW effects hit ALL LivingEntities in range (players + mobs + the thrower).
 */
public final class BrewEffects {
    private BrewEffects() {}

    @FunctionalInterface public interface DrinkEffect { void apply(World world, LivingEntity user, ItemStack stack); }
    @FunctionalInterface public interface ThrowEffect { void apply(World world, BlockPos pos, @Nullable LivingEntity thrower, ItemStack stack); }

    private static final Map<String, DrinkEffect> DRINK = new HashMap<>();
    private static final Map<String, ThrowEffect> THROW = new HashMap<>();
    private static boolean defaultsRegistered = false;

    public static void registerDrink(String id, DrinkEffect fx) { DRINK.put(id, fx); }
    public static void registerThrow(String id, ThrowEffect fx) { THROW.put(id, fx); }

    /** Call once during common init. */
    public static void registerDefaults() {
        if (defaultsRegistered) return;
        defaultsRegistered = true;

        // ---- DRINKABLES ----
        registerDrink("speed_tonic", (world, user, stack) -> {
            user.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 20 * 45, 1));
            world.playSound(null, user.getBlockPos(), SoundEvents.ENTITY_WITCH_DRINK, SoundCategory.PLAYERS, 0.8f, 1.6f);
        });

        registerDrink("healing_draught", (world, user, stack) -> {
            user.heal(6.0f);
            world.playSound(null, user.getBlockPos(), SoundEvents.ENTITY_GENERIC_DRINK, SoundCategory.PLAYERS, 0.8f, 1.2f);
        });

        registerDrink("fire_guard", (world, user, stack) -> {
            user.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 20 * 60, 0));
            world.playSound(null, user.getBlockPos(), SoundEvents.ITEM_TOTEM_USE, SoundCategory.PLAYERS, 0.6f, 1.4f);
        });

        // ---- THROWABLES ----
        registerThrow("shockwave", (world, pos, thrower, stack) -> {
            double radius = 4.0;
            forLivingInRadius(world, pos, radius, target -> {
                Vec3d push = target.getPos().subtract(Vec3d.ofCenter(pos)).normalize().multiply(0.9);
                target.addVelocity(push.x, 0.35, push.z);
                target.velocityModified = true;
            });
            world.playSound(null, pos, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 0.7f, 1.0f);
        });

        registerThrow("cleanse_fire", (world, pos, thrower, stack) -> {
            int r = 3;
            BlockPos.Mutable m = new BlockPos.Mutable();
            for (int dx = -r; dx <= r; dx++)
                for (int dy = -1; dy <= 2; dy++)
                    for (int dz = -r; dz <= r; dz++) {
                        m.set(pos.getX()+dx, pos.getY()+dy, pos.getZ()+dz);
                        if (world.getBlockState(m).isOf(Blocks.FIRE)) {
                            world.setBlockState(m, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                        }
                    }
            world.playSound(null, pos, SoundEvents.ENTITY_GENERIC_EXTINGUISH_FIRE, SoundCategory.BLOCKS, 0.9f, 1.1f);
        });

        // Crystaline Trap — impact crystals + extra crystals on targets; rooted + no-jump; grounded correctly
        registerThrow("crystaline_trap", (world, pos, thrower, stack) -> {
            if (!(world instanceof net.minecraft.server.world.ServerWorld sw)) return;

            final double RADIUS = 5.0;
            final int DURATION_TICKS = 20 * 5; // 5s
            final net.minecraft.util.math.random.Random rng = sw.getRandom();

            // Track all displays so we can despawn them
            final java.util.List<net.minecraft.entity.decoration.DisplayEntity.BlockDisplayEntity> spawnedAll = new java.util.ArrayList<>();

            // ---------------- base crystals at the impact point ----------------
            final double cx = pos.getX() + 0.5;
            final double cz = pos.getZ() + 0.5;
            double[] ringRadii  = {1.1, 1.8, 2.6};
            int[]    ringCounts = {18, 24, 30};

            for (int rIndex = 0; rIndex < ringRadii.length; rIndex++) {
                double rad = ringRadii[rIndex];
                int count  = ringCounts[rIndex];

                for (int i = 0; i < count; i++) {
                    double ang = (i / (double) count) * (Math.PI * 2) + (rng.nextDouble() - 0.5) * 0.25;
                    double bx  = cx + Math.cos(ang) * rad;
                    double bz  = cz + Math.sin(ang) * rad;

                    int ix = net.minecraft.util.math.BlockPos.ofFloored(bx, 0.0, bz).getX();
                    int iz = net.minecraft.util.math.BlockPos.ofFloored(bx, 0.0, bz).getZ();

                    // heightmap for impact site is usually fine
                    int topY = sw.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, ix, iz);
                    var ground = new net.minecraft.util.math.BlockPos(ix, topY - 1, iz);
                    if (topY <= sw.getBottomY() || sw.getBlockState(ground).isAir()) continue;

                    net.minecraft.block.BlockState state;
                    switch (rng.nextInt(4)) {
                        default -> state = net.minecraft.block.Blocks.AMETHYST_CLUSTER.getDefaultState();
                        case 1   -> state = net.minecraft.block.Blocks.LARGE_AMETHYST_BUD.getDefaultState();
                        case 2   -> state = net.minecraft.block.Blocks.MEDIUM_AMETHYST_BUD.getDefaultState();
                        case 3   -> state = net.minecraft.block.Blocks.SMALL_AMETHYST_BUD.getDefaultState();
                    }

                    var disp = net.minecraft.entity.EntityType.BLOCK_DISPLAY.create(sw);
                    if (disp != null) {
                        ((net.seep.odd.mixin.BlockDisplayEntityAccessor)(Object) disp).odd$setBlockState(state);
                        disp.setNoGravity(true);
                        disp.setInvulnerable(true);

                        double y = topY + 0.01 + rng.nextDouble() * 0.4;
                        disp.refreshPositionAndAngles(bx, y, bz, (float)(rng.nextDouble() * 360.0), 0.0f);

                        // auto-expire tagging (cleaned by CrystalTrapCleaner)
                        disp.addCommandTag("odd_crystal_trap");
                        long expireAt = sw.getTime() + DURATION_TICKS + 2;
                        disp.addCommandTag("odd_exp_" + expireAt);

                        sw.spawnEntity(disp);
                        spawnedAll.add(disp);
                    }
                }
            }

            // ---------------- effects & extra crystals on targets ----------------
            net.minecraft.util.math.Box aabb = new net.minecraft.util.math.Box(pos).expand(RADIUS);
            var targets = sw.getEntitiesByClass(net.minecraft.entity.LivingEntity.class, aabb, e -> true);

            for (var t : targets) {
                // root
                t.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                        net.minecraft.entity.effect.StatusEffects.SLOWNESS, DURATION_TICKS, 10, false, true, true));
                t.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                        net.minecraft.entity.effect.StatusEffects.MINING_FATIGUE, DURATION_TICKS, 2, false, true, true));
                var v0 = t.getVelocity();
                t.setVelocity(0, Math.min(0, v0.y * 0.3), 0);
                t.velocityModified = true;

                // no jump for the duration
                for (int dt = 0; dt <= DURATION_TICKS; dt++) {
                    net.seep.odd.util.TickScheduler.runLater(sw, dt, () -> {
                        if (!t.isAlive()) return;
                        t.setJumping(false);
                        var v = t.getVelocity();
                        if (v.y > 0) { t.setVelocity(v.x, -0.02, v.z); t.velocityModified = true; }
                        t.fallDistance = 0.0F;
                    });
                }

                // extra crystals around target — find a surface *near the target's Y* (works indoors/caves)
                int extras = 10 + rng.nextInt(6); // 10..15
                int yHint  = net.minecraft.util.math.BlockPos.ofFloored(t.getX(), t.getY(), t.getZ()).getY();

                for (int i = 0; i < extras; i++) {
                    double ang  = (i / (double) extras) * (Math.PI * 2) + (rng.nextDouble() - 0.5) * 0.35;
                    double dist = 0.8 + rng.nextDouble() * 0.6;
                    double bx   = t.getX() + Math.cos(ang) * dist;
                    double bz   = t.getZ() + Math.sin(ang) * dist;

                    int ix = net.minecraft.util.math.BlockPos.ofFloored(bx, yHint, bz).getX();
                    int iz = net.minecraft.util.math.BlockPos.ofFloored(bx, yHint, bz).getZ();

                    // scan downward up to 8 blocks to find the first solid block under the hint
                    int placeY = -1;
                    for (int dy = 0; dy <= 8; dy++) {
                        int y = yHint - dy;
                        var p = new net.minecraft.util.math.BlockPos(ix, y, iz);
                        if (!sw.getBlockState(p).isAir() && !sw.getBlockState(p).getCollisionShape(sw, p).isEmpty()) {
                            placeY = y + 1; // surface is one above the solid block
                            break;
                        }
                    }
                    // fallback to heightmap (ignores leaves) if nothing solid was found nearby
                    if (placeY < 0) {
                        placeY = sw.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, ix, iz);
                    }
                    if (placeY <= sw.getBottomY()) continue;
                    var ground = new net.minecraft.util.math.BlockPos(ix, placeY - 1, iz);
                    if (sw.getBlockState(ground).isAir()) continue;

                    net.minecraft.block.BlockState state;
                    switch (rng.nextInt(4)) {
                        default -> state = net.minecraft.block.Blocks.AMETHYST_CLUSTER.getDefaultState();
                        case 1   -> state = net.minecraft.block.Blocks.LARGE_AMETHYST_BUD.getDefaultState();
                        case 2   -> state = net.minecraft.block.Blocks.MEDIUM_AMETHYST_BUD.getDefaultState();
                        case 3   -> state = net.minecraft.block.Blocks.SMALL_AMETHYST_BUD.getDefaultState();
                    }

                    var disp = net.minecraft.entity.EntityType.BLOCK_DISPLAY.create(sw);
                    if (disp != null) {
                        ((net.seep.odd.mixin.BlockDisplayEntityAccessor)(Object) disp).odd$setBlockState(state);
                        disp.setNoGravity(true);
                        disp.setInvulnerable(true);

                        double y = placeY + 0.01 + rng.nextDouble() * 0.35;
                        disp.refreshPositionAndAngles(bx, y, bz, (float)(rng.nextDouble() * 360.0), 0.0f);

                        // auto-expire tagging (cleaned by CrystalTrapCleaner)
                        disp.addCommandTag("odd_crystal_trap");
                        long expireAt = sw.getTime() + DURATION_TICKS + 2;
                        disp.addCommandTag("odd_exp_" + expireAt);

                        sw.spawnEntity(disp);
                        spawnedAll.add(disp);
                    }
                }
            }

            // cleanup after duration (runtime)
            net.seep.odd.util.TickScheduler.runLater(sw, DURATION_TICKS, () -> {
                for (var e : spawnedAll) if (e.isAlive()) e.discard();
            });

            // sound
            sw.playSound(null, pos, net.minecraft.sound.SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE,
                    net.minecraft.sound.SoundCategory.BLOCKS, 1.0f, 0.9f + sw.getRandom().nextFloat() * 0.2f);
        });

        registerThrow("frost_burst", (world, pos, thrower, stack) -> {
            int r = 3;
            BlockPos.Mutable m = new BlockPos.Mutable();
            for (int dx = -r; dx <= r; dx++)
                for (int dy = -1; dy <= 1; dy++)
                    for (int dz = -r; dz <= r; dz++) {
                        m.set(pos.getX()+dx, pos.getY()+dy, pos.getZ()+dz);
                        var bs = world.getBlockState(m);
                        if (bs.isOf(Blocks.WATER)) {
                            world.setBlockState(m, Blocks.ICE.getDefaultState(), Block.NOTIFY_ALL);
                        }
                    }
            world.playSound(null, pos, SoundEvents.BLOCK_GLASS_PLACE, SoundCategory.BLOCKS, 0.6f, 1.4f);
        });

        registerThrow("gaia_bloom", (world, pos, thrower, stack) -> {
            int r = 3;
            BlockPos.Mutable ground = new BlockPos.Mutable();
            BlockPos.Mutable above  = new BlockPos.Mutable();
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++) {
                    ground.set(pos.getX()+dx, pos.getY(), pos.getZ()+dz);
                    above.set(ground.getX(), ground.getY()+1, ground.getZ());
                    if (world.getBlockState(ground).isOf(Blocks.GRASS_BLOCK) && world.isAir(above)) {
                        world.setBlockState(above, Math.random() < 0.5 ? Blocks.DANDELION.getDefaultState() : Blocks.POPPY.getDefaultState(), Block.NOTIFY_LISTENERS);
                    }
                }
            world.playSound(null, pos, SoundEvents.ITEM_BONE_MEAL_USE, SoundCategory.BLOCKS, 0.7f, 1.2f);
        });

        registerThrow("healing_pulse", (world, pos, thrower, stack) -> {
            double radius = 4.0;
            forLivingInRadius(world, pos, radius, target -> target.heal(4.0f));
            world.playSound(null, pos, SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.BLOCKS, 0.8f, 1.2f);
        });
    }

    /* ===== apply helpers ===== */
    public static void applyDrink(World world, LivingEntity user, String brewId, ItemStack stack) {
        if (brewId == null || brewId.isEmpty()) return;
        var fx = DRINK.get(brewId);
        if (fx != null) fx.apply(world, user, stack);
        else if (user instanceof PlayerEntity p && world.isClient) p.sendMessage(Text.literal("Unknown drink brew: " + brewId), true);
    }

    public static void applyThrowable(World world, BlockPos pos, @Nullable LivingEntity thrower, String brewId, ItemStack stack) {
        if (brewId == null || brewId.isEmpty()) return;
        var fx = THROW.get(brewId);
        if (fx != null) fx.apply(world, pos, thrower, stack);
        else if (thrower instanceof PlayerEntity p && world.isClient) p.sendMessage(Text.literal("Unknown throw brew: " + brewId), true);
    }

    /* ===== utilities ===== */
    private static void forLivingInRadius(World world, BlockPos pos, double radius, Consumer<LivingEntity> action) {
        Box aabb = new Box(pos).expand(radius);
        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, aabb, entity -> true)) {
            action.accept(e);
        }
    }
}
