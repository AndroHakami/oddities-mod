package net.seep.odd;

import com.terraformersmc.terraform.boat.api.client.TerraformBoatClientHelper;
import dev.architectury.registry.client.rendering.BlockEntityRendererRegistry;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.*;
import net.fabricmc.fabric.api.object.builder.v1.block.type.WoodTypeBuilder;
import net.minecraft.block.WoodType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.client.render.block.entity.HangingSignBlockEntityRenderer;
import net.minecraft.client.render.block.entity.SignBlockEntityRenderer;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.MutableText;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;

import net.seep.odd.abilities.accelerate.client.AccelerateFx;
import net.seep.odd.abilities.accelerate.client.AccelerateWorldBurstFx;
import net.seep.odd.abilities.artificer.mixer.IceStatueNet;
import net.seep.odd.abilities.artificer.mixer.brew.RadiantBrambleEffect;
import net.seep.odd.abilities.artificer.mixer.brew.client.AmplifiedJudgementFx;
import net.seep.odd.abilities.artificer.mixer.brew.client.BlackFlameFx;
import net.seep.odd.abilities.artificer.mixer.brew.client.DismantleFx;
import net.seep.odd.abilities.artificer.mixer.brew.client.SnowgraveAuroraFx;
import net.seep.odd.abilities.astral.UmbraAirSwimBoostNetClient;
import net.seep.odd.abilities.buddymorph.client.BuddymorphClient;
import net.seep.odd.abilities.chef.client.ChefClient;
import net.seep.odd.abilities.chef.net.ChefNet;
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
import net.seep.odd.abilities.core.CoreNet;
import net.seep.odd.abilities.core.client.CoreCpmBridge;
import net.seep.odd.abilities.cosmic.CosmicNet;
import net.seep.odd.abilities.cosmic.client.CosmicCpmBridge;
import net.seep.odd.abilities.cosmic.entity.HomingCosmicSwordEntity;
import net.seep.odd.abilities.cosmic.entity.HomingCosmicSwordRenderer;
import net.seep.odd.abilities.darkknight.client.DarkKnightShieldHudClient;
import net.seep.odd.abilities.fairy.client.FairyBeamClient;
import net.seep.odd.abilities.fairy.client.FairyKeysClient;
import net.seep.odd.abilities.fairy.client.FlowerMenuClient;
import net.seep.odd.abilities.fallingsnow.FallingSnowClient;
import net.seep.odd.abilities.fallingsnow.FallingSnowNet;
import net.seep.odd.abilities.fallingsnow.FallingSnowPowerAccessor;
import net.seep.odd.abilities.firesword.FireSwordNet;
import net.seep.odd.abilities.firesword.client.FireSwordCpmBridge;
import net.seep.odd.abilities.firesword.client.FireSwordFx;
import net.seep.odd.abilities.gamble.item.GambleRevolverItem;
import net.seep.odd.abilities.gamble.item.client.GambleRevolverClient;
import net.seep.odd.abilities.ghostlings.GhostPackets;
import net.seep.odd.abilities.ghostlings.registry.GhostRegistries;
import net.seep.odd.abilities.ghostlings.registry.GhostScreens;
import net.seep.odd.abilities.ghostlings.screen.client.GhostManageScreen;
import net.seep.odd.abilities.ghostlings.screen.inventory.GhostCargoScreen;
import net.seep.odd.abilities.ghostlings.screen.inventory.GhostCargoScreenHandler;
import net.seep.odd.abilities.icewitch.IceWitchInit;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.TexturedRenderLayers;

