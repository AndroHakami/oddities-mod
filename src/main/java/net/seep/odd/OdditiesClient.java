package net.seep.odd;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.World;
import net.seep.odd.abilities.artificer.client.ArtificerHud;
import net.seep.odd.abilities.artificer.fluid.ArtificerFluids;
import net.seep.odd.abilities.artificer.fluid.client.ArtificerFluidsClient;
import net.seep.odd.abilities.artificer.item.client.ArtificerVacuumModel;
import net.seep.odd.abilities.artificer.item.client.ArtificerVacuumRenderer;
import net.seep.odd.abilities.artificer.mixer.client.PotionMixerHud;
import net.seep.odd.abilities.client.*;
import net.seep.odd.abilities.client.hud.AstralHudOverlay;
import net.seep.odd.abilities.init.ArtificerCondenserRegistry;
import net.seep.odd.abilities.init.ArtificerMixerRegistry;
import net.seep.odd.abilities.net.*;
import net.seep.odd.abilities.overdrive.client.OverdriveCpmBridge;
import net.seep.odd.abilities.tamer.client.EmeraldShurikenRenderer;
import net.seep.odd.abilities.tamer.client.TamerHudOverlay;
import net.seep.odd.abilities.tamer.client.TameBallRenderer;
import net.seep.odd.abilities.tamer.client.VillagerEvo1Renderer;
import net.seep.odd.abilities.voids.VoidPortalEntity;
import net.seep.odd.abilities.voids.client.VoidCpmBridge;
import net.seep.odd.block.ModBlocks;
import net.seep.odd.block.grandanvil.ModScreens;
import net.seep.odd.block.grandanvil.client.GrandAnvilScreen;
import net.seep.odd.entity.ModEntities;
import net.seep.odd.entity.creepy.client.CreepyRenderer;
import net.seep.odd.entity.misty.client.MistyBubbleRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.seep.odd.block.ModBlocks;
import net.seep.odd.abilities.artificer.mixer.client.PotionMixerScreen;
import net.seep.odd.abilities.artificer.mixer.client.PotionMixerHud;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

import static net.seep.odd.abilities.astral.AstralInventory.HUD_START_ID;
import static net.seep.odd.abilities.astral.AstralInventory.HUD_STOP_ID;

