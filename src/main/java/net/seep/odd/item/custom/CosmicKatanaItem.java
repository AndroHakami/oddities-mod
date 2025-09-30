// net/seep/odd/item/custom/CosmicKatanaItem.java
package net.seep.odd.item.custom;

import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.*;
import net.minecraft.sound.SoundEvents;
import net.minecraft.sound.SoundCategory;
import net.minecraft.stat.Stats;
import net.minecraft.util.*;
import net.minecraft.world.World;
import net.minecraft.util.math.MathHelper;
import net.seep.odd.abilities.power.CosmicPower;

import java.util.UUID;

public class CosmicKatanaItem extends SwordItem {
    private static final UUID ATTACK_SPEED_MOD = UUID.fromString("b21bcd43-26a7-4b0a-b76f-9b2d1b3b3a6a");
    private static final int BLOCK_MAX_USE_TICKS = 72000;
    private static final float BLOCK_DAMAGE_REDUCTION = 0.5f; // 50%

    public CosmicKatanaItem(Settings settings) {
        // Light, fast katana feel (tweak to taste)
        super(ToolMaterials.NETHERITE, 3, -2.2f, settings);
        installBlockReductionHook();
    }

    // Old-style sword block feel
    @Override public UseAction getUseAction(ItemStack stack) { return UseAction.BLOCK; }
    @Override public int getMaxUseTime(ItemStack stack) { return BLOCK_MAX_USE_TICKS; }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        user.setCurrentHand(hand);
        user.incrementStat(Stats.USED.getOrCreateStat(this));
        if (!world.isClient) {
            world.playSound(null, user.getX(), user.getY(), user.getZ(),
                    SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 0.6f, 1.25f);
        }
        return TypedActionResult.consume(stack);
    }

    private static boolean hookInstalled = false;
    private static void installBlockReductionHook() {
        if (hookInstalled) return;
        hookInstalled = true;

        // Reduce incoming damage while actively "blocking" with the katana
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof PlayerEntity player)) return true;
            ItemStack active = player.isUsingItem() ? player.getActiveItem() : ItemStack.EMPTY;
            if (!active.isEmpty() && active.getItem() instanceof CosmicKatanaItem) {
                // Minor stamina/cooldown tax to avoid perma-block cheese
                if (!player.getItemCooldownManager().isCoolingDown(active.getItem())) {
                    player.getItemCooldownManager().set(active.getItem(), 8);
                }
                entity.getWorld().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                        SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 0.25f, 1.35f);
                return amount * (1f - BLOCK_DAMAGE_REDUCTION) <= 0 ? false : true;
            }
            return true;
        });
    }
}
