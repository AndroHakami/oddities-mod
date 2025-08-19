package net.seep.odd.sound;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;

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
    public static final SoundEvent CRAPPY_BLOCK_PLACE = registerSoundEvent("crappy_block_place");
    public static final SoundEvent CRAPPY_BLOCK_FALL = registerSoundEvent("crappy_block_fall");
    public static final SoundEvent CRAPPY_BLOCK_BREAK = registerSoundEvent("crappy_block_break");
    public static final SoundEvent CRAPPY_BLOCK_HIT = registerSoundEvent("crappy_block_hit");
    public static final SoundEvent CRAPPY_BLOCK_STEP = registerSoundEvent("crappy_block_step");

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
