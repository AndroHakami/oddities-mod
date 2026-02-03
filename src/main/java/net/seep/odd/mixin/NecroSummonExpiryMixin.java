package net.seep.odd.mixin;

import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MobEntity.class)
public abstract class NecroSummonExpiryMixin {

    @Unique private static final String ODD_TAG_SUMMONED = "odd_necro_summoned";
    @Unique private static final String ODD_TAG_EXPIRES_PREFIX = "odd_necro_expires:";

    @Unique private boolean odd$expiryScanned = false;
    @Unique private long odd$expireAtTick = Long.MIN_VALUE; // world time tick

    @Inject(method = "tick", at = @At("HEAD"))
    private void odd$necroExpireTick(CallbackInfo ci) {
        MobEntity self = (MobEntity)(Object)this;

        if (self.getWorld().isClient) return;

        // don’t do string scanning every tick
        if ((self.age & 7) != 0) return; // every 8 ticks

        // only our summoned mobs
        if (!self.getCommandTags().contains(ODD_TAG_SUMMONED)) return;

        if (!odd$expiryScanned) {
            odd$expiryScanned = true;

            for (String tag : self.getCommandTags()) {
                if (tag.startsWith(ODD_TAG_EXPIRES_PREFIX)) {
                    try {
                        odd$expireAtTick = Long.parseLong(tag.substring(ODD_TAG_EXPIRES_PREFIX.length()));
                    } catch (Exception ignored) {
                        // if malformed, treat as "never"
                        odd$expireAtTick = Long.MAX_VALUE;
                    }
                    break;
                }
            }
        }

        if (odd$expireAtTick == Long.MIN_VALUE) return;

        long now = self.getWorld().getTime();
        if (now >= odd$expireAtTick) {
            // kill via damage so normal death hooks fire (your corpse spawn, etc.)
            self.damage(self.getDamageSources().magic(), Float.MAX_VALUE);
        }
    }
}
