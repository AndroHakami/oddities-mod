package net.seep.odd.entity.cultist.client;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.util.math.Box;
import net.seep.odd.entity.cultist.ShyGuyEntity;
import net.seep.odd.sound.ModSounds;

import java.util.List;

public final class ShyGuyClientSounds {
    private ShyGuyClientSounds() {}

    private enum LoopKind { NONE, AMBIENT, SIT, SIT_POST_RAGE, RAGE_RUN }

    private static final class Handle {
        LoopKind kind;
        ShyGuyLoopSoundInstance inst;

        Handle(LoopKind kind, ShyGuyLoopSoundInstance inst) {
            this.kind = kind;
            this.inst = inst;
        }
    }

    private static final Int2ObjectOpenHashMap<Handle> ACTIVE = new Int2ObjectOpenHashMap<>();

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(ShyGuyClientSounds::tick);
    }

    private static void tick(MinecraftClient client) {
        if (client.world == null || client.getSoundManager() == null) {
            stopAll(client);
            return;
        }

        SoundManager sm = client.getSoundManager();
        IntSet seen = new IntOpenHashSet();

        // only check nearby shyguy entities for performance
        Box box = (client.player != null)
                ? client.player.getBoundingBox().expand(160)
                : new Box(-160, -160, -160, 160, 160, 160);

        List<ShyGuyEntity> guys = client.world.getEntitiesByClass(ShyGuyEntity.class, box, e -> true);

        for (ShyGuyEntity e : guys) {
            int id = e.getId();
            seen.add(id);

            LoopKind desired = desiredLoop(e);

            Handle h = ACTIVE.get(id);
            if (desired == LoopKind.NONE) {
                if (h != null) {
                    sm.stop(h.inst);
                    ACTIVE.remove(id);
                }
                continue;
            }

            if (h == null || h.kind != desired || !sm.isPlaying(h.inst)) {
                if (h != null) sm.stop(h.inst);

                ShyGuyLoopSoundInstance inst = switch (desired) {
                    case AMBIENT -> new ShyGuyLoopSoundInstance(e, ModSounds.SHY_GUY_AMBIENT, 0.75f, 1.0f);
                    case SIT -> new ShyGuyLoopSoundInstance(e, ModSounds.SHY_GUY_SIT, 0.95f, 1.0f);
                    case SIT_POST_RAGE -> new ShyGuyLoopSoundInstance(e, ModSounds.SHY_GUY_SIT_POST_RAGE, 1.0f, 1.0f);
                    case RAGE_RUN -> new ShyGuyLoopSoundInstance(e, ModSounds.SHY_GUY_RAGE_RUN, 0.95f, 1.0f);
                    default -> null;
                };

                if (inst != null) {
                    sm.play(inst);
                    ACTIVE.put(id, new Handle(desired, inst));
                }
            }
        }

        // cleanup old handles
        ACTIVE.int2ObjectEntrySet().removeIf(entry -> {
            int id = entry.getIntKey();
            if (!seen.contains(id)) {
                sm.stop(entry.getValue().inst);
                return true;
            }
            return false;
        });
    }

    private static LoopKind desiredLoop(ShyGuyEntity e) {
        int state = e.getSyncedState();

        // transitions + rage windup: NO loops
        if (state == 1 || state == 3 || state == 4 || state == 6) return LoopKind.NONE;

        // enraged run loop
        if (state == 5) return LoopKind.RAGE_RUN;

        // sitting loops
        if (state == 2) return e.isPostRageSitting() ? LoopKind.SIT_POST_RAGE : LoopKind.SIT;

        // idle/walk loop
        if (state == 0) return LoopKind.AMBIENT;

        return LoopKind.NONE;
    }

    private static void stopAll(MinecraftClient client) {
        if (client != null && client.getSoundManager() != null) {
            for (Handle h : ACTIVE.values()) {
                client.getSoundManager().stop(h.inst);
            }
        }
        ACTIVE.clear();
    }
}
