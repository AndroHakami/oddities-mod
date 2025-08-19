package net.seep.odd.item.ghost;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.function.Consumer;
import java.util.function.Supplier;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.animatable.client.RenderProvider;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;
import software.bernie.geckolib.util.RenderUtils;

import net.seep.odd.item.ghost.client.GhostHandRenderer;
import net.seep.odd.item.ghost.client.GhostPullLoopSound;

public final class GhostHandItem extends Item implements GeoItem {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Supplier<Object> renderProvider = GeoItem.makeRenderer(this);

    public GhostHandItem(Settings settings) {
        super(settings);
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    /* ---------- Animation ---------- */

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("ghost.model.idle");
    private static final RawAnimation PULL = RawAnimation.begin().thenPlayAndHold("ghost.model.pull");
    private static final RawAnimation PUSH = RawAnimation.begin().thenPlayAndHold("ghost.model.push");

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", state -> {
            PlayerEntity p = null;
            var e = state.getData(DataTickets.ENTITY);
            if (e instanceof PlayerEntity pe) p = pe; else p = getClientPlayer();

            boolean pushing = false;
            boolean pulling = false;

            if (p != null && p.getWorld().isClient) {
                boolean holdingThis = p.getMainHandStack().isOf(this) || p.getOffHandStack().isOf(this);
                if (holdingThis) {
                    pushing = isAttackKeyDown();
                    if (!pushing) {
                        pulling = isUseKeyDown();
                    }
                }
            }

            // start/stop looped pull sound on the client in sync with the animation
            if (p != null && p.getWorld().isClient) {
                if (pulling) {
                    ensurePullLoopPlaying(p);
                } else {
                    stopPullLoop();
                }
            }

            if (pushing) {
                state.setAndContinue(PUSH);
            } else if (pulling) {
                state.setAndContinue(PULL);
            } else {
                state.setAndContinue(IDLE);
            }

            return PlayState.CONTINUE;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public double getTick(Object animatable) {
        return RenderUtils.getCurrentTick();
    }

    /* ---------- Disable vanilla "use" animation ---------- */

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        return TypedActionResult.pass(user.getStackInHand(hand));
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.NONE;
    }

    @Override
    public int getMaxUseTime(ItemStack stack) {
        return 0;
    }

    /* ---------- Item renderer wiring (GeckoLib pattern) ---------- */

    @Override
    public void createRenderer(Consumer<Object> consumer) {
        consumer.accept(new RenderProvider() {
            private GeoItemRenderer<?> renderer;

            @Override
            public GeoItemRenderer<?> getCustomRenderer() {
                if (renderer == null) {
                    renderer = new GhostHandRenderer();
                }
                return renderer;
            }
        });
    }

    @Override
    public Supplier<Object> getRenderProvider() {
        return renderProvider;
    }

    /* ---------- QoL ---------- */

    @Override
    public boolean hasGlint(ItemStack stack) { return false; }

    @Override
    public boolean isEnchantable(ItemStack stack) { return false; }

    /* ---------- Client-only helpers ---------- */

    @Environment(EnvType.CLIENT)
    private static boolean isUseKeyDown() {
        var mc = net.minecraft.client.MinecraftClient.getInstance();
        return mc != null && mc.options.useKey.isPressed();
    }

    @Environment(EnvType.CLIENT)
    private static boolean isAttackKeyDown() {
        var mc = net.minecraft.client.MinecraftClient.getInstance();
        return mc != null && mc.options.attackKey.isPressed();
    }

    @Environment(EnvType.CLIENT)
    private static PlayerEntity getClientPlayer() {
        var mc = net.minecraft.client.MinecraftClient.getInstance();
        return mc != null ? mc.player : null;
    }

    /* ---------- Pull loop sound control (client) ---------- */

    @Environment(EnvType.CLIENT)
    private static GhostPullLoopSound pullLoop;

    @Environment(EnvType.CLIENT)
    private static void ensurePullLoopPlaying(PlayerEntity p) {
        var mc = net.minecraft.client.MinecraftClient.getInstance();
        if (mc == null) return;

        // if nothing playing (or the old one marked done), start it
        if (pullLoop == null || pullLoop.isDone()) {
            pullLoop = new GhostPullLoopSound(p, () -> {
                // keep playing only while RMB is held AND this item is still in hand
                boolean holdingThis = p.getMainHandStack().getItem() instanceof GhostHandItem
                        || p.getOffHandStack().getItem() instanceof GhostHandItem;
                return holdingThis && isUseKeyDown();
            });
            mc.getSoundManager().play(pullLoop);
        }
    }

    @Environment(EnvType.CLIENT)
    private static void stopPullLoop() {
        var mc = net.minecraft.client.MinecraftClient.getInstance();
        if (mc == null) return;
        if (pullLoop != null) {
            mc.getSoundManager().stop(pullLoop);
            pullLoop = null;
        }
    }
}
