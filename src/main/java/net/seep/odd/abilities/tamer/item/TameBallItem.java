// net/seep/odd/abilities/tamer/item/TameBallItem.java
package net.seep.odd.abilities.tamer.item;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.world.World;
import net.seep.odd.abilities.tamer.projectile.TameBallEntity;

// GeckoLib item renderer hook
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.animatable.client.RenderProvider;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.renderer.GeoItemRenderer;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class TameBallItem extends Item implements GeoItem {
    private final Supplier<Object> renderProvider = GeoItem.makeRenderer(this);

    public TameBallItem(Settings settings) {
        super(settings);
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    // ---- use: hold to charge, release to throw ----
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        user.setCurrentHand(hand);
        return TypedActionResult.consume(user.getStackInHand(hand));
    }

    @Override public int getMaxUseTime(ItemStack stack) { return 40; }  // up to 2s hold
    @Override public UseAction getUseAction(ItemStack stack) { return UseAction.BOW; }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remaining) {
        if (world.isClient) return;

        // Only the Tamer can throw
        if (!(user instanceof net.minecraft.server.network.ServerPlayerEntity sp)) return;
        String current = net.seep.odd.abilities.PowerAPI.get(sp);
        if (!"tamer".equals(current)) {
            sp.sendMessage(Text.literal("Only Tamers can use Tame Balls."), true);
            return;
        }

        int used = getMaxUseTime(stack) - remaining;           // 0..40
        float charge = Math.min(1f, used / 20f);               // 0..1 (0–1s)
        double speed = 0.7 + charge * 1.3;                     // 0.7..2.0

        TameBallEntity ball = new TameBallEntity(world, sp);
        ball.setPosition(sp.getEyePos().getX(), sp.getEyeY() - 0.1, sp.getEyePos().getZ());
        ball.setVelocity(sp, sp.getPitch(), sp.getYaw(), 0f, (float)speed, 0.1f);
        world.spawnEntity(ball);

        world.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.ENTITY_SNOWBALL_THROW, SoundCategory.PLAYERS, 0.7f, 1.1f);

        if (!sp.isCreative()) stack.decrement(1);
    }

    // ---- GeckoLib item renderer wiring ----
    @Override
    public void createRenderer(Consumer<Object> consumer) {
        consumer.accept(new RenderProvider() {
            private GeoItemRenderer<?> renderer;

            @Override
            public GeoItemRenderer<?> getCustomRenderer() {
                if (renderer == null) renderer = new net.seep.odd.abilities.tamer.client.TameBallItemRenderer();
                return renderer;
            }
        });
    }

    @Override public Supplier<Object> getRenderProvider() { return renderProvider; }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllerRegistrar) {

    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return null;
    }
}
