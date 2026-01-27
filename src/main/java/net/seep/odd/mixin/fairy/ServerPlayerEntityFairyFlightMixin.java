// src/main/java/net/seep/odd/mixin/fairy/ServerPlayerEntityFairyFlightMixin.java
package net.seep.odd.mixin.fairy;

import net.minecraft.server.network.ServerPlayerEntity;
import net.seep.odd.abilities.PowerAPI;
import net.seep.odd.abilities.fairy.CastLogic;
import net.seep.odd.abilities.power.FairyPower;
import net.seep.odd.abilities.power.Powers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityFairyFlightMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void odd$fairySurvivalFlight(CallbackInfo ci) {
        ServerPlayerEntity p = (ServerPlayerEntity) (Object) this;

        // ✅ ONLY apply to Fairy power
        var pow = Powers.get(PowerAPI.get(p));
        if (!(pow instanceof FairyPower)) return;

        // ✅ If in cast form: NEVER allow flight (prevents weird vertical controls)
        boolean casting = CastLogic.isCastFormEnabled(p);

        float mana = FairyPower.getMana(p);
        boolean allow = !casting && mana > 0.01f;

        boolean changed = false;

        if (p.getAbilities().allowFlying != allow) {
            p.getAbilities().allowFlying = allow;
            changed = true;
        }

        // ✅ Always force stop flying during cast form (and when mana empty)
        if (casting || !allow) {
            if (p.getAbilities().flying) {
                p.getAbilities().flying = false;
                changed = true;
            }
        }

        if (changed) {
            p.sendAbilitiesUpdate();
        }
    }
}
