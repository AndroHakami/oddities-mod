package net.seep.odd;

import ladysnake.satin.api.managed.ShaderEffectManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.registry.FuelRegistry;

import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.ActionResult;

import net.minecraft.util.Identifier;
import net.seep.odd.abilities.AbilityServerTicks;
import net.seep.odd.abilities.PowerAPI;
import net.seep.odd.abilities.PowerCommands;
import net.seep.odd.abilities.artificer.condenser.CondenserNet; // (only used inside registry)
import net.seep.odd.abilities.artificer.fluid.ArtificerFluids;
import net.seep.odd.abilities.artificer.mixer.MixerNet;
import net.seep.odd.abilities.astral.AstralInventory;
import net.seep.odd.abilities.buddymorph.BuddymorphCommands;
import net.seep.odd.abilities.buddymorph.BuddymorphData;
import net.seep.odd.abilities.buddymorph.BuddymorphNet;
import net.seep.odd.abilities.buddymorph.client.BuddymorphClient;
import net.seep.odd.abilities.buddymorph.client.BuddymorphScreen;
import net.seep.odd.abilities.climber.net.ClimberClimbNetworking;
import net.seep.odd.abilities.conquer.entity.DarkHorseEntity;
import net.seep.odd.abilities.cosmic.CosmicNet;
import net.seep.odd.abilities.druid.DruidNet;
import net.seep.odd.abilities.effect.ModEffects;
import net.seep.odd.abilities.fairy.client.ManageFlowersScreen;
import net.seep.odd.abilities.ghostlings.GhostPackets;
import net.seep.odd.abilities.ghostlings.entity.GhostlingEntity;
import net.seep.odd.abilities.ghostlings.screen.inventory.GhostCargoScreenHandler;
import net.seep.odd.abilities.icewitch.IceSpellAreaEntity;
import net.seep.odd.abilities.icewitch.IceWitchInit;
import net.seep.odd.abilities.icewitch.IceWitchPackets;
import net.seep.odd.abilities.icewitch.client.IceSpellAreaRenderer;
import net.seep.odd.abilities.icewitch.client.IceWitchHud;
import net.seep.odd.abilities.init.ArtificerCondenserRegistry;
import net.seep.odd.abilities.init.ArtificerMixerRegistry;
import net.seep.odd.abilities.lunar.item.LunarDrillItem;
import net.seep.odd.abilities.lunar.net.LunarPackets;
import net.seep.odd.abilities.necromancer.NecromancerCorpseDetonator;
import net.seep.odd.abilities.necromancer.NecromancerNet;
import net.seep.odd.abilities.net.*;
import net.seep.odd.abilities.owl.client.OwlSonarClient;
import net.seep.odd.abilities.owl.net.OwlNetworking;
import net.seep.odd.abilities.possession.PossessionManager;
import net.seep.odd.abilities.power.*;
import net.seep.odd.abilities.rat.PehkuiUtil;
import net.seep.odd.abilities.rider.RiderNet;
import net.seep.odd.abilities.spectral.SpectralNet;
import net.seep.odd.abilities.spectral.SpectralPhaseHooks;
import net.seep.odd.abilities.spectral.SpectralRenderState;
import net.seep.odd.abilities.spotted.SpottedNet;
import net.seep.odd.abilities.tamer.TamerLeveling;
import net.seep.odd.abilities.tamer.TamerMoves;
import net.seep.odd.abilities.vampire.VampireTempCrystalManager;
import net.seep.odd.abilities.voids.VoidRegistry;
import net.seep.odd.abilities.voids.VoidSystem;

import net.seep.odd.block.ModBlocks;
import net.seep.odd.block.falseflower.FalseFlowerTracker;
import net.seep.odd.block.grandanvil.ModScreens;
import net.seep.odd.block.grandanvil.net.GrandAnvilNet;
import net.seep.odd.block.grandanvil.recipe.ModGrandAnvilRecipes;

import net.seep.odd.commands.OddCooldownCommand;
import net.seep.odd.enchant.ItalianStompersHandler;
import net.seep.odd.enchant.ModEnchantments;

