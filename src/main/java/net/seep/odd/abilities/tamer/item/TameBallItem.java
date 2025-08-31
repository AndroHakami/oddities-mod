// net/seep/odd/abilities/tamer/item/TameBallItem.java
package net.seep.odd.abilities.tamer.item;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;
import net.seep.odd.abilities.tamer.projectile.TameBallEntity;

import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.animatable.client.RenderProvider;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class TameBallItem extends Item implements GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Supplier<Object> renderProvider = GeoItem.makeRenderer(this);

    public TameBallItem(Settings settings) {
        super(settings);
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    @Override public int getMaxUseTime(ItemStack stack) { return 300; }  // hold up to 2 seconds
    @Override public UseAction getUseAction(ItemStack stack) { return UseAction.BOW; }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        user.setCurrentHand(hand);
        return TypedActionResult.consume(user.getStackInHand(hand));
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remaining) {
        if (world.isClient) return;

        if (!(user instanceof net.minecraft.server.network.ServerPlayerEntity sp)) return;
        String current = net.seep.odd.abilities.PowerAPI.get(sp);
        if (!"tamer".equals(current)) {
            sp.sendMessage(Text.literal("Only Tamers can use Tame Balls."), true);
            return;
        }

        int used = getMaxUseTime(stack) - remaining;  // 0..40
        float charge = Math.min(1f, used / 20f);      // 0..1
        float speed = 0.8f + 1.2f * charge;           // 0.8..2.0

        TameBallEntity ball = new TameBallEntity(world, sp);
        var eye = sp.getEyePos();
        ball.setPosition(eye.x, sp.getEyeY() - 0.1, eye.z);
        ball.setVelocity(sp, sp.getPitch(), sp.getYaw(), 0f, speed, 0.08f);
        world.spawnEntity(ball);

        world.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.ENTITY_ENDER_PEARL_THROW, SoundCategory.PLAYERS, 0.9f, 0.5f);

        if (!sp.isCreative()) stack.decrement(1);
    }

    /* ------- GeckoLib wiring ------- */

    @Override
    public void createRenderer(Consumer<Object> consumer) {
        consumer.accept(new RenderProvider() {
            private GeoItemRenderer<?> renderer;
            @Override public GeoItemRenderer<?> getCustomRenderer() {
                if (renderer == null)
                    renderer = new net.seep.odd.abilities.tamer.client.TameBallItemRenderer();
                return renderer;
            }
        });
    }

    @Override public Supplier<Object> getRenderProvider() { return renderProvider; }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar c) {}
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
