package net.seep.odd.sound;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;
import net.seep.odd.entity.car.RiderCarEntity;

public class ModSounds {

    public static final SoundEvent METAL_DETECTOR_FOUND_ORE = registerSoundEvent("metal_detector_found_ore");
    public static final SoundEvent SOUND_BLOCK_BREAK = registerSoundEvent("sound_block_break");
    public static final SoundEvent SOUND_BLOCK_STEP = registerSoundEvent("sound_block_step");
    public static final SoundEvent SOUND_BLOCK_PLACE = registerSoundEvent("sound_block_place");
    public static final SoundEvent SOUND_BLOCK_HIT = registerSoundEvent("sound_block_hit");
    public static final SoundEvent SOUND_BLOCK_FALL = registerSoundEvent("sound_block_fall");
    public static final SoundEvent SNOWGRAVE_TELEPORT = registerSoundEvent("snowgrave_teleport");
    public static final SoundEvent FORGER_HIT = registerSoundEvent("forger_hit");
    public static final SoundEvent ITALIAN_STOMPERS_JUMP = registerSoundEvent("italian_stompers_jump");
    public static final SoundEvent CREEPY_PULL = registerSoundEvent("creepy_pull");
    public static final SoundEvent CREEPY_PUSH = registerSoundEvent("creepy_push");
    public static final SoundEvent CRAPPY_BLOCK_PLACE = registerSoundEvent("crappy_block_place");
    public static final SoundEvent CRAPPY_BLOCK_FALL = registerSoundEvent("crappy_block_fall");
    public static final SoundEvent CRAPPY_BLOCK_BREAK = registerSoundEvent("crappy_block_break");
    public static final SoundEvent CRAPPY_BLOCK_HIT = registerSoundEvent("crappy_block_hit");
    public static final SoundEvent CRAPPY_BLOCK_STEP = registerSoundEvent("crappy_block_step");
    public static final SoundEvent VOID_AMBIENCE = registerSoundEvent("void_ambience");
    public static final SoundEvent ROTTEN_ROOTS = registerSoundEvent("rotten_roots");
    public static final SoundEvent VOID_OPEN = registerSoundEvent("void_open");
    public static final SoundEvent TELEKINESIS = registerSoundEvent("glitch_telekinesis");
    public static final SoundEvent TELEKINESIS_GRAB = registerSoundEvent("glitch_telekinesis_grab");
    public static final SoundEvent TELEKINESIS_EN = registerSoundEvent("glitch_telekinesis_end");
    public static final SoundEvent ACCELERATE = registerSoundEvent("accelerate");


    // UFO SHIT
    public static final SoundEvent SAUCER_HOVER = registerSoundEvent("saucer_hover");
    public static final SoundEvent SAUCER_ATTACK = registerSoundEvent("saucer_attack");
    public static final SoundEvent SAUCER_BOOST = registerSoundEvent("saucer_boost");
    public static final SoundEvent SAUCER_TRACTOR = registerSoundEvent("saucer_tractor");
    public static final SoundEvent SAUCER_HURT = registerSoundEvent("saucer_hurt");
    public static final SoundEvent SAUCER_DEATH = registerSoundEvent("saucer_death");

    // Rider Car
    public static final SoundEvent CAR_START = registerSoundEvent("car_start");
    public static final SoundEvent CAR_ACC = registerSoundEvent("car_acc");


    //Rider Car Radio
    public static final SoundEvent RADIO_TRACK1 = registerSoundEvent("radio_spanish");
    public static final SoundEvent RADIO_TRACK2 = registerSoundEvent("radio_fight");
    public static final SoundEvent RADIO_TRACK3 = registerSoundEvent("radio_ryan");
    public static final SoundEvent RADIO_TRACK4 = registerSoundEvent("radio_light");

    // Glacier
    public static final SoundEvent ICE_SPELL = registerSoundEvent("ice_spell");

    // Zero Suit
    public static final SoundEvent ZERO_CHARGE = registerSoundEvent("zero_charge");
    public static final SoundEvent ZERO_BLAST = registerSoundEvent("zero_blast");
    public static final SoundEvent GRAVITY = registerSoundEvent("gravity");
    public static final SoundEvent GRAVITY_SWITCH = registerSoundEvent("gravity_switch");

    // Buddymorph
    public static final SoundEvent MELODY = registerSoundEvent("melody");

    // Lunar
    public static final SoundEvent DRILL = registerSoundEvent("drill");
    public static final SoundEvent DRILL_CHARGE = registerSoundEvent("drill_charge");
    public static final SoundEvent DRILL_READY = registerSoundEvent("drill_ready");

    // Splash
    public static final SoundEvent BUBBLES = registerSoundEvent("bubbles");
    public static final SoundEvent STREAM = registerSoundEvent("stream");
    public static final SoundEvent STREAM_APPLIED = registerSoundEvent("stream_applied");



    // Shop
    public static final SoundEvent SHOP_MUSIC = registerSoundEvent("shop_music");

    // Cultist
    public static final SoundEvent SHY_GUY_AMBIENT = registerSoundEvent("shy_guy_ambient");
    public static final SoundEvent SHY_GUY_SIT = registerSoundEvent("shy_guy_sit");
    public static final SoundEvent SHY_GUY_SIT_POST_RAGE = registerSoundEvent("shy_guy_sit_post_rage");
    public static final SoundEvent SHY_GUY_RAGE_WINDUP = registerSoundEvent("shy_guy_rage");
    public static final SoundEvent SHY_GUY_RAGE_RUN = registerSoundEvent("shy_guy_rage_run");
    public static final SoundEvent SHY_GUY_ATTACK = registerSoundEvent("shy_guy_attack");



    public static final BlockSoundGroup SOUND_BLOCK_SOUNDS = new BlockSoundGroup(1, 1,
            ModSounds.SOUND_BLOCK_BREAK,
            ModSounds.SOUND_BLOCK_STEP, ModSounds.SOUND_BLOCK_PLACE,
            ModSounds.SOUND_BLOCK_HIT, ModSounds.SOUND_BLOCK_FALL);

    public static final BlockSoundGroup CRAPPY_BLOCK_SOUNDS = new BlockSoundGroup(1, 1,
            ModSounds.CRAPPY_BLOCK_BREAK,
            ModSounds.CRAPPY_BLOCK_STEP, ModSounds.CRAPPY_BLOCK_PLACE,
            ModSounds.CRAPPY_BLOCK_HIT, ModSounds.CRAPPY_BLOCK_FALL);



    private static SoundEvent registerSoundEvent(String name) {

        Identifier id = new Identifier(Oddities.MOD_ID, name);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }


    public static void registerSounds() {

        Oddities.LOGGER.info("Registering Sounds for " + Oddities.MOD_ID);

    }

}
