package net.seep.odd.abilities.power;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.FluidTags;
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
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.seep.odd.abilities.firesword.entity.FireSwordProjectileEntity;
import net.seep.odd.abilities.firesword.item.FireSwordItem;
import net.seep.odd.entity.ModEntities;
import net.seep.odd.item.ModItems;

public final class FireSwordPower implements Power {

    @Override public String id() { return "firesword"; }

    @Override
    public boolean hasSlot(String slot) {
        return "primary".equals(slot) || "secondary".equals(slot);
    }

    @Override public long cooldownTicks() { return 0; }
    @Override public long secondaryCooldownTicks() { return 2 * 20; }

    @Override
    public Identifier iconTexture(String slot) {
        return switch (slot) {
            case "primary"   -> new Identifier("odd", "textures/gui/abilities/firesword_toggle.png");
            case "secondary" -> new Identifier("odd", "textures/gui/abilities/firesword_throw.png");
            default          -> new Identifier("odd", "textures/gui/abilities/ability_default.png");
        };
    }

    @Override
    public String slotLongDescription(String slot) {
        return switch (slot) {
            case "primary"   -> "Conjure a Fire Sword by consuming a fire source (items, lava sources, or ground flame). One source = one sword.";
            case "secondary" -> "Throw your conjured Fire Sword. On impact it ignites and explodes, destroying the sword.";
            default          -> "FireSword";
        };
    }

    @Override
    public String longDescription() {
        return "Conjure a fragile but deadly Fire Sword by consuming a fire source (items, lava sources, or ground flame). "
                + "Strikes ignite targets. Throwing it causes a fiery explosion on impact, destroying it. "
                + "You gain hidden Fire Resistance while you have the FireSword power.";
    }

