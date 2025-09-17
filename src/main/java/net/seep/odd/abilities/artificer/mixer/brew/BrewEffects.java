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
 * Updated so THROW effects hit ALL LivingEntities in range (players + mobs + the thrower).
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

        // ---- DRINKABLES (unchanged; already hit the consumer which can be mob or player) ----
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

        // ---- THROWABLES (now affect ALL LivingEntities, including the thrower) ----
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

        // Example: an AoE heal that affects everyone—players and mobs—including the thrower
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

    /** Run an action on ALL LivingEntities within radius of pos (includes players AND the thrower). */
    private static void forLivingInRadius(World world, BlockPos pos, double radius, Consumer<LivingEntity> action) {
        Box aabb = new Box(pos).expand(radius);
        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, aabb, entity -> true)) {
            action.accept(e);
        }
    }
}
