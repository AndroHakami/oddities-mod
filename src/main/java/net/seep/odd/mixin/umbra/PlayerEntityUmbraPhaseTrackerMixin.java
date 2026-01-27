// src/main/java/net/seep/odd/mixin/umbra/PlayerEntityUmbraPhaseTrackerMixin.java
package net.seep.odd.mixin.umbra;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import net.seep.odd.abilities.astral.OddUmbraPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityUmbraPhaseTrackerMixin extends LivingEntity implements OddUmbraPhase {

    @Unique
    private static final TrackedData<Boolean> ODD_UMBRA_PHASING =
            DataTracker.registerData(PlayerEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    protected PlayerEntityUmbraPhaseTrackerMixin(EntityType<? extends LivingEntity> type, World world) {
        super(type, world);
    }

    @Inject(method = "initDataTracker", at = @At("TAIL"))
    private void oddities$initUmbraPhaseTracker(CallbackInfo ci) {
        this.dataTracker.startTracking(ODD_UMBRA_PHASING, false);
    }

    @Override
    public void oddities$setUmbraPhasing(boolean value) {
        this.dataTracker.set(ODD_UMBRA_PHASING, value);
    }

    @Override
    public boolean oddities$isUmbraPhasing() {
        return this.dataTracker.get(ODD_UMBRA_PHASING);
    }
}
