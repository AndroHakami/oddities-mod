// FILE: src/main/java/net/seep/odd/abilities/wizard/client/WizardClientInit.java
package net.seep.odd.abilities.wizard.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;

import net.seep.odd.abilities.power.WizardPower;
import net.seep.odd.abilities.wizard.WizardElement;
import net.seep.odd.abilities.wizard.entity.client.CapybaraFamiliarRenderer;
import net.seep.odd.entity.ModEntities;

@Environment(EnvType.CLIENT)
public final class WizardClientInit {
    private WizardClientInit() {}

    public static void initClient() {

        // ---------- Renderers ----------
        EntityRendererRegistry.register(ModEntities.CAPYBARA_FAMILIAR, CapybaraFamiliarRenderer::new);

        // Projectiles (Option A)
        EntityRendererRegistry.register(ModEntities.WIZARD_FIRE_PROJECTILE, ctx -> new FlyingItemEntityRenderer<>(ctx));
        EntityRendererRegistry.register(ModEntities.WIZARD_WATER_PROJECTILE, ctx -> new FlyingItemEntityRenderer<>(ctx));
        EntityRendererRegistry.register(ModEntities.WIZARD_EARTH_PROJECTILE, ctx -> new FlyingItemEntityRenderer<>(ctx));

        // Tornado / cloud / quake can be no-render (they do particles server-side)
        EntityRendererRegistry.register(ModEntities.WIZARD_TORNADO, WizardNoRenderRenderer::new);
        EntityRendererRegistry.register(ModEntities.WIZARD_FIRE_TORNADO, WizardNoRenderRenderer::new);
        EntityRendererRegistry.register(ModEntities.WIZARD_STEAM_CLOUD, WizardNoRenderRenderer::new);
        EntityRendererRegistry.register(ModEntities.WIZARD_EARTHQUAKE, WizardNoRenderRenderer::new);

        // Meteor (NEW) - no-render, it uses block-displays + particles
        EntityRendererRegistry.register(ModEntities.WIZARD_METEOR, WizardNoRenderRenderer::new);

        // ---------- S2C: open wheels ----------
        ClientPlayNetworking.registerGlobalReceiver(WizardPower.S2C_OPEN_ELEMENT_WHEEL, (client, handler, buf, response) -> {
            client.execute(() -> MinecraftClient.getInstance().setScreen(new WizardElementWheelScreen()));
        });

        ClientPlayNetworking.registerGlobalReceiver(WizardPower.S2C_OPEN_COMBO_WHEEL, (client, handler, buf, response) -> {
            client.execute(() -> MinecraftClient.getInstance().setScreen(new WizardComboWheelScreen()));
        });

        // ---------- S2C: mana sync ----------
        ClientPlayNetworking.registerGlobalReceiver(WizardPower.S2C_MANA_SYNC, (client, handler, buf, response) -> {
            boolean has = buf.readBoolean();
            float mana = buf.readFloat();
            float max  = buf.readFloat();
            client.execute(() -> WizardClientState.setMana(has, mana, max));
        });

        // ---------- S2C: element sync ----------
        ClientPlayNetworking.registerGlobalReceiver(WizardPower.S2C_ELEMENT_SYNC, (client, handler, buf, response) -> {
            int id = buf.readInt();
            WizardElement e = WizardElement.fromId(id);
            client.execute(() -> WizardClientState.setElement(e));
        });

        // ---------- S2C: screenshake ----------
        ClientPlayNetworking.registerGlobalReceiver(WizardPower.S2C_SCREEN_SHAKE, (client, handler, buf, response) -> {
            int ticks = buf.readInt();
            float str = buf.readFloat();
            client.execute(() -> WizardScreenShakeClient.trigger(ticks, str));
        });

        // ---------- Client tick ----------


        // HUD
        WizardManaHudClient.register();

        // World renders
        WizardScreenShakeClient.init();
        // circle renderer must be inited once
        WizardSummonCircleClient.init();

// targeting ticks
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            WizardBigCastTargetClient.tick(mc);
            WizardComboTargetClient.tick(mc);
            WizardChargeSoundClient.tick(mc);
        });



    }
}
