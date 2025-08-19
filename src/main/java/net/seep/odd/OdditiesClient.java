package net.seep.odd;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.entity.Entity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;
import net.seep.odd.abilities.client.*;
import net.seep.odd.abilities.client.hud.AstralClientState;
import net.seep.odd.abilities.client.hud.AstralHudOverlay;
import net.seep.odd.abilities.net.PowerNetworking;
import net.seep.odd.abilities.net.UmbraNet;
import net.seep.odd.block.ModBlocks;
import net.seep.odd.block.grandanvil.ModScreens;
import net.seep.odd.block.grandanvil.client.GrandAnvilScreen;
import net.seep.odd.entity.ModEntities;
import net.seep.odd.entity.creepy.client.CreepyRenderer;
import net.seep.odd.item.ghost.client.GhostHandModel;
import net.seep.odd.item.ghost.client.GhostHandRenderer;
import software.bernie.geckolib.GeckoLib;

import static net.seep.odd.abilities.astral.AstralInventory.HUD_START_ID;
import static net.seep.odd.abilities.astral.AstralInventory.HUD_STOP_ID;


public class OdditiesClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // power id sync -> client holder
        ClientPowerNetworking.registerReceiver(ClientPowerHolder::set, PowerNetworking.SYNC);


        // keys (ONCE)
        AbilityKeybinds.register();

        // HUD overlay (uncomment if the class exists in your project)
       AbilityHudOverlay.register();

        // Cooldown Reg
        ClientCooldowns.registerTicker();
        ClientPowerNetworking.registerCooldownReceiver();

        // ANIMATION SHIT BELOW






        // Forger Power Stuff
        HandledScreens.register(ModScreens.GRAND_ANVIL, GrandAnvilScreen::new);
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.GRAND_ANVIL, RenderLayer.getCutout());

        //Forger Color overlay
        GeckoLib.initialize();













        // Crappy Block Effect
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.CRAPPY_BLOCK, RenderLayer.getTranslucent());


        // umbra client bits + possession input sender
        net.seep.odd.abilities.client.ShadowFormOverlay.register();

        UmbraNet.registerClient();


        PossessionClientController.register();

        // umbra astral
        net.seep.odd.abilities.client.AstralClientController.register();

        EntityRendererRegistry.register(ModEntities.CREEPY, CreepyRenderer::new);

        ClientPlayNetworking.registerGlobalReceiver(HUD_START_ID, (client, handler, buf, responseSender) -> {
            Identifier dimId = buf.readIdentifier();
            BlockPos pos     = buf.readBlockPos();
            int maxTicks     = buf.readVarInt();

            client.execute(() -> {
                RegistryKey<World> key = RegistryKey.of(RegistryKeys.WORLD, dimId);
                AstralClientState.start(GlobalPos.create(key, pos), maxTicks, MinecraftClient.getInstance());
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(HUD_STOP_ID, (client, handler, buf, responseSender) ->
                client.execute(AstralClientState::stop)
        );

        AstralHudOverlay.register();;






        // possession camera control
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
                (client, h, buf, rs) -> client.execute(() -> {
                    if (client.player != null) client.setCameraEntity(client.player);
                    PossessionClientController.setPossessing(false);
                }));

        net.seep.odd.Oddities.LOGGER.info("OdditiesClient initialized (keys + HUD/packets ready).");
    }
}
