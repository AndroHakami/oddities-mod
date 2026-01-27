// src/main/java/net/seep/odd/mixin/umbra/PlayerEntityAirSwimTrackerMixin.java
package net.seep.odd.mixin.umbra;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import net.seep.odd.abilities.astral.OddAirSwim;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityAirSwimTrackerMixin extends LivingEntity implements OddAirSwim {

    @Unique
    private static final TrackedData<Boolean> ODD_AIR_SWIM =
            DataTracker.registerData(PlayerEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    protected PlayerEntityAirSwimTrackerMixin(EntityType<? extends LivingEntity> type, World world) {
        super(type, world);
    }

    @Inject(method = "initDataTracker", at = @At("TAIL"))
    private void oddities$initAirSwimTracker(CallbackInfo ci) {
        this.dataTracker.startTracking(ODD_AIR_SWIM, false);
    }

    @Override
    public void oddities$setAirSwim(boolean value) {
        this.dataTracker.set(ODD_AIR_SWIM, value);
    }

    @Override
    public boolean oddities$isAirSwim() {
        return this.dataTracker.get(ODD_AIR_SWIM);
    }
}
