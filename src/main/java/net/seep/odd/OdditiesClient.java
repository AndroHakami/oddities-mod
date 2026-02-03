package net.seep.odd;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.MutableText;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;

import net.seep.odd.abilities.astral.UmbraAirSwimBoostNetClient;
import net.seep.odd.abilities.buddymorph.client.BuddymorphClient;
import net.seep.odd.abilities.client.*;
import net.seep.odd.abilities.client.hud.AstralHudOverlay;
import net.seep.odd.abilities.climber.client.ClimberClient;
import net.seep.odd.abilities.climber.client.render.ClimberPullTetherRenderer;
import net.seep.odd.abilities.climber.client.render.ClimberRopeAnchorRenderer;
import net.seep.odd.abilities.climber.client.render.ClimberRopeShotRenderer;
import net.seep.odd.abilities.climber.net.ClimberClimbNetworking;
import net.seep.odd.abilities.conquer.client.ConquerCorruptionClient;
import net.seep.odd.abilities.conquer.client.render.CorruptedIronGolemRenderer;
import net.seep.odd.abilities.conquer.client.render.CorruptedVillagerRenderer;
import net.seep.odd.abilities.conquer.client.render.DarkHorseEntityRenderer;
import net.seep.odd.abilities.conquer.entity.DarkHorseEntity;
import net.seep.odd.abilities.cosmic.CosmicNet;
import net.seep.odd.abilities.cosmic.client.CosmicCpmBridge;
import net.seep.odd.abilities.cosmic.entity.HomingCosmicSwordEntity;
import net.seep.odd.abilities.cosmic.entity.HomingCosmicSwordRenderer;
import net.seep.odd.abilities.fairy.client.FairyKeysClient;
import net.seep.odd.abilities.fairy.client.FlowerMenuClient;
import net.seep.odd.abilities.fallingsnow.FallingSnowClient;
import net.seep.odd.abilities.fallingsnow.FallingSnowNet;
import net.seep.odd.abilities.fallingsnow.FallingSnowPowerAccessor;
import net.seep.odd.abilities.ghostlings.GhostPackets;
import net.seep.odd.abilities.ghostlings.registry.GhostRegistries;
import net.seep.odd.abilities.ghostlings.registry.GhostScreens;
import net.seep.odd.abilities.ghostlings.screen.client.GhostManageScreen;
import net.seep.odd.abilities.ghostlings.screen.inventory.GhostCargoScreen;
import net.seep.odd.abilities.ghostlings.screen.inventory.GhostCargoScreenHandler;
import net.seep.odd.abilities.icewitch.IceWitchInit;
import net.seep.odd.abilities.icewitch.IceWitchPackets;
import net.seep.odd.abilities.icewitch.client.IceProjectileRenderer;
import net.seep.odd.abilities.icewitch.client.IceSpellAreaRenderer;
import net.seep.odd.abilities.icewitch.client.IceWitchHud;
import net.seep.odd.abilities.init.ArtificerCondenserRegistry;
import net.seep.odd.abilities.init.ArtificerMixerRegistry;
import net.seep.odd.abilities.looker.LookerClient;
import net.seep.odd.abilities.lunar.item.LunarDrillItem;
import net.seep.odd.abilities.lunar.net.LunarPackets;
import net.seep.odd.abilities.necromancer.NecromancerNet;
import net.seep.odd.abilities.necromancer.client.NecromancerClient;
import net.seep.odd.abilities.necromancer.client.NecromancerSummonCircleClient;
import net.seep.odd.abilities.necromancer.client.render.NecroBoltRenderer;
import net.seep.odd.abilities.net.*;
import net.seep.odd.abilities.overdrive.client.OverdriveCpmBridge;
import net.seep.odd.abilities.power.*;

import net.seep.odd.abilities.rider.RiderClientInput;
import net.seep.odd.abilities.rider.RiderNet;
import net.seep.odd.abilities.rise.client.render.RisenZombieRenderer;
import net.seep.odd.abilities.spectral.SpectralClientState;
import net.seep.odd.abilities.spectral.SpectralNet;
import net.seep.odd.abilities.spectral.SpectralPhaseHooks;
import net.seep.odd.abilities.spectral.SpectralRenderState;
import net.seep.odd.abilities.splash.client.SplashPowerClient;
import net.seep.odd.abilities.spotted.SpottedNet;
import net.seep.odd.abilities.supercharge.SuperChargeNet;
import net.seep.odd.abilities.supercharge.SuperHud;
import net.seep.odd.abilities.tamer.client.EmeraldShurikenRenderer;
import net.seep.odd.abilities.tamer.client.TamerHudOverlay;
import net.seep.odd.abilities.tamer.client.TameBallRenderer;
import net.seep.odd.abilities.tamer.client.VillagerEvo1Renderer;
import net.seep.odd.abilities.vampire.client.render.BloodCrystalProjectileRenderer;
import net.seep.odd.abilities.voids.client.VoidCpmBridge;

