// FILE: src/main/java/net/seep/odd/abilities/artificer/mixer/brew/CreationEffect.java
package net.seep.odd.abilities.artificer.mixer.brew;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.seep.odd.block.ModBlocks;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public final class CreationEffect {
    private CreationEffect() {}

    private static final Map<Block, EntityType<?>> FLOWER_TO_ENTITY = new HashMap<>();
    static {
        // edit freely
        FLOWER_TO_ENTITY.put(Blocks.DANDELION, EntityType.BEE);
        FLOWER_TO_ENTITY.put(Blocks.POPPY, EntityType.CHICKEN);
        FLOWER_TO_ENTITY.put(Blocks.CORNFLOWER, EntityType.ALLAY);
        FLOWER_TO_ENTITY.put(Blocks.AZURE_BLUET, EntityType.RABBIT);
        FLOWER_TO_ENTITY.put(Blocks.LILY_OF_THE_VALLEY, EntityType.HORSE);
        FLOWER_TO_ENTITY.put(Blocks.OXEYE_DAISY, EntityType.SHEEP);
        FLOWER_TO_ENTITY.put(Blocks.BLUE_ORCHID, EntityType.FROG);

        // non-flowers (still valid triggers)
        FLOWER_TO_ENTITY.put(Blocks.CACTUS, EntityType.CAMEL);
        FLOWER_TO_ENTITY.put(Blocks.CAVE_VINES_PLANT, EntityType.AXOLOTL);
        FLOWER_TO_ENTITY.put(Blocks.SWEET_BERRY_BUSH, EntityType.FOX);
        FLOWER_TO_ENTITY.put(ModBlocks.FALSE_FLOWER, EntityType.SNIFFER);

        FLOWER_TO_ENTITY.put(Blocks.WITHER_ROSE, EntityType.WITHER_SKELETON);

        // corals / sea stuff (also not flowers)
        FLOWER_TO_ENTITY.put(Blocks.FIRE_CORAL, EntityType.PUFFERFISH);
        FLOWER_TO_ENTITY.put(Blocks.BRAIN_CORAL, EntityType.SQUID);
        FLOWER_TO_ENTITY.put(Blocks.BUBBLE_CORAL, EntityType.DOLPHIN);
        FLOWER_TO_ENTITY.put(Blocks.SEA_PICKLE, EntityType.TURTLE);
        FLOWER_TO_ENTITY.put(Blocks.DEAD_BRAIN_CORAL, EntityType.GLOW_SQUID);
        FLOWER_TO_ENTITY.put(Blocks.TUBE_CORAL, EntityType.TROPICAL_FISH);
    }

    public static void apply(World world, BlockPos pos, @Nullable LivingEntity thrower, ItemStack stack) {
        if (!(world instanceof ServerWorld sw)) return;

        // impact on block, or try block below (if it hits air above)
        BlockPos hit = pos;
        BlockState st = sw.getBlockState(hit);
        if (st.isAir()) {
            hit = pos.down();
            st = sw.getBlockState(hit);
        }

        Block block = st.getBlock();

        // ✅ allow either real flowers OR anything explicitly mapped (cactus/vines/coral/etc)
        boolean allowed = st.isIn(BlockTags.FLOWERS) || FLOWER_TO_ENTITY.containsKey(block);
        if (!allowed) {
            sw.playSound(null, pos, net.seep.odd.sound.ModSounds.CREATION, SoundCategory.BLOCKS, 1.0f, 1.0f);
            return;
        }

        EntityType<?> type = FLOWER_TO_ENTITY.get(block);
        if (type == null) {
            // fallback for “other flowers”
            type = EntityType.BEE;
        }

        var ent = type.create(sw);
        if (ent != null) {
            ent.refreshPositionAndAngles(
                    hit.getX() + 0.5, hit.getY() + 1.0, hit.getZ() + 0.5,
                    sw.getRandom().nextFloat() * 360f, 0f
            );
            sw.spawnEntity(ent);

            sw.playSound(null, hit, SoundEvents.ENTITY_EVOKER_CAST_SPELL, SoundCategory.BLOCKS, 0.9f, 1.5f);
            sw.spawnParticles(net.minecraft.particle.ParticleTypes.HAPPY_VILLAGER,
                    hit.getX() + 0.5, hit.getY() + 1.0, hit.getZ() + 0.5,
                    30, 0.4, 0.4, 0.4, 0.05
            );
        }
    }
}