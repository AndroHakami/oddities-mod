package net.seep.odd.mixin.druid;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import net.seep.odd.abilities.druid.DruidData;
import net.seep.odd.abilities.druid.DruidForm;
import net.seep.odd.abilities.power.DruidPower;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityDruidRevertOnLethalMixin {

    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void odd$druidRevertIfLethal(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        PlayerEntity self = (PlayerEntity)(Object)this;
        if (self.getWorld().isClient) return;
        if (!(self instanceof ServerPlayerEntity p)) return;

        if (p.isCreative() || p.isSpectator()) return;

        ServerWorld sw = p.getServerWorld();
        DruidData data = DruidData.get(sw);
        DruidForm form = data.getCurrentForm(p.getUuid());
        if (form == DruidForm.HUMAN) return;

        // If this hit would kill you, cancel the death and revert instead.
        // We keep it simple & reliable (no rerouting).
        float hp = p.getHealth();
        if (hp - amount <= 0.5f) {
            data.setCurrentForm(p.getUuid(), DruidForm.HUMAN);
            data.setDeathCooldownUntil(p.getUuid(), sw.getTime() + DruidPower.FORM_DEATH_COOLDOWN_TICKS);

            DruidPower.serverRevertToHuman(p, true);

            // Leave you at half a heart so it feels fair.
            p.setHealth(1.0f);

            // Cancel the lethal hit
            cir.setReturnValue(true);
        }
    }
}
