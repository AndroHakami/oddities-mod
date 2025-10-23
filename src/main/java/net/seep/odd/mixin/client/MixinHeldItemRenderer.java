package net.seep.odd.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.item.ItemStack;
import net.seep.odd.abilities.gamble.item.GambleRevolverItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public abstract class MixinHeldItemRenderer {
    @Shadow private float equipProgressMainHand;
    @Shadow private float prevEquipProgressMainHand;
    @Shadow private float equipProgressOffHand;
    @Shadow private float prevEquipProgressOffHand;
    @Shadow private ItemStack mainHand;
    @Shadow private ItemStack offHand;

    /** Ensure the revolver never re-equip dips or desyncs the renderer's stacks. */
    @Inject(method = "updateHeldItems", at = @At("TAIL"))
    private void odd$lockEquipForRevolver(CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return;

        ItemStack curMain = mc.player.getMainHandStack();
        ItemStack curOff  = mc.player.getOffHandStack();

        if (curMain.getItem() instanceof GambleRevolverItem) {
            // Force renderer to use exactly the player's stack & keep fully equipped
            this.mainHand = curMain;
            this.prevEquipProgressMainHand = 1.0F;
            this.equipProgressMainHand     = 1.0F;
        }
        if (curOff.getItem() instanceof GambleRevolverItem) {
            this.offHand = curOff;
            this.prevEquipProgressOffHand = 1.0F;
            this.equipProgressOffHand     = 1.0F;
        }
    }
}
