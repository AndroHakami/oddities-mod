package net.seep.odd.mixin;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.seep.odd.abilities.tamer.data.TamerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(MobEntity.class)
public abstract class MobEntityTamerDataMixin implements TamerData {
    @Unique private boolean odd$tamed = false;
    @Unique private UUID odd$tamerOwner = null;
    @Unique private int odd$level = 1;
    @Unique private int odd$xp = 0;

    // --- Persist to NBT ---
    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void odd$writeTamer(NbtCompound nbt, CallbackInfo ci) {
        NbtCompound tag = new NbtCompound();
        tag.putBoolean("tamed", odd$tamed);
        if (odd$tamerOwner != null) tag.putUuid("owner", odd$tamerOwner);
        tag.putInt("level", odd$level);
        tag.putInt("xp", odd$xp);
        nbt.put("odd.tamer", tag);
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void odd$readTamer(NbtCompound nbt, CallbackInfo ci) {
        if (!nbt.contains("odd.tamer", 10)) return;
        NbtCompound tag = nbt.getCompound("odd.tamer");
        odd$tamed = tag.getBoolean("tamed");
        if (tag.containsUuid("owner")) odd$tamerOwner = tag.getUuid("owner");
        odd$level = Math.max(1, tag.getInt("level"));
        odd$xp = Math.max(0, tag.getInt("xp"));
    }

    // --- Interface impl ---
    @Override public boolean odd$isTamed() { return odd$tamed; }
    @Override public void odd$setTamed(boolean b) { odd$tamed = b; }
    @Override public UUID odd$getTamerOwner() { return odd$tamerOwner; }
    @Override public void odd$setTamerOwner(UUID id) { odd$tamerOwner = id; }
    @Override public int odd$getLevel() { return odd$level; }
    @Override public void odd$setLevel(int lvl) { odd$level = Math.max(1, lvl); }
    @Override public int odd$getXp() { return odd$xp; }
    @Override public void odd$setXp(int xp) { odd$xp = Math.max(0, xp); }
}
