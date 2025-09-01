package net.seep.odd;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
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
import net.seep.odd.abilities.client.*;
import net.seep.odd.abilities.net.*;
import net.seep.odd.abilities.tamer.client.EmeraldShurikenRenderer;
import net.seep.odd.abilities.tamer.client.TamerHudOverlay;
import net.seep.odd.abilities.tamer.client.TameBallRenderer;
import net.seep.odd.abilities.tamer.client.VillagerEvo1Renderer;
import net.seep.odd.block.ModBlocks;
import net.seep.odd.block.grandanvil.ModScreens;
import net.seep.odd.block.grandanvil.client.GrandAnvilScreen;
import net.seep.odd.entity.ModEntities;
import net.seep.odd.entity.creepy.client.CreepyRenderer;
import net.seep.odd.entity.misty.client.MistyBubbleRenderer;

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

        // Misty (client)
        net.seep.odd.abilities.client.MistyClientController.register();
        MistyNet.initClient();

        // ---- Tamer (client) ----
        TamerNet.initClient();
        TamerHudOverlay.register();

        // Entity renderers
        EntityRendererRegistry.register(ModEntities.CREEPY, CreepyRenderer::new);
        EntityRendererRegistry.register(ModEntities.MISTY_BUBBLE, MistyBubbleRenderer::new);
        EntityRendererRegistry.register(ModEntities.EMERALD_SHURIKEN, EmeraldShurikenRenderer::new);
        EntityRendererRegistry.register(ModEntities.VILLAGER_EVO, VillagerEvo1Renderer::new);
        EntityRendererRegistry.register(ModEntities.TAME_BALL, TameBallRenderer::new);

        Oddities.LOGGER.info("OdditiesClient initialized (renderers, HUD, client packets).");
    }
}