import net.seep.odd.abilities.artificer.client.ArtificerHud;
import net.seep.odd.abilities.artificer.fluid.client.ArtificerFluidsClient;
import net.seep.odd.abilities.artificer.mixer.MixerNet;
import net.seep.odd.abilities.artificer.mixer.client.PotionMixerHud;
import net.seep.odd.abilities.artificer.mixer.client.PotionMixerScreen;

import net.seep.odd.abilities.zerosuit.ZeroSuitNet;
import net.seep.odd.abilities.zerosuit.client.AnnihilationShaderClient;
import net.seep.odd.abilities.zerosuit.client.ZeroSuitCpmBridge;
import net.seep.odd.block.ModBlocks;
import net.seep.odd.block.falseflower.FalseFlowerTracker;
import net.seep.odd.block.falseflower.client.FalseFlowerAuraClient;
import net.seep.odd.block.falseflower.client.FalseFlowerRenderer;
import net.seep.odd.block.grandanvil.ModScreens;
import net.seep.odd.block.grandanvil.client.GrandAnvilScreen;


import net.seep.odd.client.audio.RiderRadioClient;
import net.seep.odd.client.render.SuperThrownItemEntityRenderer;
import net.seep.odd.entity.ModEntities;
import net.seep.odd.entity.car.RiderCarRenderer;
import net.seep.odd.entity.creepy.client.CreepyRenderer;
import net.seep.odd.entity.cultist.ShyGuyRenderer;
import net.seep.odd.entity.cultist.SightseerRenderer;
import net.seep.odd.entity.falsefrog.client.FalseFrogRenderer;
import net.seep.odd.entity.firefly.client.FireflyRenderer;
import net.seep.odd.entity.misty.client.MistyBubbleRenderer;
import net.seep.odd.entity.necromancer.client.CorpseRenderer;
import net.seep.odd.entity.outerman.OuterManRenderer;
import net.seep.odd.entity.seal.client.SealRenderer;
import net.seep.odd.entity.spotted.PhantomBuddyRenderer;
import net.seep.odd.entity.supercharge.SuperEntities;
import net.seep.odd.entity.ufo.UfoSaucerRenderer;

import net.seep.odd.entity.zerosuit.ZeroBeamRenderer;
import net.seep.odd.entity.zerosuit.ZeroSuitMissileEntity;
import net.seep.odd.entity.zerosuit.client.AnnihilationFx;
import net.seep.odd.entity.zerosuit.client.ZeroSuitMissileRenderer;
import net.seep.odd.particles.OddParticles;
import net.seep.odd.particles.client.OddParticlesClient;
import net.seep.odd.particles.client.SpottedStepsParticle;
import net.seep.odd.particles.client.TelekinesisParticle;
import net.seep.odd.sky.CelestialEventClient;
import net.seep.odd.sky.CelestialEventS2C;
import net.seep.odd.abilities.ghostlings.client.GhostlingRenderer;

import net.seep.odd.sky.client.RottenRootsClient;


import static net.seep.odd.abilities.astral.AstralInventory.HUD_START_ID;
import static net.seep.odd.abilities.astral.AstralInventory.HUD_STOP_ID;