public final class OdditiesClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Power sync + cooldowns (client)
        ClientPowerNetworking.registerReceiver(ClientPowerHolder::set, PowerNetworking.SYNC);
        ClientCooldowns.registerTicker();
        ClientPowerNetworking.registerCooldownReceiver();

        // Keybinds + HUDs (client)
        AbilityKeybinds.register();
        AbilityHudOverlay.register();
        ShadowFormOverlay.register();


        // Forger screens (client)
        HandledScreens.register(ModScreens.GRAND_ANVIL, GrandAnvilScreen::new);
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.GRAND_ANVIL, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.CRAPPY_BLOCK, RenderLayer.getTranslucent());

        // Umbra (client)
        UmbraNet.registerClient();
        PossessionClientController.register();
        AstralClientController.register();

        // Camera swap for possession
        ClientPlayNetworking.registerGlobalReceiver(new Identifier("odd","possess_start"),
                (client, h, buf, rs) -> {
                    int targetId = buf.readVarInt();
                    client.execute(() -> {
                        var w = client.world;
                        if (w != null) {
                            Entity e = w.getEntityById(targetId);
                            if (e != null) client.setCameraEntity(e);
                        }
                        PossessionClientController.setPossessing(true);
                    });
                });
        ClientPlayNetworking.registerGlobalReceiver(new Identifier("odd","possess_stop"),
                (client, h, buf, rs) ->
                        client.execute(() -> {
                            if (client.player != null) client.setCameraEntity(client.player);
                            PossessionClientController.setPossessing(false);
                        })
        );

        // Astral HUD start/stop packets
        ClientPlayNetworking.registerGlobalReceiver(HUD_START_ID, (client, handler, buf, responseSender) -> {
            Identifier dimId = buf.readIdentifier();
            BlockPos pos     = buf.readBlockPos();
            int maxTicks     = buf.readVarInt();
            client.execute(() -> {
                RegistryKey<World> key = RegistryKey.of(RegistryKeys.WORLD, dimId);
                net.seep.odd.abilities.client.hud.AstralClientState.start(GlobalPos.create(key, pos), maxTicks, MinecraftClient.getInstance());
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(HUD_STOP_ID, (client, handler, buf, responseSender) ->
                client.execute(net.seep.odd.abilities.client.hud.AstralClientState::stop)
        );
        AstralHudOverlay.register();

        // Misty (client)
        net.seep.odd.abilities.client.MistyClientController.register();
        MistyNet.initClient();

        // ---- Tamer (client) ----
        TamerNet.initClient();
        net.seep.odd.abilities.tamer.client.CommandWheelHud.init();
        TamerHudOverlay.register();
        net.seep.odd.abilities.tamer.client.SummonWheelHud.init();

        // Overdrive (client)
        net.seep.odd.abilities.overdrive.OverdriveNet.initClient();
        net.seep.odd.abilities.overdrive.OverdriveClientController.register();
        net.seep.odd.abilities.overdrive.OverdriveHudOverlay.register();

        // Void (Client)
        net.seep.odd.abilities.voids.VoidNet.initClient();
        net.seep.odd.abilities.voids.client.VoidClient.init();

        //Artificer (client)
        ArtificerHud.register();
        net.seep.odd.abilities.artificer.condenser.ArtificerCreateInit.registerClient();
        ArtificerCondenserRegistry.registerClient();
        ArtificerFluidsClient.registerClient();
        ArtificerMixerRegistry.registerClient();
        ColorProviderRegistry.ITEM.register((stack, tintIndex) -> {
            if (tintIndex == 1 && stack.hasNbt())
                return stack.getNbt().getInt("odd_brew_color");
            return 0xFFFFFFFF; // base layer untouched
        }, ArtificerMixerRegistry.BREW_DRINKABLE, ArtificerMixerRegistry.BREW_THROWABLE);
        HudRenderCallback.EVENT.register(new PotionMixerHud());
        HandledScreens.register(ModBlocks.POTION_MIXER_HANDLER, PotionMixerScreen::new);










        // Custom Player Model
        net.seep.odd.abilities.anim.CpmBridge CPM;
        OverdriveCpmBridge.init();
        VoidCpmBridge.init();

        {
            var impl = net.seep.odd.abilities.anim.CpmBridgeCpm.tryCreate();
            CPM = (impl != null) ? impl : new net.seep.odd.abilities.anim.CpmBridgeNoop();
        }

// Expose it somewhere convenient (static holder or a getter)
        net.seep.odd.abilities.anim.CpmHolder.install(CPM);
        net.seep.odd.abilities.anim.CpmNet.initClient();
        net.seep.odd.integrations.cpm.CpmBridge.init();
        net.seep.odd.abilities.overdrive.client.OverdriveClientState.register();
        OverdriveCpmBridge.init();





        // Entity renderers
        EntityRendererRegistry.register(ModEntities.CREEPY, CreepyRenderer::new);
        EntityRendererRegistry.register(ModEntities.MISTY_BUBBLE, MistyBubbleRenderer::new);
        EntityRendererRegistry.register(ModEntities.EMERALD_SHURIKEN, EmeraldShurikenRenderer::new);
        EntityRendererRegistry.register(ModEntities.VILLAGER_EVO, VillagerEvo1Renderer::new);
        EntityRendererRegistry.register(ModEntities.TAME_BALL, TameBallRenderer::new);
        EntityRendererRegistry.register(
                net.seep.odd.abilities.voids.VoidRegistry.VOID_PORTAL,
                net.seep.odd.abilities.voids.client.VoidPortalRenderer::new
        );


        Oddities.LOGGER.info("OdditiesClient initialized (renderers, HUD, client packets).");
    }
}