import net.seep.odd.entity.ModEntities;
import net.seep.odd.entity.car.RiderCarEntity;
import net.seep.odd.entity.car.radio.RadioTracksInit;
import net.seep.odd.entity.creepy.CreepyEntity;
import net.seep.odd.entity.falsefrog.FalseFrogEntity;
import net.seep.odd.entity.firefly.FireflyEntity;
import net.seep.odd.entity.outerman.OuterManEntity;
import net.seep.odd.entity.seal.SealEntity;
import net.seep.odd.entity.supercharge.SuperEntities;
import net.seep.odd.entity.ufo.UfoSaucerEntity;

import net.seep.odd.entity.zerosuit.client.AnnihilationFx;
import net.seep.odd.expeditions.Expeditions;
import net.seep.odd.expeditions.rottenroots.RottenRootsCommands;
import net.seep.odd.item.ModItemGroups;
import net.seep.odd.item.ModItems;

import net.seep.odd.particles.OddParticles;
import net.seep.odd.shader.ModShaders;
import net.seep.odd.shop.screen.ModScreenHandlers;
import net.seep.odd.sky.CelestialCommands;

import net.seep.odd.sound.ModSounds;

import net.seep.odd.status.ModStatusEffects;
import net.seep.odd.worldgen.ModSpawns;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.bernie.geckolib.GeckoLib;

public final class Oddities implements ModInitializer {
	public static final String MOD_ID = "odd";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// ---- content registration (common/server-safe) ----
		ModItemGroups.registerItemGroups();
		ModBlocks.registerModBlocks();
		ModItems.registerModItems();
		ModSounds.registerSounds();
		ModStatusEffects.init();
		net.seep.odd.recipe.ModRecipeSerializers.register();
		ModShaders.init();
		PowerNetworking.initServer();
		net.seep.odd.recipe.ModRecipes.init();
		net.seep.odd.screen.ModScreenHandlers.init();



		// Dabloon //
		ModScreenHandlers.register();
		net.seep.odd.shop.catalog.ShopCatalogManager.init();
		net.seep.odd.shop.command.ShopCommands.register();
		net.seep.odd.shop.ShopNetworking.registerC2S();
		net.seep.odd.shop.ShopServerTick.register();


		FuelRegistry.INSTANCE.add(ModItems.COAL_BRIQUETTE, 200);
		net.seep.odd.util.TickScheduler.init();
		OddParticles.register();

		// EXPEDITIONS
		Expeditions.register();





