package net.seep.odd.abilities.artificer.mixer.brew;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import net.seep.odd.status.ModStatusEffects;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

 // <-- adjust if your package differs

public final class RestartEffect {
    private RestartEffect() {}

    /** Effects that should NEVER be cleansed by Restart. */
    private static final Set<StatusEffect> PROTECTED = new HashSet<>();

    static {
        // Keep POWERLESS no matter what.
        PROTECTED.add(ModStatusEffects.POWERLESS);

        // Add more if you want:
        // PROTECTED.add(ModStatusEffect.SOMETHING_ELSE);
    }

    public static void apply(World world, BlockPos pos, @Nullable LivingEntity thrower, ItemStack stack) {
        if (!(world instanceof ServerWorld sw)) return;

        final double RADIUS = 4.5;

        // ---- extinguish fires in the world ----
        int r = (int) Math.ceil(RADIUS);
        BlockPos.Mutable m = new BlockPos.Mutable();

        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx*dx + dy*dy + dz*dz > RADIUS*RADIUS) continue;

                    m.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    BlockState bs = sw.getBlockState(m);

                    if (bs.isOf(Blocks.FIRE) || bs.isOf(Blocks.SOUL_FIRE)) {
                        sw.setBlockState(m, Blocks.AIR.getDefaultState(), 3);
                        continue;
                    }

                    // Extinguish lit campfires (QoL)
                    if (bs.isOf(Blocks.CAMPFIRE) && bs.contains(net.minecraft.state.property.Properties.LIT) && bs.get(net.minecraft.state.property.Properties.LIT)) {
                        sw.setBlockState(m, bs.with(net.minecraft.state.property.Properties.LIT, false), 3);
                    }
                    if (bs.isOf(Blocks.SOUL_CAMPFIRE) && bs.contains(net.minecraft.state.property.Properties.LIT) && bs.get(net.minecraft.state.property.Properties.LIT)) {
                        sw.setBlockState(m, bs.with(net.minecraft.state.property.Properties.LIT, false), 3);
                    }
                }
            }
        }

        // ---- cleanse entities ----
        Box box = new Box(pos).expand(RADIUS);
        for (LivingEntity e : sw.getEntitiesByClass(LivingEntity.class, box, ent -> true)) {

            // Remove all potion/status effects EXCEPT protected ones
            Collection<StatusEffectInstance> effects = e.getStatusEffects();
            if (!effects.isEmpty()) {
                for (StatusEffectInstance inst : java.util.List.copyOf(effects)) {
                    StatusEffect type = inst.getEffectType();
                    if (type == null) continue;
                    if (PROTECTED.contains(type)) continue; // <-- keep it
                    e.removeStatusEffect(type);
                }
            }

            // Extinguish burning
            if (e.isOnFire()) e.extinguish();
        }

        // ---- feedback ----
        sw.playSound(null, pos, SoundEvents.ENTITY_GENERIC_EXTINGUISH_FIRE, SoundCategory.BLOCKS, 1.0f, 1.1f);
        sw.playSound(null, pos, SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.BLOCKS, 0.7f, 1.7f);
    }
}
