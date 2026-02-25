// src/main/java/net/seep/odd/abilities/power/ChefPower.java
package net.seep.odd.abilities.power;

import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import net.minecraft.particle.DustParticleEffect;
import net.seep.odd.abilities.chef.RecipeBookContent;
import org.joml.Vector3f;

import net.seep.odd.abilities.chef.ChefData;
import net.seep.odd.block.ModBlocks;
import net.seep.odd.block.supercooker.SuperCookerBlock;
import net.seep.odd.block.supercooker.SuperCookerBlockEntity;
import net.seep.odd.status.ModStatusEffects;

public final class ChefPower implements Power {
    @Override public String id() { return "chef"; }
    @Override public String displayName() { return "Chef"; }

    @Override
    public boolean hasSlot(String slot) {
        return "primary".equals(slot) || "secondary".equals(slot);
    }

    @Override public long cooldownTicks() { return 10; }           // cooker toggle
    @Override public long secondaryCooldownTicks() { return 10; }  // quick, but not spammy
    @Override public long thirdCooldownTicks() { return 0; }

    @Override
    public Identifier iconTexture(String slot) {
        return switch (slot) {
            case "primary"   -> new Identifier("odd", "textures/gui/abilities/chef_cooker.png");
            case "secondary" -> new Identifier("odd", "textures/gui/abilities/chef_recipe_book.png"); // add this texture (or reuse cooker)
            default          -> new Identifier("odd", "textures/gui/abilities/ability_default.png");
        };
    }

    @Override public String longDescription() {
        return "Summon a Super Cooker. Cooking is a rhythm of stirring and timing. "
                + "Secondary: pull out your Recipe Book.";
    }

    private static boolean isPowerless(ServerPlayerEntity player) {
        return player != null && player.hasStatusEffect(ModStatusEffects.POWERLESS);
    }

    /* ============================================================
     * PRIMARY: Cooker toggle / spawn (unchanged)
     * ============================================================ */

    @Override
    public void activate(ServerPlayerEntity p) {
        // POWERLESS: deny activation (no toggle / no spawn / no despawn)
        if (isPowerless(p)) {
            p.sendMessage(Text.literal("§cYou are powerless."), true);
            return;
        }

        ServerWorld sw = (ServerWorld) p.getWorld();
        ChefData data = ChefData.get(sw);

        // Toggle: if cooker exists, despawn it
        ChefData.CookerRef ref = data.getCooker(p.getUuid());
        if (ref != null) {
            // only despawn if same dimension
            Identifier dim = sw.getRegistryKey().getValue();
            if (dim.equals(ref.dimension)) {
                BlockPos oldPos = ref.pos;
                if (sw.getBlockState(oldPos).isOf(ModBlocks.SUPER_COOKER)) {
                    // drop ingredients/result/fuel before removing
                    if (sw.getBlockEntity(oldPos) instanceof SuperCookerBlockEntity be) {
                        be.dropAllCookerContents(sw);
                    }
                    sw.setBlockState(oldPos, net.minecraft.block.Blocks.AIR.getDefaultState(), 3);

                    // Jakarta-skyline dust burst (purple + orange)
                    dustBurst(sw, Vec3d.ofCenter(oldPos).add(0, 0.6, 0));

                    sw.playSound(null, oldPos, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 0.6f, 1.35f);
                    p.sendMessage(Text.literal("Cooker dismissed."), true);
                }
                data.clearCooker(p.getUuid());
                return;
            }
            // different dimension: just clear stale ref
            data.clearCooker(p.getUuid());
        }

        // Spawn new cooker above the block you’re looking at
        HitResult hr = p.raycast(6.0, 0.0f, false);
        if (!(hr instanceof BlockHitResult bhr) || hr.getType() != HitResult.Type.BLOCK) {
            p.sendMessage(Text.literal("Look at a block to place the cooker."), true);
            return;
        }

        BlockPos place = bhr.getBlockPos().up();
        if (!sw.getBlockState(place).isAir()) {
            p.sendMessage(Text.literal("Not enough space above that block."), true);
            return;
        }

        Direction facing = p.getHorizontalFacing().getOpposite();
        BlockState state = ModBlocks.SUPER_COOKER.getDefaultState();
        if (state.contains(SuperCookerBlock.FACING)) {
            state = state.with(SuperCookerBlock.FACING, facing);
        }

        sw.setBlockState(place, state, 3);

        if (sw.getBlockEntity(place) instanceof SuperCookerBlockEntity be) {
            be.serverStartEmerge();
        }

        data.setCooker(p.getUuid(), sw.getRegistryKey().getValue(), place);
        sw.playSound(null, place, SoundEvents.BLOCK_ANVIL_PLACE, SoundCategory.BLOCKS, 0.6f, 1.2f);
        p.sendMessage(Text.literal("Cooker summoned."), true);
    }

