// src/main/java/net/seep/odd/mixin/umbra/PlayerEntityUmbraBoostInputMixin.java
package net.seep.odd.mixin.umbra;

import net.minecraft.entity.player.PlayerEntity;
import net.seep.odd.abilities.astral.OddUmbraBoostInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityUmbraBoostInputMixin implements OddUmbraBoostInput {

    @Unique private boolean oddities$umbraBoostHeld = false;
    @Unique private int oddities$umbraBoostTick = -999999;

    @Override
    public void oddities$setUmbraBoostHeld(boolean held, int age) {
        this.oddities$umbraBoostHeld = held;
        this.oddities$umbraBoostTick = age;
    }

    @Override
    public boolean oddities$isUmbraBoostHeld(int age) {
        // safety: if packets stop, don’t get stuck boosting forever
        if (age - oddities$umbraBoostTick > 20) { // 1s stale
            oddities$umbraBoostHeld = false;
        }
        return oddities$umbraBoostHeld;
    }
}
