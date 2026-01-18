// src/main/java/net/seep/odd/abilities/conquer/item/MetalicFrostSpawnItem.java
package net.seep.odd.abilities.conquer.item;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
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

public final class MetalicFrostSpawnItem extends Item {

    public static final int SUMMON_TICKS = 40; // 2s
    public static final String GOLEM_KEY = "Golem";

    // internal guard so it doesn't double-fire when we stopUsingItem()
    private static final String GUARD_KEY = "oddSummoned";

    public MetalicFrostSpawnItem(Settings settings) {
        super(settings);
    }

    @Override public int getMaxUseTime(ItemStack stack) { return SUMMON_TICKS; }

    // keep zoom-ish feel
    @Override public UseAction getUseAction(ItemStack stack) { return UseAction.SPYGLASS; }

    @Override
    public TypedActionResult<ItemStack> use(World world, net.minecraft.entity.player.PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        // clear guard every new use (important for creative / non-consumed)
        stack.getOrCreateNbt().remove(GUARD_KEY);

        user.setCurrentHand(hand);
        return TypedActionResult.consume(stack);
    }

    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        if (world.isClient) return;
        if (!(world instanceof ServerWorld sw)) return;

        // visuals + aura while channeling
        if ((user.age & 1) == 0) {
            spawnStorm(sw, user, 3.5, 35);
            applyDarknessAura(sw, user, 18.0, 8);
        }

        // slow the summoner while channeling
        user.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 10, 2, true, false, false));

        // auto-finish: fire when the timer reaches the end
        NbtCompound nbt = stack.getOrCreateNbt();
        if (nbt.getBoolean(GUARD_KEY)) return;

        if (remainingUseTicks <= 1) {
            if (user instanceof ServerPlayerEntity sp) {
                boolean ok = summonOne(sw, sp, stack);
                if (ok) {
                    nbt.putBoolean(GUARD_KEY, true);
                    // stop using so it doesn't keep looping the channel
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

        // if we already summoned from usageTick, do nothing
        NbtCompound nbt = stack.getNbt();
        if (nbt != null && nbt.getBoolean(GUARD_KEY)) return;

        int used = getMaxUseTime(stack) - remainingUseTicks;

        // allow a tiny tolerance (tick timing)
        if (used < SUMMON_TICKS - 1) {
            sp.sendMessage(Text.literal("Summon cancelled."), true);
            return;
        }

        summonOne(sw, sp, stack);
    }

    private static boolean summonOne(ServerWorld sw, ServerPlayerEntity sp, ItemStack stack) {
        NbtCompound root = stack.getNbt();
        if (root == null || !root.contains(GOLEM_KEY, 10)) {
            sp.sendMessage(Text.literal("This spawn has no golem data."), true);
            return false;
        }

        NbtCompound golemNbt = root.getCompound(GOLEM_KEY).copy();

        IronGolemEntity golem = new IronGolemEntity(net.minecraft.entity.EntityType.IRON_GOLEM, sw);
        golem.readNbt(golemNbt);

        // position BEFORE spawn (important)
        Vec3d forward = sp.getRotationVec(1.0f).normalize().multiply(2.2);
        Vec3d pos = sp.getPos().add(forward);

        golem.refreshPositionAndAngles(pos.x, pos.y, pos.z, sp.getYaw(), 0f);
        golem.setPersistent();

        // spawn now so it's tracked
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

        // burst VFX at the spawn position
        sw.spawnParticles(ParticleTypes.SNOWFLAKE, pos.x, pos.y + 1.0, pos.z, 160, 2.2, 1.8, 2.2, 0.06);
        sw.spawnParticles(ParticleTypes.CLOUD,     pos.x, pos.y + 0.6, pos.z,  60, 1.6, 0.6, 1.6, 0.02);

        sw.playSound(null, golem.getBlockPos(), SoundEvents.ENTITY_IRON_GOLEM_REPAIR, SoundCategory.PLAYERS, 1.0f, 0.7f);

        if (!sp.isCreative()) stack.decrement(1);
        return true;
    }

    private static void spawnStorm(ServerWorld sw, LivingEntity center, double radius, int count) {
        sw.spawnParticles(ParticleTypes.SNOWFLAKE,
                center.getX(), center.getBodyY(0.6), center.getZ(),
                count,
                radius, 1.2, radius,
                0.02);
        sw.spawnParticles(ParticleTypes.CLOUD,
                center.getX(), center.getBodyY(0.2), center.getZ(),
                Math.max(6, count / 4),
                radius * 0.65, 0.35, radius * 0.65,
                0.01);
    }

    private static void applyDarknessAura(ServerWorld sw, LivingEntity center, double radius, int durationTicks) {
        for (var p : sw.getPlayers(pl -> pl.squaredDistanceTo(center) <= radius * radius)) {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, durationTicks, 0, true, false, false));
        }
    }
}