		// Entities & attributes
		ModEntities.register();
		FabricDefaultAttributeRegistry.register(ModEntities.CREEPY, CreepyEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(ModEntities.VILLAGER_EVO, net.seep.odd.abilities.tamer.entity.VillagerEvoEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(ModEntities.UFO_SAUCER, UfoSaucerEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(ModEntities.OUTERMAN, OuterManEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(ModEntities.RIDER_CAR, RiderCarEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(ModEntities.GHOSTLING, GhostlingEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(ModEntities.FALSE_FROG, FalseFrogEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(ModEntities.FIREFLY, FireflyEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(ModEntities.DARK_HORSE, DarkHorseEntity.createDarkHorseAttributes());
		FabricDefaultAttributeRegistry.register(ModEntities.SEAL, SealEntity.createSealAttributes());
		ModSpawns.register();



		// GeckoLib (common entrypoint)
		GeckoLib.initialize();

		// ---- Powers registry (no client classes referenced here) ----
		Powers.register(new FireBlastPower());
		Powers.register(new Blockade());
		Powers.register(new UmbraSoulPower());
		Powers.register(new ForgerPower());
		Powers.register(new MistyVeilPower());
		Powers.register(new TamerPower());
		Powers.register(new OverdrivePower());
		Powers.register(new VoidPower());
		Powers.register(new ArtificerPower());
		Powers.register(new SpectralPhasePower());
		Powers.register(new RiderPower());
		Powers.register(new CosmicPower());
		Powers.register(new GhostlingsPower());
		Powers.register(new IceWitchPower());
		Powers.register(new SpottedPhantomPower());
		Powers.register(new ZeroSuitPower());
		Powers.register(new LookerPower());
		Powers.register(new RatPower());
		Powers.register(new SuperChargePower());
		Powers.register(new GamblePower());
		Powers.register(new BuddymorphPower());
		Powers.register(new FallingSnowPower());
		Powers.register(new FairyPower());
		Powers.register(new LunarPower());
		Powers.register(new FireSwordPower());
		Powers.register(new GlitchPower());
		Powers.register(new AcceleratePower());
		Powers.register(new ConquerPower());
		Powers.register(new SplashPower());
		Powers.register(new CultistPower());
		Powers.register(new ClimberPower());
		Powers.register(new OwlPower());
		Powers.register(new RisePower());
		Powers.register(new NecromancerPower());
		Powers.register(new VampirePower());
		Powers.register(new DruidPower());
		Powers.register(new ChefPower());

		// ---- Commands ----
		PowerCommands.register();
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				CelestialCommands.register(dispatcher) // sun/moon commands: server-side only
		);

		// ---- Forger (common/server) ----
		ModScreens.register(); // screen HANDLERS (server-safe)
		ModEnchantments.register();
		ModGrandAnvilRecipes.register();
		GrandAnvilNet.registerServer();
		ItalianStompersHandler.register();
		ModEnchantments.registerTicker();


		// ---- Networking / tickers (server) ----
		AbilityKeyPacket.registerServerReceiver();

		PossessionManager.INSTANCE.registerTicker();
		net.seep.odd.abilities.net.PossessionControlPacket.registerServer((player, state) -> {
			var ses = PossessionManager.INSTANCE.getSession(player);
			if (ses != null) ses.last = state;
		});

		// Astral (server)
		net.seep.odd.abilities.astral.AstralGuards.register();
		AstralInventory.init(ModItems.GHOST_HAND);

		// Umbra (server tick)
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (var p : server.getPlayerManager().getPlayerList()) {
				UmbraSoulPower.serverTick(p);
			}
		});
		// Misty (server tick)
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (var p : server.getPlayerManager().getPlayerList()) {
				MistyVeilPower.serverTick(p);
			}
		});

		// Net channels (server/common)
		UmbraNet.registerServerAstral();
		UmbraAirSwimBoostNet.initServer();
		MistyNet.init(); // server receivers

		// Tamer
		net.seep.odd.abilities.tamer.ai.SpeciesProfiles.init();
		net.seep.odd.abilities.net.TamerNet.initCommon();
		TamerLeveling.register();
		AbilityServerTicks.init();
		TamerMoves.bootstrap();

		// Overdrive
		net.seep.odd.abilities.overdrive.OverdriveNet.initServer();
		net.seep.odd.abilities.overdrive.OverdriveSystem.registerServerTick();

		// Void
		VoidRegistry.initCommon();
		VoidSystem.init();

		// Artificer
		ArtificerCondenserRegistry.registerCommon();  // delegates to ArtificerCreateInit.register()
		ArtificerFluids.registerAll();
		ArtificerMixerRegistry.registerCommon();
		MixerNet.registerServer();
		net.seep.odd.util.CrystalTrapCleaner.init();


		// Spectral Phase
		net.seep.odd.abilities.spectral.SpectralNet.registerServer();




		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (var p : server.getPlayerManager().getPlayerList()) {
				net.seep.odd.abilities.power.SpectralPhasePower.serverTick(p);
			}
		});

		net.fabricmc.fabric.api.event.player.AttackBlockCallback.EVENT.register((player, world, hand, pos, dir) ->
				(player instanceof net.minecraft.server.network.ServerPlayerEntity sp
						&& net.seep.odd.abilities.power.SpectralPhasePower.isPhased(sp))
						? net.minecraft.util.ActionResult.FAIL : net.minecraft.util.ActionResult.PASS);
		net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hit) ->
				(player instanceof net.minecraft.server.network.ServerPlayerEntity sp
						&& net.seep.odd.abilities.power.SpectralPhasePower.isPhased(sp))
						? net.minecraft.util.ActionResult.FAIL : net.minecraft.util.ActionResult.PASS);
		net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) ->
				(player instanceof net.minecraft.server.network.ServerPlayerEntity sp
						&& net.seep.odd.abilities.power.SpectralPhasePower.isPhased(sp))
						? net.minecraft.util.ActionResult.FAIL : net.minecraft.util.ActionResult.PASS);
		net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register((player, world, hand, entity, hit) ->
				(player instanceof net.minecraft.server.network.ServerPlayerEntity sp
						&& net.seep.odd.abilities.power.SpectralPhasePower.isPhased(sp))
						? net.minecraft.util.ActionResult.FAIL : net.minecraft.util.ActionResult.PASS);


		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
				net.seep.odd.abilities.power.SpectralPhasePower.forceReset(handler.player)
		);

		// Rider
		RiderNet.registerServer();
		RadioTracksInit.init();


		// Cosmic Sword
		CosmicNet.registerServer();

		// Ghostlings
		net.seep.odd.abilities.power.GhostlingsPower.registerCommonHooks();
		GhostPackets.registerC2S();

		net.seep.odd.abilities.ghostlings.GhostPackets.registerC2S();
		GhostCargoScreenHandler.TYPE = net.fabricmc.fabric.api.screenhandler.v1.ScreenHandlerRegistry.registerSimple(
				new Identifier("odd","ghost_cargo"), GhostCargoScreenHandler::new);

		// Ice Witch
		IceWitchPackets.registerServer();
		IceWitchInit.registerCommon();

		//Spotted Phantom
		SpottedNet.initCommon();
		net.seep.odd.abilities.spotted.SpottedScreens.register();

		// Zero Gravity

		net.seep.odd.abilities.zerosuit.ZeroSuitNet.initCommon();
		AnnihilationFx.init();




		// Looker
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (var p : server.getPlayerManager().getPlayerList()) {
				net.seep.odd.abilities.power.LookerPower.serverTick(p);
			}
		});

		net.seep.odd.abilities.power.LookerPower.installPersistHooks();

		// Rat
		RatPower.bootstrap();
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
				Power pow = Powers.get(/* PowerAPI.get(p) or your accessor */ PowerAPI.get(p));
				if (pow instanceof RatPower)       RatPower.serverTick(p);

				// …other powers…
			}
		});

		// Supercharge
		 SuperEntities.register();
		 SuperChargePower.bootstrap();

		 // Buddymorph
		BuddymorphCommands.register();
		BuddymorphNet.init();

		// Fairy

		FairyPower.register();

		FalseFlowerTracker.registerServer();
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			net.seep.odd.block.falseflower.spell.BanishEffect.tickReturns(server);
		});


		// Lunar
		LunarPackets.registerServerReceivers();
		LunarPower.register();
		LunarDrillItem.registerHooks();


		// Conquer

		// Splash
		SplashPower.init();


		ShaderEffectManager.getInstance().manage(new Identifier(Oddities.MOD_ID, "shaders/post/annihilation.json"));

		// Climber
		net.seep.odd.abilities.climber.ClimberBootstrap.initCommon();
		ClimberClimbNetworking.registerServer();

		// Owl

		OwlPower.registerNetworking();

		// Rise
		RisePower.registerNetworking();

		// Necromancer
		NecromancerNet.initServer();
		NecromancerPower.init();
		NecromancerCorpseDetonator.init();

		// Vampire
		VampirePower.register();
		VampireTempCrystalManager.init();

		// Druid
		DruidNet.init();






































		AttackBlockCallback.EVENT.register((player, world, hand, pos, dir) ->
				(player instanceof ServerPlayerEntity sp && PossessionManager.INSTANCE.isPossessing(sp))
						? ActionResult.FAIL : ActionResult.PASS);
		UseBlockCallback.EVENT.register((player, world, hand, hit) ->
				(player instanceof ServerPlayerEntity sp && PossessionManager.INSTANCE.isPossessing(sp))
						? ActionResult.FAIL : ActionResult.PASS);
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) ->
				(player instanceof ServerPlayerEntity sp && PossessionManager.INSTANCE.isPossessing(sp))
						? ActionResult.FAIL : ActionResult.PASS);
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) ->
				(player instanceof ServerPlayerEntity sp && PossessionManager.INSTANCE.isPossessing(sp))
						? ActionResult.FAIL : ActionResult.PASS);

		// ---- Sync current power to client on join ----
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			var player = handler.player;
			String id = PowerAPI.get(player);
			PowerNetworking.syncTo(player, id);
			LOGGER.info("[Oddities] synced power '{}' to {}", id, player.getEntityName());
		});

		LOGGER.info("Oddities initialized (content + powers + networking).");
	}
}
