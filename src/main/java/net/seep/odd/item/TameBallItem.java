// net/seep/odd/item/TameBallItem.java
package net.seep.odd.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.seep.odd.abilities.PowerAPI;
import net.seep.odd.abilities.tamer.projectile.TameBallEntity;

public class TameBallItem extends Item {
    public TameBallItem(Settings settings) { super(settings); }

    @Override public int getMaxUseTime(ItemStack stack) { return 72000; }           // bow-like
    @Override public net.minecraft.util.UseAction getUseAction(ItemStack stack) { return UseAction.BOW; }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        // Only Tamers can throw
        if (user instanceof ServerPlayerEntity sp) {
            String id = PowerAPI.get(sp); // adjust if your power id differs
            if (!"tamer".equals(id)) {
                sp.sendMessage(net.minecraft.text.Text.literal("Only Tamers can use Tame Balls."), true);
                return TypedActionResult.fail(user.getStackInHand(hand));
            }
        }
        user.setCurrentHand(hand);
        return TypedActionResult.consume(user.getStackInHand(hand));
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, net.minecraft.entity.LivingEntity user, int remainingUseTicks) {
        if (!(user instanceof ServerPlayerEntity sp)) return;

        int used = this.getMaxUseTime(stack) - remainingUseTicks;
        float charge = Math.min(1.0f, used / 20.0f);            // 0..1 after ~1s
        float speed  = 0.6f + 1.8f * charge;                     // throw farther when charged
        float inacc  = 0.0f;

        TameBallEntity ball = new TameBallEntity(world, sp);
        Vec3d eye = sp.getEyePos();
        ball.setPosition(eye.x, eye.y - 0.1, eye.z);
        Vec3d dir = sp.getRotationVec(1.0f);
        ball.setVelocity(dir.x * speed, dir.y * speed, dir.z * speed, 1.0f, inacc);
        world.spawnEntity(ball);

        world.playSound(null, sp.getBlockPos(), SoundEvents.ITEM_TRIDENT_THROW, SoundCategory.PLAYERS, 0.6f, 1.2f);

        if (!sp.getAbilities().creativeMode) {
            stack.decrement(1);
        }
        sp.incrementStat(Stats.USED.getOrCreateStat(this));
    }
}
