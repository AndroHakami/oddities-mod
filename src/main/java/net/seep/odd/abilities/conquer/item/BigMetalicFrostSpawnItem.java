// src/main/java/net/seep/odd/abilities/conquer/item/BigMetalicFrostSpawnItem.java
package net.seep.odd.abilities.conquer.item;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.seep.odd.status.ModStatusEffects;

public final class BigMetalicFrostSpawnItem extends Item {

    public static final int SUMMON_TICKS = 160; // 8s
    public static final String GOLEMS_KEY = "Golems";
    private static final String GUARD_KEY = "oddSummoned";

    public BigMetalicFrostSpawnItem(Settings settings) {
        super(settings);
    }

    @Override public int getMaxUseTime(ItemStack stack) { return SUMMON_TICKS; }
    @Override public UseAction getUseAction(ItemStack stack) { return UseAction.SPYGLASS; }

    @Override
    public TypedActionResult<ItemStack> use(World world, net.minecraft.entity.player.PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        stack.getOrCreateNbt().remove(GUARD_KEY);

        user.setCurrentHand(hand);
        return TypedActionResult.consume(stack);
    }

    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        if (world.isClient) return;
        if (!(world instanceof ServerWorld sw)) return;

        if ((user.age & 1) == 0) {
            spawnStorm(sw, user, 7.5, 90);
            applyDarknessAura(sw, user, 34.0, 10);
        }

        user.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 10, 3, true, false, false));

        NbtCompound nbt = stack.getOrCreateNbt();
        if (nbt.getBoolean(GUARD_KEY)) return;

        if (remainingUseTicks <= 1) {
            if (user instanceof ServerPlayerEntity sp) {
                boolean ok = summonFour(sw, sp, stack);
                if (ok) {
                    nbt.putBoolean(GUARD_KEY, true);
                    user.stopUsingItem();
                }
            }
        }
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (world.isClient) return;
        if (!(world instanceof ServerWorld sw)) return;
        if (!(user instanceof ServerPlayerEntity sp)) return;

        NbtCompound nbt = stack.getNbt();
        if (nbt != null && nbt.getBoolean(GUARD_KEY)) return;

        int used = getMaxUseTime(stack) - remainingUseTicks;
        if (used < SUMMON_TICKS - 1) {
            sp.sendMessage(Text.literal("Summon cancelled."), true);
            return;
        }

        summonFour(sw, sp, stack);
    }

    private static boolean summonFour(ServerWorld sw, ServerPlayerEntity sp, ItemStack stack) {
        NbtCompound root = stack.getNbt();
        if (root == null || !root.contains(GOLEMS_KEY, NbtElement.LIST_TYPE)) {
            sp.sendMessage(Text.literal("This spawn has no golem data."), true);
            return false;
        }

        NbtList list = root.getList(GOLEMS_KEY, NbtElement.COMPOUND_TYPE);
        if (list.isEmpty()) {
            sp.sendMessage(Text.literal("No stored golems."), true);
            return false;
        }

        Vec3d base = sp.getPos();

        Vec3d[] offsets = new Vec3d[] {
                new Vec3d( 3.0, 0,  0.0),
                new Vec3d(-3.0, 0,  0.0),
                new Vec3d( 0.0, 0,  3.0),
                new Vec3d( 0.0, 0, -3.0),
        };

        int spawned = 0;
        for (int i = 0; i < list.size() && spawned < 4; i++) {
            NbtCompound golemNbt = list.getCompound(i).copy();

            IronGolemEntity golem = new IronGolemEntity(net.minecraft.entity.EntityType.IRON_GOLEM, sw);
            golem.readNbt(golemNbt);

            Vec3d p = base.add(offsets[spawned]);
            golem.refreshPositionAndAngles(p.x, p.y, p.z, sp.getYaw(), 0f);
            golem.setPersistent();

            sw.spawnEntity(golem);

            // FORCE client to learn corruption (remove+add guarantees a packet)
            golem.removeStatusEffect(ModStatusEffects.CORRUPTION);
            golem.addStatusEffect(new StatusEffectInstance(
                    ModStatusEffects.CORRUPTION,
                    20 * 60 * 60 * 24 * 365,
                    0,
                    true,
                    false,
                    false
            ));

            sw.spawnParticles(ParticleTypes.SNOWFLAKE, p.x, p.y + 1.0, p.z, 120, 2.0, 1.7, 2.0, 0.06);
            sw.spawnParticles(ParticleTypes.CLOUD,     p.x, p.y + 0.6, p.z,  50, 1.4, 0.6, 1.4, 0.02);

            spawned++;
        }

        sw.playSound(null, sp.getBlockPos(), SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.PLAYERS, 0.9f, 1.2f);

        if (!sp.isCreative()) stack.decrement(1);
        return true;
    }

    private static void spawnStorm(ServerWorld sw, LivingEntity center, double radius, int count) {
        sw.spawnParticles(ParticleTypes.SNOWFLAKE,
                center.getX(), center.getBodyY(0.6), center.getZ(),
                count,
                radius, 1.6, radius,
                0.03);
        sw.spawnParticles(ParticleTypes.CLOUD,
                center.getX(), center.getBodyY(0.2), center.getZ(),
                Math.max(10, count / 5),
                radius * 0.75, 0.45, radius * 0.75,
                0.01);
    }

    private static void applyDarknessAura(ServerWorld sw, LivingEntity center, double radius, int durationTicks) {
        for (var p : sw.getPlayers(pl -> pl.squaredDistanceTo(center) <= radius * radius)) {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, durationTicks, 0, true, false, false));
        }
    }
}