public final class OdditiesClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Power sync + cooldowns (client)
        ClientPowerNetworking.registerReceiver(ClientPowerHolder::set, PowerNetworking.S2C_SYNC_POWER);
        ClientCooldowns.registerTicker();
        ClientPowerNetworking.registerCooldownReceiver();
        ClientPowerNetworking.registerChargesReceiver();
        PowerNetworking.initClient();
        OddParticlesClient.register();
        // Dabloon Client //
        net.seep.odd.shop.client.ShopClientNetworking.registerS2C();
        net.minecraft.client.gui.screen.ingame.HandledScreens.register(
                net.seep.odd.shop.screen.ModScreenHandlers.DABLOONS_MACHINE,
                net.seep.odd.shop.client.DabloonsMachineScreen::new
        );


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
        UmbraAirSwimBoostNetClient.initClient();
        PossessionClientController.register();
        AstralClientController.register();

        // Camera swap for possession
        ClientPlayNetworking.registerGlobalReceiver(new Identifier("odd", "possess_start"),
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
        ClientPlayNetworking.registerGlobalReceiver(new Identifier("odd", "possess_stop"),
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
                net.seep.odd.abilities.client.hud.AstralClientState.start(
                        GlobalPos.create(key, pos), maxTicks, MinecraftClient.getInstance()
                );
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

        // --- Artificer (client) ---
        ArtificerHud.register();
 // if you still keep this path
        ArtificerCondenserRegistry.registerClient();
        net.seep.odd.abilities.artificer.fluid.client.ArtificerFluidsClient.registerClient();
        ArtificerMixerRegistry.Client.register();
        // Item tint (overlay layer index 1)

        // HUD overlay for mixer
        HudRenderCallback.EVENT.register(new PotionMixerHud());
        // Brew bottle renderer
        EntityRendererRegistry.register(
                net.seep.odd.entity.ModEntities.BREW_BOTTLE,
                ctx -> new net.minecraft.client.render.entity.FlyingItemEntityRenderer<>(ctx)
        );

        // Spectral Phase (client)
        SpectralRenderState.installClientTickKeepAlive();
        SpectralNet.registerClient();
        net.seep.odd.abilities.power.SpectralPhasePower.Client.init();

        // Rider (Client)
        RiderNet.Client.init();
        RiderClientInput.init();
        RiderRadioClient.init();

        // Cosmic Sword (Client)
        CosmicPower.Client.init();
        net.seep.odd.abilities.cosmic.CosmicNet.Client.register();
        CosmicCpmBridge.init();

        // Ghostling (Client)
        net.seep.odd.abilities.ghostlings.registry.GhostScreens.register();

        EntityRendererRegistry.register(ModEntities.GHOSTLING, GhostlingRenderer::new);
        GhostPackets.Client.register();
        HandledScreens.register(GhostScreens.GHOST_MANAGE_HANDLER, GhostManageScreen::new);
        HandledScreens.register(GhostScreens.COURIER_PAY_HANDLER, net.seep.odd.abilities.ghostlings.screen.client.courier.CourierPayScreen::new);
        net.fabricmc.fabric.api.client.screenhandler.v1.ScreenRegistry.register(
                GhostCargoScreenHandler.TYPE, GhostCargoScreen::new);

        // Ice Witch (Client)

        IceWitchPackets.registerClient();

        // Spotted Phantom
        SpottedPhantomPower.Client.init();
        SpottedNet.initClient();
        ParticleFactoryRegistry.getInstance()
                .register(OddParticles.SPOTTED_STEPS, SpottedStepsParticle.Factory::new);
        net.minecraft.client.gui.screen.ingame.HandledScreens.register(
                net.seep.odd.abilities.spotted.SpottedScreens.PHANTOM_BUDDY,
                net.seep.odd.abilities.spotted.PhantomBuddyScreen::new
        );


        // Zero Gravity
        net.seep.odd.abilities.zerosuit.ZeroSuitNet.initClient();
        net.seep.odd.abilities.power.ZeroSuitPower.ClientHud.init();
        ZeroSuitCpmBridge.init();
        AnnihilationFx.init();
        AnnihilationShaderClient.init();




        // Looker (Client)
        LookerClient.init();


        // Supercharge (Client)
        SuperHud.init();
        SuperChargeNet.initClient();

        // renderer for thrown items


        // model predicate: lets JSON switch to an overlayed model when supercharged
        ModelPredicateProviderRegistry.register(
                new Identifier(Oddities.MOD_ID, "supercharged"),
                (stack, world, entity, seed) -> SuperChargePower.isSupercharged(stack) ? 1.0F : 0.0F
        );
        ItemTooltipCallback.EVENT.register((stack, ctx, lines) -> {
            if (!SuperChargePower.isSupercharged(stack)) return;
            if (!lines.isEmpty()) {
                MutableText title = lines.get(0).copy().styled(s -> s.withColor(0xFFAA00)); // bright orange
                lines.set(0, title);
            }
        });
        net.seep.odd.client.SuperchargeClientFX.init();

        EntityRendererRegistry.register(SuperEntities.THROWN_ITEM, SuperThrownItemEntityRenderer::new);

        // Gamble (client)
        net.seep.odd.abilities.gamble.item.GambleRevolverItem.initClientHooks();


        // Buddymorph (client)
        BuddymorphClient.init();
        BuddymorphPower.Client.init();

        // Falling Snow (client)
        FallingSnowPower.Client.init();
        FallingSnowClient.init();
        EntityRendererRegistry.register(ModEntities.HEALING_SNOWBALL, ctx -> new FlyingItemEntityRenderer<>(ctx));
        EntityRendererRegistry.register(ModEntities.BIG_SNOWBALL, ctx -> new FlyingItemEntityRenderer<>(ctx));
        EntityRendererRegistry.register(ModEntities.ORBITING_SNOWBALL, ctx -> new FlyingItemEntityRenderer<>(ctx));


        // Dimensions and Sky (World)


        RottenRootsClient.init();



        // Fairy (client)
        BlockEntityRendererFactories.register(ModBlocks.FALSE_FLOWER_BE, ctx -> new FalseFlowerRenderer());
        FairyKeysClient.registerClient();
        FalseFlowerTracker.registerClient();
        FlowerMenuClient.init();
        FalseFlowerAuraClient.init();
        net.seep.odd.abilities.fairy.client.FairyManaHudClient.init();




        // Lunar (client)
        LunarPackets.registerClientReceivers();
        net.seep.odd.abilities.lunar.client.MoonAnchorClient.init();
        EntityRendererRegistry.register(ModEntities.LUNAR_MARK, ctx -> new FlyingItemEntityRenderer<>(ctx));
        LunarPower.Hud.init();
        LunarDrillItem.initClientHooks();

        // Fire Sword (Client)
        // Client init ONLY
        EntityRendererRegistry.register(ModEntities.FIRE_SWORD_PROJECTILE,
                net.seep.odd.abilities.firesword.client.FireSwordProjectileRenderer::new);

        // Glitch Power (Client)
        GlitchPower.Client.init();
        ParticleFactoryRegistry.getInstance()
                .register(OddParticles.TELEKINESIS, TelekinesisParticle.Factory::new);
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.GLITCH_BLOCK, RenderLayer.getTranslucent());

        // Accelerate Power (Client)
        AcceleratePower.Client.init();

        // Conquer (Client)


        // Splash (Client)
        SplashPowerClient.init();

        // Cultist (Client)
        net.seep.odd.entity.cultist.client.ShyGuyClientSounds.init();

        // Climber (Client)
        ClimberClient.init();
        ClimberClimbNetworking.registerClient();

        // Owl (client)
        net.seep.odd.abilities.owl.net.OwlNetworking.registerClient();
        net.seep.odd.abilities.owl.client.render.OwlWingsFeatureRegistration.register();
        net.seep.odd.abilities.owl.client.OwlSonarFx.init();

        // Rise (Client)
        net.seep.odd.abilities.rise.client.RiseClientNetworking.registerClient();
        net.seep.odd.abilities.rise.client.RiseSoulParticlesClient.init();

        // Necromancer (Client)
        NecromancerSummonCircleClient.init();
        NecromancerClient.init();
        NecromancerNet.initClient();

        // Vampire (Client)
        net.seep.odd.abilities.vampire.client.VampireClientFlag.init();
        // in your client init (whatever class you use)
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.BLOOD_CRYSTAL, RenderLayer.getCutout());

        net.seep.odd.abilities.vampire.client.VampireClientNet.init();

        // Druid (Client)
        DruidPower.Client.init();

        // Chef (Client)
        net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry.register(
                net.seep.odd.registry.ModBlockEntities.SUPER_COOKER,
                ctx -> new net.seep.odd.block.supercooker.client.SuperCookerRenderer()
        );






































        // CPM bridges (client)
        net.seep.odd.abilities.anim.CpmBridge CPM;
        OverdriveCpmBridge.init();
        VoidCpmBridge.init();
        {
            var impl = net.seep.odd.abilities.anim.CpmBridgeCpm.tryCreate();
            CPM = (impl != null) ? impl : new net.seep.odd.abilities.anim.CpmBridgeNoop();
        }
        net.seep.odd.abilities.anim.CpmHolder.install(CPM);
        net.seep.odd.abilities.anim.CpmNet.initClient();
        net.seep.odd.integrations.cpm.CpmBridge.init();
        net.seep.odd.abilities.overdrive.client.OverdriveClientState.register();

        // Celestial events (client-only)
        CelestialEventS2C.registerClientReceivers();
        ClientTickEvents.END_CLIENT_TICK.register(client -> CelestialEventClient.clientTick());

        // Entity renderers
        EntityRendererRegistry.register(ModEntities.CREEPY, CreepyRenderer::new);
        EntityRendererRegistry.register(ModEntities.MISTY_BUBBLE, MistyBubbleRenderer::new);
        EntityRendererRegistry.register(ModEntities.EMERALD_SHURIKEN, EmeraldShurikenRenderer::new);
        EntityRendererRegistry.register(ModEntities.VILLAGER_EVO, VillagerEvo1Renderer::new);
        EntityRendererRegistry.register(ModEntities.UFO_SAUCER, UfoSaucerRenderer::new);
        EntityRendererRegistry.register(ModEntities.OUTERMAN, OuterManRenderer::new);
        EntityRendererRegistry.register(ModEntities.TAME_BALL, TameBallRenderer::new);
        EntityRendererRegistry.register(ModEntities.RIDER_CAR, RiderCarRenderer::new);
        EntityRendererRegistry.register(ModEntities.HOMING_COSMIC_SWORD, HomingCosmicSwordRenderer::new);
        EntityRendererRegistry.register(net.seep.odd.abilities.voids.VoidRegistry.VOID_PORTAL, net.seep.odd.abilities.voids.client.VoidPortalRenderer::new);
        EntityRendererRegistry.register(ModEntities.GHOSTLING, GhostlingRenderer::new);
        EntityRendererRegistry.register(ModEntities.ICE_SPELL_AREA, IceSpellAreaRenderer::new);
        EntityRendererRegistry.register(ModEntities.ICE_PROJECTILE, IceProjectileRenderer::new);
        EntityRendererRegistry.register(ModEntities.PHANTOM_BUDDY, PhantomBuddyRenderer::new);
        EntityRendererRegistry.register(ModEntities.ZERO_BEAM, ZeroBeamRenderer::new);
        EntityRendererRegistry.register(ModEntities.FALSE_FROG, FalseFrogRenderer::new);
        EntityRendererRegistry.register(ModEntities.FIREFLY, FireflyRenderer::new);
        EntityRendererRegistry.register(ModEntities.DARK_HORSE, DarkHorseEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.CORRUPTED_VILLAGER, CorruptedVillagerRenderer::new);
        EntityRendererRegistry.register(ModEntities.CORRUPTED_IRON_GOLEM, CorruptedIronGolemRenderer::new);
        EntityRendererRegistry.register(ModEntities.SEAL, SealRenderer::new);
        EntityRendererRegistry.register(ModEntities.ZERO_SUIT_MISSILE, ZeroSuitMissileRenderer::new);
        EntityRendererRegistry.register(ModEntities.SIGHTSEER, SightseerRenderer::new);
        EntityRendererRegistry.register(ModEntities.SHY_GUY, ShyGuyRenderer::new);
        EntityRendererRegistry.register(ModEntities.WEEPING_ANGEL, net.seep.odd.entity.cultist.client.WeepingAngelRenderer::new);
        EntityRendererRegistry.register(ModEntities.CENTIPEDE, net.seep.odd.entity.cultist.CentipedeRenderer::new);
        EntityRendererRegistry.register(ModEntities.CLIMBER_ROPE_ANCHOR, ClimberRopeAnchorRenderer::new);
        EntityRendererRegistry.register(ModEntities.CLIMBER_PULL_TETHER, ClimberPullTetherRenderer::new);
        EntityRendererRegistry.register(ModEntities.CLIMBER_ROPE_SHOT, ClimberRopeShotRenderer::new);
        EntityRendererRegistry.register(ModEntities.RISEN_ZOMBIE, RisenZombieRenderer::new);
        EntityRendererRegistry.register(ModEntities.ZOMBIE_CORPSE,
                ctx -> new net.seep.odd.entity.necromancer.client.CorpseRenderer<>(ctx,
                        net.seep.odd.entity.necromancer.client.CorpseRenderer.ZOMBIE_TEX));

        EntityRendererRegistry.register(ModEntities.SKELETON_CORPSE,
                ctx -> new net.seep.odd.entity.necromancer.client.CorpseRenderer<>(ctx,
                        net.seep.odd.entity.necromancer.client.CorpseRenderer.SKELETON_TEX));
        EntityRendererRegistry.register(ModEntities.NECRO_BOLT, NecroBoltRenderer::new);
        EntityRendererRegistry.register(
                ModEntities.BLOOD_CRYSTAL_PROJECTILE,
                BloodCrystalProjectileRenderer::new
        );














        Oddities.LOGGER.info("OdditiesClient initialized (renderers, HUD, client packets).");
    }
}
