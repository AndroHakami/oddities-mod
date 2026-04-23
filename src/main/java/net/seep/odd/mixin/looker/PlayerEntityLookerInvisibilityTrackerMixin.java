package net.seep.odd.mixin.looker;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import net.seep.odd.abilities.looker.OddLookerInvisibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityLookerInvisibilityTrackerMixin extends LivingEntity implements OddLookerInvisibility {

    @Unique
    private static final TrackedData<Boolean> ODD_LOOKER_INVISIBLE =
            DataTracker.registerData(PlayerEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    protected PlayerEntityLookerInvisibilityTrackerMixin(EntityType<? extends LivingEntity> type, World world) {
        super(type, world);
    }

    @Inject(method = "initDataTracker", at = @At("TAIL"))
    private void oddities$initLookerInvisibilityTracker(CallbackInfo ci) {
        this.dataTracker.startTracking(ODD_LOOKER_INVISIBLE, false);
    }

    @Override
    public void oddities$setLookerInvisible(boolean value) {
        this.dataTracker.set(ODD_LOOKER_INVISIBLE, value);
    }

    @Override
    public boolean oddities$isLookerInvisible() {
        return this.dataTracker.get(ODD_LOOKER_INVISIBLE);
    }
}