import net.seep.odd.abilities.shift.ShiftFxNet;
import net.seep.odd.abilities.shift.client.ShiftScreenFx;
import net.seep.odd.abilities.shift.entity.DecoyRenderer;
import net.seep.odd.abilities.sun.SunFxNet;
import net.seep.odd.abilities.sun.SunNet;
import net.seep.odd.abilities.sun.client.SunCpmBridge;
import net.seep.odd.abilities.sun.client.SunEmpoweredFx;
import net.seep.odd.abilities.sun.client.SunTransformRayFx;
import net.seep.odd.block.ModBlocks;
import net.seep.odd.block.client.DabloonStoreRenderer;
import net.seep.odd.block.client.DabloonsMachineRenderer;
import net.seep.odd.block.false_memory.client.FalseMemoryBlockRenderer;
import net.seep.odd.block.rps_machine.client.RpsMachineBlockRenderer;
import net.seep.odd.block.rps_machine.screen.RpsMachineScreen;
import net.seep.odd.block.rps_machine.screen.RpsMachineScreenHandlers;
import net.seep.odd.client.device.guild.GuildClientCache;
import net.seep.odd.client.device.info.InfoClientCache;
import net.seep.odd.client.device.social.SocialClientCache;
import net.seep.odd.client.fx.DabloonStoreSatinFx;
import net.seep.odd.client.fx.DabloonsMachineSatinFx;
import net.seep.odd.device.store.client.StoreClientInit;
import net.seep.odd.entity.ModBoats;
import net.seep.odd.entity.ModEntities;


import net.seep.odd.abilities.icewitch.IceWitchPackets;
import net.seep.odd.abilities.icewitch.client.IceProjectileRenderer;
import net.seep.odd.abilities.icewitch.client.IceSpellAreaRenderer;
import net.seep.odd.abilities.icewitch.client.IceWitchHud;
import net.seep.odd.abilities.icewitch.client.IceWitchSoarFx;
import net.seep.odd.abilities.init.ArtificerCondenserRegistry;
import net.seep.odd.abilities.init.ArtificerMixerClient;
import net.seep.odd.abilities.init.ArtificerMixerRegistry;
import net.seep.odd.abilities.looker.LookerClient;
import net.seep.odd.abilities.looker.client.LookerInvisFx;
import net.seep.odd.abilities.lunar.item.LunarDrillItem;
import net.seep.odd.abilities.lunar.net.LunarPackets;
import net.seep.odd.abilities.misty.client.MistyHoverFx;
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
import net.seep.odd.abilities.sniper.item.SniperItem;
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
import net.seep.odd.abilities.umbra.client.UmbraEntitiesClient;
import net.seep.odd.abilities.vampire.client.render.BloodCrystalProjectileRenderer;
import net.seep.odd.abilities.voids.client.VoidCpmBridge;

import net.seep.odd.abilities.artificer.client.ArtificerHud;
import net.seep.odd.abilities.artificer.fluid.client.ArtificerFluidsClient;
import net.seep.odd.abilities.artificer.mixer.MixerNet;
import net.seep.odd.abilities.artificer.mixer.client.PotionMixerHud;
import net.seep.odd.abilities.artificer.mixer.client.PotionMixerScreen;


import net.seep.odd.abilities.wizard.client.WizardClientInit;
import net.seep.odd.abilities.wizard.client.WizardNoRenderRenderer;
import net.seep.odd.abilities.wizard.entity.client.CapybaraFamiliarRenderer;
import net.seep.odd.abilities.zerosuit.ZeroSuitNet;

import net.seep.odd.abilities.zerosuit.client.ZeroSuitCpmBridge;
import net.seep.odd.block.ModBlocks;
import net.seep.odd.block.combiner.client.CombinerRenderer;
import net.seep.odd.block.combiner.client.CombinerScreen;
import net.seep.odd.block.combiner.enchant.HostSwapNet;
import net.seep.odd.block.combiner.enchant.client.GazeOfTheEndClient;

import net.seep.odd.block.combiner.net.CombinerNet;
import net.seep.odd.block.falseflower.FalseFlowerTracker;
import net.seep.odd.block.falseflower.client.FalseFlowerAuraClient;
import net.seep.odd.block.falseflower.client.FalseFlowerRenderer;
import net.seep.odd.block.gate.client.DimensionalGateDarkenFx;
import net.seep.odd.block.gate.client.DimensionalGateProximityDistortFx;
import net.seep.odd.block.gate.client.DimensionalGateRenderer;
import net.seep.odd.block.grandanvil.ModScreens;
import net.seep.odd.block.grandanvil.client.GrandAnvilScreen;


