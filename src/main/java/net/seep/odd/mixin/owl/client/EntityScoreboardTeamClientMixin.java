package net.seep.odd.mixin.owl.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.scoreboard.Team;
import net.seep.odd.abilities.owl.client.OwlSonarClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(Entity.class)
public abstract class EntityScoreboardTeamClientMixin {

    @Inject(method = "getScoreboardTeam", at = @At("HEAD"), cancellable = true)
    private void odd$owlOverrideTeam(CallbackInfoReturnable<Team> cir) {
        Entity self = (Entity)(Object)this;

        if (OwlSonarClient.shouldGlow(self)) {
            Team t = OwlSonarClient.getOrCreateSonarTeam();
            if (t != null) cir.setReturnValue(t);
        }
    }
}
