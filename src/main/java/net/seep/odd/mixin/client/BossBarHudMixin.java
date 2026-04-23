package net.seep.odd.mixin.client;

import net.minecraft.client.gui.hud.BossBarHud;
import net.seep.odd.quest.client.QuestClientState;
import net.seep.odd.quest.client.QuestHudOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(BossBarHud.class)
public abstract class BossBarHudMixin {
    @ModifyArg(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/hud/BossBarHud;renderBossBar(Lnet/minecraft/client/gui/DrawContext;IILnet/minecraft/entity/boss/BossBar;)V"
            ),
            index = 2
    )
    private int odd$moveQuestBossBarDown(int y) {
        if (QuestClientState.INSTANCE.hasActiveQuest()) {
            return y + QuestHudOverlay.getBossBarYOffset();
        }
        return y;
    }
}