import net.seep.odd.client.audio.RiderRadioClient;
import net.seep.odd.client.render.SuperThrownItemEntityRenderer;
import net.seep.odd.entity.ModEntities;
import net.seep.odd.entity.booklet.client.BookletRenderer;
import net.seep.odd.entity.bosswitch.client.*;
import net.seep.odd.entity.car.RiderCarRenderer;
import net.seep.odd.entity.client.IceStatueEntityRenderer;
import net.seep.odd.entity.creepy.client.CreepyRenderer;
import net.seep.odd.entity.cultist.ShyGuyRenderer;
import net.seep.odd.entity.cultist.SightseerRenderer;
import net.seep.odd.entity.darkknight.client.DarkShieldRenderer;
import net.seep.odd.entity.eggasaur.client.EggasaurRenderer;
import net.seep.odd.entity.falsefrog.client.FalseFrogRenderer;
import net.seep.odd.entity.firefly.client.FireflyRenderer;
import net.seep.odd.entity.flyingwitch.client.FlyingWitchRenderer;
import net.seep.odd.entity.flyingwitch.client.HexProjectileRenderer;
import net.seep.odd.entity.granny.GrannyRenderer;
import net.seep.odd.entity.him.HimRenderer;
import net.seep.odd.entity.librarian.LibrarianRenderer;
import net.seep.odd.entity.misty.client.MistyBubbleRenderer;
import net.seep.odd.entity.necromancer.client.CorpseRenderer;
import net.seep.odd.entity.outerblaster.client.BlasterProjectileRenderer;
import net.seep.odd.entity.outerman.OuterManGunnerRenderer;
import net.seep.odd.entity.outerman.OuterManRenderer;
import net.seep.odd.entity.projectile.client.OceanChakramRenderer;
import net.seep.odd.entity.race_rascal.RaceRascalRenderer;
import net.seep.odd.entity.rake.RakeRenderer;
import net.seep.odd.entity.rascal.RascalRenderer;
import net.seep.odd.entity.robo_rascal.RoboRascalRenderer;
import net.seep.odd.entity.rotten_roots.ElderShroomRenderer;
import net.seep.odd.entity.rotten_roots.ShroomRenderer;
import net.seep.odd.entity.rotten_roots.SporeMushroomProjectileRenderer;
import net.seep.odd.entity.scared_rascal.ScaredRascalRenderer;
import net.seep.odd.entity.scared_rascal_fight.ScaredRascalFightRenderer;
import net.seep.odd.entity.seal.client.SealRenderer;
import net.seep.odd.entity.skitter.client.SkitterRenderer;
import net.seep.odd.entity.skull_bird.client.SkullBirdRenderer;
import net.seep.odd.entity.spotted.PhantomBuddyRenderer;
import net.seep.odd.entity.star_ride.StarRideRenderer;
import net.seep.odd.entity.sun.client.PocketSunRenderer;
import net.seep.odd.entity.supercharge.SuperEntities;
import net.seep.odd.entity.ufo.*;

import net.seep.odd.entity.ufo.client.UfoAbductionBeamFx;
import net.seep.odd.entity.ufo.client.UfoClientFx;
import net.seep.odd.entity.whiskers.client.WhiskersRenderer;
import net.seep.odd.entity.windwitch.client.TornadoProjectileRenderer;
import net.seep.odd.entity.windwitch.client.WindWitchRenderer;
import net.seep.odd.entity.zerosuit.ZeroBeamRenderer;
import net.seep.odd.entity.zerosuit.ZeroSuitMissileEntity;
import net.seep.odd.entity.zerosuit.client.AnnihilationFx;
import net.seep.odd.entity.zerosuit.client.ZeroGrenadeRenderer;
import net.seep.odd.entity.zerosuit.client.ZeroSuitMissileRenderer;
import net.seep.odd.event.alien.client.AlienInvasionSkyClient;
import net.seep.odd.event.alien.client.AlienOverworldGradeClient;
import net.seep.odd.expeditions.atheneum.granny.GrannyClientState;
import net.seep.odd.expeditions.atheneum.granny.GrannyNetworking;
import net.seep.odd.expeditions.atheneum.granny.client.GrannyFx;
import net.seep.odd.expeditions.rottenroots.boggy.client.BoggyBoatRenderer;