    /** POWER passive: hidden Fire Resistance for power owner (NOT tied to the item). */
    public static void serverTick(ServerPlayerEntity player) {
        var cur = player.getStatusEffect(StatusEffects.FIRE_RESISTANCE);
        if (cur == null || cur.getDuration() < 40) {
            player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.FIRE_RESISTANCE,
                    220,
                    0,
                    true,
                    false,
                    false
            ));
        }
    }

    /**
     * Primary: Conjure ONLY.
     * - No more "despawn/dismiss" toggle.
     * - Consumes ONE fire product (NO flint & steel), OR consumes a lava SOURCE block, OR consumes ground fire (extinguishes it).
     */
    @Override
    public void activate(ServerPlayerEntity player) {
        // Already holding a conjured sword? Don't despawn; just refuse to conjure again.
        if (isConjuredFireSword(player.getMainHandStack())) {
            player.sendMessage(Text.literal("You already wield a Fire Sword."), true);
            return;
        }

        boolean consumed =
                consumeWorldFireOrLava(player)   // lava source blocks / ground fire get extinguished
                        || consumeFireProductOneUse(player); // inventory items (NOT flint & steel)

        if (!consumed) {
            player.sendMessage(Text.literal("Need a fire source (fire charge / blaze powder / magma cream / lava bucket, or consume ground fire / lava source)."), true);
            return;
        }

        // Create conjured sword
        ItemStack conjured = new ItemStack(ModItems.FIRE_SWORD);
        conjured.getOrCreateNbt().putBoolean(FireSwordItem.SUMMONED_NBT, true);

        // Move existing mainhand item into inventory (or drop)
        ItemStack existing = player.getMainHandStack();
        if (!existing.isEmpty()) {
            if (!player.getInventory().insertStack(existing.copy())) {
                player.dropItem(existing.copy(), false);
            }
        }

        player.setStackInHand(Hand.MAIN_HAND, conjured);

        // Small conjure cue
        player.getWorld().playSound(
                null,
                player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_FIRECHARGE_USE,
                SoundCategory.PLAYERS,
                0.7f,
                0.95f + player.getWorld().random.nextFloat() * 0.15f
        );

        player.sendMessage(Text.literal("Fire Sword: CONJURED"), true);
    }

    /** Secondary: throw the conjured sword and destroy it immediately. */
    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld sw)) return;

        ItemStack main = player.getMainHandStack();
        if (!isConjuredFireSword(main)) return;

        FireSwordProjectileEntity proj = new FireSwordProjectileEntity(ModEntities.FIRE_SWORD_PROJECTILE, sw, player);
        proj.setItem(main.copyWithCount(1));

        Vec3d look = player.getRotationVec(1.0f).normalize();
        proj.setPos(player.getX(), player.getEyeY() - 0.1, player.getZ());
        proj.setVelocity(look.x, look.y, look.z, 1.8f, 0.5f);

        sw.spawnEntity(proj);

        // Destroy conjured sword
        player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
    }

    /* ===== helpers ===== */

    private static boolean isConjuredFireSword(ItemStack stack) {
        return !stack.isEmpty()
                && stack.isOf(ModItems.FIRE_SWORD)
                && stack.hasNbt()
                && stack.getNbt() != null
                && stack.getNbt().getBoolean(FireSwordItem.SUMMONED_NBT);
    }

    /**
     * Inventory fire products ONLY (flint & steel does NOT count).
     * One source = one sword.
     */
    private static boolean consumeFireProductOneUse(ServerPlayerEntity player) {
        // Hands first (feels best)
        if (consumeFromHand(player, Hand.MAIN_HAND)) return true;
        if (consumeFromHand(player, Hand.OFF_HAND)) return true;

        // Then inventory
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack s = player.getInventory().getStack(i);
            if (s.isEmpty()) continue;

            // Lava bucket -> bucket
            if (s.isOf(Items.LAVA_BUCKET)) {
                player.getInventory().setStack(i, new ItemStack(Items.BUCKET));
                return true;
            }

            // single-consume fuels
            if (s.isOf(Items.FIRE_CHARGE) || s.isOf(Items.BLAZE_POWDER) || s.isOf(Items.MAGMA_CREAM)) {
                s.decrement(1);
                if (s.isEmpty()) player.getInventory().setStack(i, ItemStack.EMPTY);
                return true;
            }
        }

        return false;
    }

    private static boolean consumeFromHand(ServerPlayerEntity player, Hand hand) {
        ItemStack s = player.getStackInHand(hand);
        if (s.isEmpty()) return false;

        if (s.isOf(Items.LAVA_BUCKET)) {
            player.setStackInHand(hand, new ItemStack(Items.BUCKET));
            return true;
        }

        if (s.isOf(Items.FIRE_CHARGE) || s.isOf(Items.BLAZE_POWDER) || s.isOf(Items.MAGMA_CREAM)) {
            s.decrement(1);
            if (s.isEmpty()) player.setStackInHand(hand, ItemStack.EMPTY);
            return true;
        }

        // Flint & steel explicitly DOES NOT count.
        return false;
    }

    /**
     * World sources:
     * - Consumes / extinguishes ground fire (FIRE, SOUL_FIRE)
     * - Consumes lava SOURCE blocks (still lava) by removing them
     * - Also supports lit campfires (extinguishes them)
     *
     * Returns true if a world source was consumed.
     */
    private static boolean consumeWorldFireOrLava(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld sw)) return false;

        // 1) Try raycast (use OUTLINE so non-colliding blocks like fire can be targeted)
        BlockHitResult bhr = raycastBlock(sw, player, 5.0);
        if (bhr != null && bhr.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = bhr.getBlockPos();
            // If we hit the side of a block, the "fire" might be in the adjacent space
            // so also check the offset position in the hit direction.
            if (tryConsumeAt(sw, player, pos)) return true;

            Direction side = bhr.getSide();
            BlockPos adj = pos.offset(side);
            if (tryConsumeAt(sw, player, adj)) return true;
        }

        // 2) Fallback: scan near feet for ground flame / lava source (helps "flame on ground!")
        BlockPos base = player.getBlockPos();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                // check same level + one below (common for ground fire and lava)
                BlockPos p0 = base.add(dx, 0, dz);
                BlockPos p1 = base.add(dx, -1, dz);

                if (tryConsumeAt(sw, player, p0)) return true;
                if (tryConsumeAt(sw, player, p1)) return true;
            }
        }

        return false;
    }

    private static BlockHitResult raycastBlock(ServerWorld sw, ServerPlayerEntity player, double range) {
        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVec(1.0f);
        Vec3d end = eye.add(look.multiply(range));

        return sw.raycast(new RaycastContext(
                eye, end,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.ANY,
                player
        ));
    }

    private static boolean tryConsumeAt(ServerWorld sw, ServerPlayerEntity player, BlockPos pos) {
        BlockState st = sw.getBlockState(pos);

        // Ground flame -> extinguish (remove)
        if (st.isOf(Blocks.FIRE) || st.isOf(Blocks.SOUL_FIRE)) {
            sw.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            sw.playSound(null, pos, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 0.8f, 1.0f);
            return true;
        }

        // Campfire -> extinguish (keep block, turn off)
        if (st.getBlock() instanceof CampfireBlock && st.get(CampfireBlock.LIT)) {
            sw.setBlockState(pos, st.with(CampfireBlock.LIT, false), Block.NOTIFY_ALL);
            sw.playSound(null, pos, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 0.75f, 0.95f);
            return true;
        }

        // Lava source -> remove (consume)
        FluidState fs = st.getFluidState();
        if (!fs.isEmpty() && fs.isIn(FluidTags.LAVA) && fs.isStill()) {
            sw.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            sw.playSound(null, pos, SoundEvents.BLOCK_LAVA_EXTINGUISH, SoundCategory.BLOCKS, 0.9f, 1.0f);
            return true;
        }

        return false;
    }
}
