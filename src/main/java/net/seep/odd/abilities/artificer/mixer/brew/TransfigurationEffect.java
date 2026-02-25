// FILE: src/main/java/net/seep/odd/abilities/artificer/mixer/brew/TransfigurationEffect.java
package net.seep.odd.abilities.artificer.mixer.brew;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import net.seep.odd.entity.ModEntities;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public final class TransfigurationEffect {
    private TransfigurationEffect() {}

    public static final double RADIUS = 5.0;

    // status swaps (edit freely)
    private static final Map<StatusEffect, StatusEffect> POS_TO_NEG = new HashMap<>();
    private static final Map<StatusEffect, StatusEffect> NEG_TO_POS = new HashMap<>();
    static {
        POS_TO_NEG.put(StatusEffects.SPEED, StatusEffects.SLOWNESS);
        POS_TO_NEG.put(StatusEffects.STRENGTH, StatusEffects.WEAKNESS);
        POS_TO_NEG.put(StatusEffects.REGENERATION, StatusEffects.POISON);
        POS_TO_NEG.put(StatusEffects.HASTE, StatusEffects.MINING_FATIGUE);
        POS_TO_NEG.put(StatusEffects.NIGHT_VISION, StatusEffects.BLINDNESS);
        POS_TO_NEG.put(StatusEffects.FIRE_RESISTANCE, StatusEffects.WITHER); // spicy

        NEG_TO_POS.put(StatusEffects.SLOWNESS, StatusEffects.SPEED);
        NEG_TO_POS.put(StatusEffects.WEAKNESS, StatusEffects.STRENGTH);
        NEG_TO_POS.put(StatusEffects.POISON, StatusEffects.REGENERATION);
        NEG_TO_POS.put(StatusEffects.WITHER, StatusEffects.REGENERATION);
        NEG_TO_POS.put(StatusEffects.MINING_FATIGUE, StatusEffects.HASTE);
        NEG_TO_POS.put(StatusEffects.BLINDNESS, StatusEffects.NIGHT_VISION);
    }

    // entity swaps (edit freely)
    private static final Map<EntityType<?>, EntityType<?>> ENTITY_SWAP = new HashMap<>();
    static {
        ENTITY_SWAP.put(EntityType.VILLAGER, EntityType.ZOMBIE_VILLAGER);
        ENTITY_SWAP.put(EntityType.ZOMBIE_VILLAGER, EntityType.VILLAGER);
        ENTITY_SWAP.put(ModEntities.CORRUPTED_VILLAGER, EntityType.VILLAGER);
        ENTITY_SWAP.put(ModEntities.CORRUPTED_IRON_GOLEM, EntityType.IRON_GOLEM);
        ENTITY_SWAP.put(EntityType.ZOMBIE, EntityType.HUSK);
        ENTITY_SWAP.put(EntityType.HUSK, EntityType.DROWNED);
        ENTITY_SWAP.put(EntityType.SKELETON, EntityType.STRAY);
        ENTITY_SWAP.put(EntityType.STRAY, EntityType.SKELETON);
        ENTITY_SWAP.put(EntityType.SPIDER, EntityType.CAVE_SPIDER);
        ENTITY_SWAP.put(EntityType.CAVE_SPIDER, EntityType.SPIDER);
        ENTITY_SWAP.put(EntityType.COW, EntityType.MOOSHROOM);
        ENTITY_SWAP.put(EntityType.MOOSHROOM, EntityType.COW);
        ENTITY_SWAP.put(EntityType.BEE, EntityType.PARROT);
        ENTITY_SWAP.put(EntityType.PARROT, EntityType.BEE);
        ENTITY_SWAP.put(EntityType.SLIME, EntityType.MAGMA_CUBE);
        ENTITY_SWAP.put(EntityType.MAGMA_CUBE, EntityType.SLIME);
    }

    // plant swaps (edit freely) – includes cactus loop
    private static final Map<Block, BlockState> PLANT_SWAP = new HashMap<>();
    static {
        PLANT_SWAP.put(Blocks.GRASS, Blocks.FERN.getDefaultState());
        PLANT_SWAP.put(Blocks.FERN, Blocks.DEAD_BUSH.getDefaultState());
        PLANT_SWAP.put(Blocks.DEAD_BUSH, Blocks.CACTUS.getDefaultState());

        PLANT_SWAP.put(Blocks.CACTUS, Blocks.SUGAR_CANE.getDefaultState());
        PLANT_SWAP.put(Blocks.SUGAR_CANE, Blocks.CACTUS.getDefaultState());

        PLANT_SWAP.put(Blocks.DANDELION, Blocks.POPPY.getDefaultState());
        PLANT_SWAP.put(Blocks.POPPY, Blocks.CORNFLOWER.getDefaultState());
        PLANT_SWAP.put(Blocks.CORNFLOWER, Blocks.DANDELION.getDefaultState());
    }

    public static void apply(World world, BlockPos pos, @Nullable LivingEntity thrower, ItemStack stack) {
        if (!(world instanceof ServerWorld sw)) return;

        // 1) entities in radius: swap status effects + maybe swap entity type
        Box box = new Box(pos).expand(RADIUS, 3.0, RADIUS);
        for (LivingEntity e : sw.getEntitiesByClass(LivingEntity.class, box, ent -> ent.isAlive())) {
            if (e instanceof net.minecraft.entity.player.PlayerEntity) {
                // players: only swap status effects (don’t swap type)
                swapStatusEffects(e);
                continue;
            }

            swapStatusEffects(e);

            EntityType<?> next = ENTITY_SWAP.get(e.getType());
            if (next != null) {
                var newEnt = next.create(sw);
                if (newEnt instanceof LivingEntity ne) {
                    ne.refreshPositionAndAngles(e.getX(), e.getY(), e.getZ(), e.getYaw(), e.getPitch());

                    // carry name + roughly carry health ratio
                    ne.setCustomName(e.getCustomName());
                    float pct = e.getHealth() / e.getMaxHealth();
                    ne.setHealth(Math.max(1.0f, ne.getMaxHealth() * pct));

                    sw.spawnEntity(ne);
                    e.discard();
                }
            }
        }

        // 2) plants in radius: swap plants
        int r = (int)Math.ceil(RADIUS);
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int dy = -1; dy <= 2; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx*dx + dz*dz > r*r) continue;

                    m.set(pos.getX()+dx, pos.getY()+dy, pos.getZ()+dz);
                    BlockState st = sw.getBlockState(m);

                    if (st.isAir()) continue;

                    BlockState out = PLANT_SWAP.get(st.getBlock());

                    // generic plant tag fallback
                    if (out == null && st.isIn(BlockTags.FLOWERS)) {
                        out = Blocks.DANDELION.getDefaultState();
                    }

                    if (out == null) continue;

                    sw.setBlockState(m, out, Block.NOTIFY_ALL);
                    sw.spawnParticles(net.minecraft.particle.ParticleTypes.HAPPY_VILLAGER,
                            m.getX()+0.5, m.getY()+0.7, m.getZ()+0.5,
                            1, 0.08, 0.10, 0.08, 0.01);
                }
            }
        }

        sw.playSound(null, pos, SoundEvents.ENTITY_ILLUSIONER_CAST_SPELL, SoundCategory.BLOCKS, 0.9f, 1.35f);
    }

    private static void swapStatusEffects(LivingEntity e) {
        var list = e.getStatusEffects().stream().toList();
        if (list.isEmpty()) return;

        // remove all, re-add swapped
        for (var inst : list) e.removeStatusEffect(inst.getEffectType());

        for (StatusEffectInstance inst : list) {
            StatusEffect fx = inst.getEffectType();
            StatusEffect out = null;

            if (fx.getCategory() == StatusEffectCategory.BENEFICIAL) out = POS_TO_NEG.get(fx);
            else if (fx.getCategory() == StatusEffectCategory.HARMFUL) out = NEG_TO_POS.get(fx);

            if (out == null) out = fx; // if unknown, keep

            e.addStatusEffect(new StatusEffectInstance(out, inst.getDuration(), inst.getAmplifier(), false, true, true));
        }
    }
}
