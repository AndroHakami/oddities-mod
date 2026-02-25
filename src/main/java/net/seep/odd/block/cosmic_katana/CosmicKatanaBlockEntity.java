// src/main/java/net/seep/odd/block/cosmic_katana/CosmicKatanaBlockEntity.java
package net.seep.odd.block.cosmic_katana;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;

import net.seep.odd.abilities.PowerAPI;
import net.seep.odd.block.ModBlocks;
import net.seep.odd.item.ModItems;
import net.seep.odd.status.ModStatusEffects;

import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

public class CosmicKatanaBlockEntity extends BlockEntity implements GeoBlockEntity {

    private static final int CLAIM_DELAY_TICKS = 50; // 2.5s @ 20tps

    private boolean claiming = false;
    private long claimFinishTick = 0L;
    private UUID claimer;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private static final RawAnimation IDLE    = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation UNLEASH = RawAnimation.begin().thenPlay("unleash").thenLoop("idle");

    public CosmicKatanaBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.COSMIC_KATANA_BLOCK_BE, pos, state);
    }

    public boolean isClaiming() { return claiming; }

    private static boolean isCosmic(ServerPlayerEntity sp) {
        if (sp.hasStatusEffect(ModStatusEffects.POWERLESS)) return false;
        return "cosmic".equals(PowerAPI.get(sp));
    }

    private static boolean hasKatana(ServerPlayerEntity sp) {
        for (int i = 0; i < sp.getInventory().size(); i++) {
            var s = sp.getInventory().getStack(i);
            if (!s.isEmpty() && s.isOf(ModItems.COSMIC_KATANA)) return true;
        }
        return false;
    }

    /** Called by block on successful right click. */
    public void serverStartClaim(ServerPlayerEntity sp) {
        if (!(world instanceof ServerWorld sw)) return;
        if (claiming) return;

        claiming = true;
        claimer = sp.getUuid();
        claimFinishTick = sw.getTime() + CLAIM_DELAY_TICKS;

        markDirty();
        sw.getChunkManager().markForUpdate(pos);

        // Play unleash anim for nearby clients
        CosmicKatanaBlockNet.broadcastUnleash(sw, pos);

        // Optional sound cue
        sw.playSound(null, pos, SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.BLOCKS, 0.8f, 1.4f);
    }

    public static void tickServer(ServerWorld sw, BlockPos pos, CosmicKatanaBlockEntity be) {
        if (!be.claiming) return;
        if (sw.getTime() < be.claimFinishTick) return;

        // Finish claim now
        be.claiming = false;

        ServerPlayerEntity sp = (be.claimer != null)
                ? sw.getServer().getPlayerManager().getPlayer(be.claimer)
                : null;

        be.claimer = null;
        be.claimFinishTick = 0L;

        be.markDirty();
        sw.getChunkManager().markForUpdate(pos);

        // If player gone or no longer cosmic, just cancel (block remains)
        if (sp == null || !isCosmic(sp)) {
            return;
        }

        // If they already have it by now, just delete block (still “taken”)
        if (hasKatana(sp)) {
            sw.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
            return;
        }

        // Give katana
        var katana = new net.minecraft.item.ItemStack(ModItems.COSMIC_KATANA);
        if (!sp.getInventory().insertStack(katana)) {
            sp.dropItem(katana, false);
        }

        sp.playSound(SoundEvents.ENTITY_ENDER_DRAGON_FLAP, SoundCategory.PLAYERS, 0.9f, 1.15f);

        // Remove the block after the sword is taken
        sw.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
    }

    /* ------------ GeckoLib ------------ */

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        AnimationController<CosmicKatanaBlockEntity> ctrl =
                new AnimationController<>(this, "main", state -> {
                    state.setAndContinue(IDLE);
                    return PlayState.CONTINUE;
                });

        ctrl.triggerableAnim("unleash", UNLEASH);
        controllers.add(ctrl);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    /* ------------ NBT (persist claim if chunk unloads) ------------ */

    @Override
    public void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putBoolean("Claiming", claiming);
        nbt.putLong("ClaimFinish", claimFinishTick);
        if (claimer != null) nbt.putUuid("Claimer", claimer);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        claiming = nbt.getBoolean("Claiming");
        claimFinishTick = nbt.getLong("ClaimFinish");
        claimer = nbt.containsUuid("Claimer") ? nbt.getUuid("Claimer") : null;
    }
}