import net.seep.odd.fluid.ModFluidRendering;
import net.seep.odd.item.ModItems;
import net.seep.odd.item.outerblaster.client.OuterBlasterClient;
import net.seep.odd.particles.OddParticles;
import net.seep.odd.particles.client.OddParticlesClient;
import net.seep.odd.particles.client.SpottedStepsParticle;
import net.seep.odd.particles.client.TelekinesisParticle;
import net.seep.odd.quest.client.ModQuestsClient;
import net.seep.odd.sky.CelestialEventClient;
import net.seep.odd.sky.CelestialEventS2C;
import net.seep.odd.abilities.ghostlings.client.GhostlingRenderer;

import net.seep.odd.sky.client.*;


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
        // DEVICE
        SocialClientCache.initClient();
        net.seep.odd.client.device.notes.NotesClientCache.initClient();
        net.seep.odd.client.device.bank.DabloonBankClientCache.initClient();
        StoreClientInit.initClient();
        OverworldDreamSkyClient.init();
        InfoClientCache.initClient();
        GuildClientCache.initClient();

        // granny
        GrannyNetworking.initClient();
        GrannyFx.init();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            GrannyClientState.clientTick();
            GrannyClientState.initClient();
            net.seep.odd.expeditions.atheneum.granny.client.GrannyFx.clientTick();
        });


        // Keybinds + HUDs (client)
        AbilityKeybinds.register();
        AbilityHudOverlay.register();
        ShadowFormOverlay.register();

        // Forger screens (client)
        HandledScreens.register(ModScreens.GRAND_ANVIL, GrandAnvilScreen::new);
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.GRAND_ANVIL, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.CRAPPY_BLOCK, RenderLayer.getTranslucent());

        CombinerNet.initClient();
        BlockEntityRendererRegistry.register(ModBlocks.COMBINER_BE, CombinerRenderer::new);
        HandledScreens.register(ModScreens.COMBINER, CombinerScreen::new);
        GazeOfTheEndClient.init();
        net.seep.odd.block.combiner.enchant.client.HostSwapKeybind.init();


        // Blockade
        net.seep.odd.abilities.blockade.client.BlockadeFx.init();
        net.seep.odd.abilities.blockade.net.BlockadeNet.initClient();

        // Umbra (client)
        UmbraNet.registerClient();
        UmbraAirSwimBoostNetClient.initClient();
        UmbraEntitiesClient.init();
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
        MistyHoverFx.init();

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
        net.seep.odd.abilities.artificer.mixer.brew.AtomicRefractionEffect.Client.init();
        net.seep.odd.abilities.artificer.mixer.brew.CloudOfEntropyEffect.Client.init();
        RadiantBrambleEffect.Client.init();
        net.seep.odd.abilities.artificer.mixer.brew.GeoThermalReleaseEffect.Client.init();
        net.seep.odd.abilities.artificer.mixer.brew.LifeAuroraEffect.Client.init();
        BlackFlameFx.init();
        DismantleFx.init();
        net.seep.odd.abilities.artificer.mixer.brew.FrostyStepsEffect.Client.init();
        net.seep.odd.abilities.artificer.mixer.brew.client.AutoColdFx.init();

        SnowgraveAuroraFx.init();

        AmplifiedJudgementFx.init();
        EntityRendererRegistry.register(ModEntities.ICE_STATUE, IceStatueEntityRenderer::new);
        IceStatueNet.initClient();






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
        net.seep.odd.abilities.cosmic.client.CosmicChargeFx.init();
        net.seep.odd.abilities.cosmic.client.CosmicDashTrailFx.init();
        net.seep.odd.abilities.cosmic.CosmicFxNet.initClient();
        net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry.register(
                net.seep.odd.block.ModBlocks.COSMIC_KATANA_BLOCK_BE,
                net.seep.odd.block.cosmic_katana.client.CosmicKatanaBlockRenderer::new
        );

        net.seep.odd.block.cosmic_katana.CosmicKatanaBlockNet.initClient();

        // Ghostling (Client)
        EntityRendererRegistry.register(ModEntities.GHOSTLING, GhostlingRenderer::new);
        GhostPackets.Client.register();

        HandledScreens.register(GhostRegistries.GHOST_MANAGE_HANDLER, GhostManageScreen::new);
        HandledScreens.register(GhostRegistries.COURIER_PAY_HANDLER, net.seep.odd.abilities.ghostlings.screen.client.courier.CourierPayScreen::new);

        net.fabricmc.fabric.api.client.screenhandler.v1.ScreenRegistry.register(
                GhostCargoScreenHandler.TYPE,
                GhostCargoScreen::new
        );

        // OVERWORLD SKY..
        OverworldDreamSkyClient.init();

        // Ice Witch (Client)

        IceWitchPackets.registerClient();
        IceWitchSoarFx.init();

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





        // Looker (Client)
        LookerClient.init();
        LookerInvisFx.init();


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
        GambleRevolverClient.init();
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
        FairyBeamClient.init();




        // Lunar (client)
        LunarPackets.registerClientReceivers();
        net.seep.odd.abilities.lunar.client.MoonAnchorClient.init();
        EntityRendererRegistry.register(ModEntities.LUNAR_MARK, ctx -> new FlyingItemEntityRenderer<>(ctx));
        LunarPower.Hud.init();
        LunarDrillItem.initClientHooks();
        net.seep.odd.abilities.lunar.client.LunarDrillFx.init();

        // Fire Sword (Client)
        // Client init ONLY
        EntityRendererRegistry.register(ModEntities.FIRE_SWORD_PROJECTILE,
                net.seep.odd.abilities.firesword.client.FireSwordProjectileRenderer::new);
        FireSwordFx.init();
        FireSwordNet.initClient();


        // Glitch Power (Client)
        GlitchPower.Client.init();
        ParticleFactoryRegistry.getInstance()
                .register(OddParticles.TELEKINESIS, TelekinesisParticle.Factory::new);
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.GLITCH_BLOCK, RenderLayer.getTranslucent());

        // Accelerate Power (Client)
        AcceleratePower.Client.init();
        AccelerateFx.init();
        AccelerateWorldBurstFx.init();


        // Conquer (Client)

        // Outerblaster
        OuterBlasterClient.init();


        // Splash (Client)
        SplashPowerClient.init();

        // Cultist (Client)
        net.seep.odd.entity.cultist.client.ShyGuyClientSounds.init();
        net.seep.odd.abilities.cultist.CultistNet.initClient();

        // Climber (Client)
        ClimberClient.init();
        ClimberClimbNetworking.registerClient();

        // Owl (client)
        net.seep.odd.abilities.owl.net.OwlNetworking.registerClient();
        net.seep.odd.abilities.owl.client.render.OwlWingsFeatureRegistration.register();


        // Rise (Client)
        net.seep.odd.abilities.rise.client.RiseClientNetworking.registerClient();
        net.seep.odd.abilities.rise.client.RiseSoulParticlesClient.init();

        // Necromancer (Client)
        NecromancerSummonCircleClient.init();
        NecromancerClient.init();
        NecromancerNet.initClient();

        // Shift (Client)
        ShiftFxNet.initClient();
        ShiftScreenFx.init();

        EntityRendererRegistry.register(ModEntities.DECOY, DecoyRenderer::new);

        // Vampire (Client)
        net.seep.odd.abilities.vampire.client.VampireClientFlag.init();
        // in your client init (whatever class you use)
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.BLOOD_CRYSTAL, RenderLayer.getCutout());

        net.seep.odd.abilities.vampire.client.VampireClientNet.init();

        // Druid (Client)
        DruidPower.Client.init();

        // Chef (Client)
        ChefClient.init();

        // DIMENSIONAL GATE
        BlockEntityRendererFactories.register(ModBlocks.DIMENSIONAL_GATE_BE, DimensionalGateRenderer::new);
        net.seep.odd.block.gate.client.DimensionalGatePortalFx.init();
        DimensionalGateDarkenFx.init();
        DimensionalGateProximityDistortFx.init();

        // Atheneum
        AtheneumClient.init();
        AtheneumGradeFx.init();
        // rotten roots
        ModelPredicateProviderRegistry.register(
                ModItems.BONE_BOW,
                new Identifier("minecraft", "pull"),
                (stack, world, entity, seed) -> {
                    if (entity == null) return 0.0F;
                    if (entity.getActiveItem() != stack) return 0.0F;

                    int useTicks = stack.getMaxUseTime() - entity.getItemUseTimeLeft();
                    float pull = useTicks / 20.0F;
                    pull = (pull * pull + pull * 2.0F) / 3.0F;
                    return Math.min(pull, 1.0F);
                }
        );

        ModelPredicateProviderRegistry.register(
                ModItems.BONE_BOW,
                new Identifier("minecraft", "pulling"),
                (stack, world, entity, seed) ->
                        (entity != null && entity.isUsingItem() && entity.getActiveItem() == stack) ? 1.0F : 0.0F
        );
        ModelPredicateProviderRegistry.register(
                ModItems.SPORE_BOW,
                new Identifier("minecraft", "pull"),
                (stack, world, entity, seed) -> {
                    if (entity == null) return 0.0F;
                    if (entity.getActiveItem() != stack) return 0.0F;

                    int useTicks = stack.getMaxUseTime() - entity.getItemUseTimeLeft();
                    float pull = useTicks / 20.0F;
                    pull = (pull * pull + pull * 2.0F) / 3.0F;
                    return Math.min(pull, 1.0F);
                }
        );

        ModelPredicateProviderRegistry.register(
                ModItems.SPORE_BOW,
                new Identifier("minecraft", "pulling"),
                (stack, world, entity, seed) ->
                        (entity != null && entity.isUsingItem() && entity.getActiveItem() == stack) ? 1.0F : 0.0F
        );
        TerraformBoatClientHelper.registerModelLayers(ModBoats.BOGGY_BOAT_ID, false);
        TerraformBoatClientHelper.registerModelLayers(ModBoats.BOGGY_BOAT_ID, true);
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.BLUE_MUSHROOM, RenderLayer.getCutout());











        // Sniper (Client)
        SniperItem.initClientHooks();

        // Core (Client)
        CoreNet.Client.register();
        CoreCpmBridge.init();

        // Dark Knight
        DarkKnightShieldHudClient.init();

        // Wizard (Client)
        net.seep.odd.abilities.wizard.client.WizardClientInit.initClient();

        // EVENT (ALIEN INVASION CLIENT)
        net.seep.odd.event.alien.net.AlienInvasionNet.registerClientReceivers();
        net.seep.odd.event.alien.client.fx.AlienOverworldSkyFx.init();
        net.seep.odd.event.alien.client.fx.AlienPillarFx.init();

        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client ->
                net.seep.odd.event.alien.client.AlienInvasionClientState.clientTick()
        );
        net.seep.odd.event.alien.client.sky.AlienOverworldSkyCore.init();
        AlienOverworldGradeClient.init();

        BossWitchMusicClient.init();
        BossGolemCarryCameraClient.init();
        BossGolemShockwaveClient.init();
        ModFluidRendering.register();
        BossWitchHexZoneClient.init();
        BossWitchSnareFx.init();

        // Sun (CLIENT)
        SunNet.Client.register();
        SunFxNet.initClient();
        SunEmpoweredFx.init();
        SunTransformRayFx.init();
        SunPower.Client.init();

        // Dablon store
        DabloonStoreSatinFx.init();

        // Alien UFO
        UfoClientFx.init();

        // Librarian
        ModQuestsClient.init();
        EntityRendererRegistry.register(ModEntities.LIBRARIAN, LibrarianRenderer::new);



        // arcade
        HandledScreens.register(RpsMachineScreenHandlers.RPS_MACHINE, RpsMachineScreen::new);
        BlockEntityRendererRegistry.register(ModBlocks.RPS_MACHINE_BE, ctx -> new RpsMachineBlockRenderer());


        // Dragoness
        net.seep.odd.entity.dragoness.client.DragonessClientBootstrap.init();













































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
        EntityRendererRegistry.register(ModEntities.UFO_SLICER, UfoSlicerRenderer::new);
        EntityRendererRegistry.register(ModEntities.OUTERMAN, OuterManRenderer::new);
        EntityRendererRegistry.register(ModEntities.OUTERMAN_GUNNER, OuterManGunnerRenderer::new);
        EntityRendererRegistry.register(ModEntities.TAME_BALL, TameBallRenderer::new);
        EntityRendererRegistry.register(ModEntities.RIDER_CAR, RiderCarRenderer::new);
        EntityRendererRegistry.register(ModEntities.HOMING_COSMIC_SWORD, HomingCosmicSwordRenderer::new);
        EntityRendererRegistry.register(net.seep.odd.abilities.voids.VoidRegistry.VOID_PORTAL, net.seep.odd.abilities.voids.client.VoidPortalRenderer::new);
        EntityRendererRegistry.register(ModEntities.GHOSTLING, GhostlingRenderer::new);
        EntityRendererRegistry.register(ModEntities.ICE_SPELL_AREA, IceSpellAreaRenderer::new);
        EntityRendererRegistry.register(ModEntities.DARK_SHIELD, DarkShieldRenderer::new);
        EntityRendererRegistry.register(ModEntities.ICE_PROJECTILE, IceProjectileRenderer::new);
        EntityRendererRegistry.register(ModEntities.PHANTOM_BUDDY, PhantomBuddyRenderer::new);
        EntityRendererRegistry.register(ModEntities.ZERO_BEAM, ZeroBeamRenderer::new);
        EntityRendererRegistry.register(ModEntities.FALSE_FROG, FalseFrogRenderer::new);
        EntityRendererRegistry.register(ModEntities.RASCAL, RascalRenderer::new);
        EntityRendererRegistry.register(ModEntities.RAKE, RakeRenderer::new);
        EntityRendererRegistry.register(ModEntities.SCARED_RASCAL, ScaredRascalRenderer::new);
        EntityRendererRegistry.register(ModEntities.UFO_BOMBER, UfoBomberRenderer::new);
        EntityRendererRegistry.register(ModEntities.OUTER_MECH, OuterMechRenderer::new);
        EntityRendererRegistry.register(ModEntities.ALIEN_BOMB, AlienBombRenderer::new);
        EntityRendererRegistry.register(ModEntities.STAR_RIDE, StarRideRenderer::new);
        EntityRendererRegistry.register(ModEntities.ALIEN_MISSILE, AlienMissileRenderer::new);
        EntityRendererRegistry.register(ModEntities.FIREFLY, FireflyRenderer::new);
        EntityRendererRegistry.register(ModEntities.DARK_HORSE, DarkHorseEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.CORRUPTED_VILLAGER, CorruptedVillagerRenderer::new);
        EntityRendererRegistry.register(ModEntities.CORRUPTED_IRON_GOLEM, CorruptedIronGolemRenderer::new);
        EntityRendererRegistry.register(ModEntities.SEAL, SealRenderer::new);
        EntityRendererRegistry.register(ModEntities.ZERO_GRENADE, ZeroGrenadeRenderer::new);
        EntityRendererRegistry.register(ModEntities.ZERO_SUIT_MISSILE, ZeroSuitMissileRenderer::new);
        EntityRendererRegistry.register(ModEntities.SIGHTSEER, SightseerRenderer::new);
        EntityRendererRegistry.register(ModEntities.SHY_GUY, ShyGuyRenderer::new);
        EntityRendererRegistry.register(ModEntities.WEEPING_ANGEL, net.seep.odd.entity.cultist.client.WeepingAngelRenderer::new);
        EntityRendererRegistry.register(ModEntities.CENTIPEDE, net.seep.odd.entity.cultist.CentipedeRenderer::new);
        EntityRendererRegistry.register(ModEntities.CLIMBER_ROPE_ANCHOR, ClimberRopeAnchorRenderer::new);
        EntityRendererRegistry.register(ModEntities.CLIMBER_PULL_TETHER, ClimberPullTetherRenderer::new);
        EntityRendererRegistry.register(ModEntities.CLIMBER_ROPE_SHOT, ClimberRopeShotRenderer::new);
        EntityRendererRegistry.register(ModEntities.RISEN_ZOMBIE, RisenZombieRenderer::new);
        EntityRendererRegistry.register(ModEntities.RACE_RASCAL, RaceRascalRenderer::new);
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
        EntityRendererRegistry.register(ModEntities.SNIPER_GRAPPLE_SHOT,
                net.seep.odd.abilities.sniper.client.render.SniperGrappleShotRenderer::new);

        EntityRendererRegistry.register(ModEntities.SNIPER_GRAPPLE_ANCHOR,
                net.seep.odd.abilities.sniper.client.render.SniperGrappleAnchorRenderer::new);
        EntityRendererRegistry.register(ModEntities.BOOKLET, BookletRenderer::new);
        EntityRendererRegistry.register(ModEntities.SHROOM, ShroomRenderer::new);
        EntityRendererRegistry.register(ModEntities.ELDER_SHROOM, ElderShroomRenderer::new);
        EntityRendererRegistry.register(ModEntities.SPORE_MUSHROOM_PROJECTILE, SporeMushroomProjectileRenderer::new);
        EntityRendererRegistry.register(ModEntities.SKITTER, SkitterRenderer::new);
        EntityRendererRegistry.register(ModEntities.WHISKERS, WhiskersRenderer::new);
        EntityRendererRegistry.register(ModEntities.EGGASAUR, EggasaurRenderer::new);
        EntityRendererRegistry.register(ModEntities.GRANNY, GrannyRenderer::new);
        EntityRendererRegistry.register(ModEntities.FLYING_WITCH, FlyingWitchRenderer::new);
        EntityRendererRegistry.register(ModEntities.HEX_PROJECTILE, HexProjectileRenderer::new);
        EntityRendererRegistry.register(ModEntities.WIND_WITCH, WindWitchRenderer::new);
        EntityRendererRegistry.register(ModEntities.TORNADO_PROJECTILE, TornadoProjectileRenderer::new);
        EntityRendererRegistry.register(ModEntities.BOSS_WITCH, BossWitchRenderer::new);
        EntityRendererRegistry.register(ModEntities.FLAMING_SKULL, FlamingSkullRenderer::new);
        EntityRendererRegistry.register(ModEntities.ROTTEN_SPIKE, RottenSpikeRenderer::new);
        EntityRendererRegistry.register(ModEntities.BOSS_WITCH_SNARE, BossWitchSnareRenderer::new);
        EntityRendererRegistry.register(ModEntities.BOSS_GOLEM, BossGolemRenderer::new);
        EntityRendererRegistry.register(ModEntities.SKULL_BIRD, SkullBirdRenderer::new);
        EntityRendererRegistry.register(ModEntities.HIM, HimRenderer::new);
        EntityRendererRegistry.register(ModEntities.FAT_WITCH, net.seep.odd.entity.fatwitch.client.FatWitchRenderer::new);
        EntityRendererRegistry.register(ModEntities.FAT_WITCH_SIGIL, net.seep.odd.entity.fatwitch.client.FatWitchSigilRenderer::new);
        EntityRendererRegistry.register(ModEntities.POCKET_SUN, PocketSunRenderer::new);
        EntityRendererRegistry.register(ModEntities.BLASTER_PROJECTILE, BlasterProjectileRenderer::new);
        BlockEntityRendererFactories.register(ModBlocks.FALSE_MEMORY_BE, FalseMemoryBlockRenderer::new);
        EntityRendererRegistry.register(ModEntities.OCEAN_CHAKRAM, OceanChakramRenderer::new);
        DabloonsMachineSatinFx.init();

        BlockEntityRendererFactories.register(ModBlocks.DABLOONS_MACHINE_BE, DabloonsMachineRenderer::new);
        BlockEntityRendererFactories.register(ModBlocks.DABLOON_STORE_BE, DabloonStoreRenderer::new);



















        Oddities.LOGGER.info("OdditiesClient initialized (renderers, HUD, client packets).");
    }
}