    /* ============================================================
     * SECONDARY: Pull Recipe Book (prewritten, uneditable)
     * ============================================================ */

    private static final String RECIPE_BOOK_MARK = "odd_recipe_book";

    @Override
    public void activateSecondary(ServerPlayerEntity p) {
        if (isPowerless(p)) {
            p.sendMessage(Text.literal("§cYou are powerless."), true);
            return;
        }
        if (!(p.getWorld() instanceof ServerWorld sw)) return;

        // If you already have the recipe book somewhere, pull it into main hand.
        if (pullExistingRecipeBookToMainHand(p)) {
            bookFx(sw, p.getPos().add(0, 1.0, 0));
            sw.playSound(null, p.getBlockPos(), SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 0.75f, 1.05f);
            p.sendMessage(Text.literal("Recipe Book: ready."), true);
            return;
        }

        // Otherwise create it and place into main hand (moving main-hand item to inv/drop)
        ItemStack book = makeRecipeBook(p);

        ItemStack existing = p.getMainHandStack();
        if (!existing.isEmpty()) {
            if (!p.getInventory().insertStack(existing.copy())) {
                p.dropItem(existing.copy(), false);
            }
        }

        p.setStackInHand(Hand.MAIN_HAND, book);

        bookFx(sw, p.getPos().add(0, 1.0, 0));
        sw.playSound(null, p.getBlockPos(), SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 0.8f, 1.10f);
        p.sendMessage(Text.literal("Recipe Book pulled out."), true);
    }

    /** Finds a marked recipe book in offhand/inventory and swaps it into main hand. */
    private static boolean pullExistingRecipeBookToMainHand(ServerPlayerEntity p) {
        // already in main hand
        if (isRecipeBook(p.getMainHandStack())) return true;

        // offhand -> swap
        if (isRecipeBook(p.getOffHandStack())) {
            ItemStack main = p.getMainHandStack();
            ItemStack off  = p.getOffHandStack();
            p.setStackInHand(Hand.MAIN_HAND, off);
            p.setStackInHand(Hand.OFF_HAND, main);
            return true;
        }

        // inventory slot -> swap with main hand
        for (int i = 0; i < p.getInventory().size(); i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (!isRecipeBook(s)) continue;

            ItemStack main = p.getMainHandStack();
            p.getInventory().setStack(i, main);           // put main item into that slot
            p.setStackInHand(Hand.MAIN_HAND, s);          // put recipe book into main hand
            return true;
        }

        return false;
    }

    private static boolean isRecipeBook(ItemStack s) {
        if (s == null || s.isEmpty()) return false;
        if (!s.isOf(Items.WRITTEN_BOOK)) return false;
        NbtCompound n = s.getNbt();
        return n != null && n.getBoolean(RECIPE_BOOK_MARK);
    }

    /** Creates a WRITTEN_BOOK with fixed pages (uneditable). */
    private static ItemStack makeRecipeBook(ServerPlayerEntity p) {
        ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);

        // mark so we can detect/pull reliably
        stack.getOrCreateNbt().putBoolean(RECIPE_BOOK_MARK, true);

        // fill content from separate file
        RecipeBookContent.applyToWrittenBook(stack);

        return stack;
    }

    /** Minimal JSON page builder (avoids Text.Serializer version differences). */


    /** Book “pull” FX: same palette as cooker (purple + orange), tighter burst at player. */
    private static void bookFx(ServerWorld sw, Vec3d at) {
        DustParticleEffect purple = new DustParticleEffect(new Vector3f(0.75f, 0.35f, 0.95f), 1.2f);
        DustParticleEffect orange = new DustParticleEffect(new Vector3f(1.0f, 0.55f, 0.15f), 1.2f);

        for (int i = 0; i < 22; i++) {
            double dx = (sw.random.nextDouble() - 0.5) * 0.55;
            double dy = (sw.random.nextDouble()) * 0.35;
            double dz = (sw.random.nextDouble() - 0.5) * 0.55;
            sw.spawnParticles((i % 2 == 0) ? purple : orange,
                    at.x, at.y, at.z,
                    1, dx, dy, dz, 0.02);
        }
    }

    private static void dustBurst(ServerWorld sw, Vec3d at) {
        DustParticleEffect purple = new DustParticleEffect(new Vector3f(0.75f, 0.35f, 0.95f), 1.6f);
        DustParticleEffect orange = new DustParticleEffect(new Vector3f(1.0f, 0.55f, 0.15f), 1.6f);

        for (int i = 0; i < 40; i++) {
            double dx = (sw.random.nextDouble() - 0.5) * 1.2;
            double dy = (sw.random.nextDouble()) * 0.7;
            double dz = (sw.random.nextDouble() - 0.5) * 1.2;
            sw.spawnParticles((i % 2 == 0) ? purple : orange,
                    at.x, at.y, at.z,
                    1, dx, dy, dz, 0.02);
        }
    }
}