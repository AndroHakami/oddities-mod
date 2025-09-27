package net.seep.odd.abilities.spectral;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SpectralPhaseClientFlag {
    private static final Set<UUID> PHASED = ConcurrentHashMap.newKeySet();
    private SpectralPhaseClientFlag() {}
    public static void set(UUID id, boolean on) { if (on) PHASED.add(id); else PHASED.remove(id); }
    public static boolean isPhased(UUID id)      { return PHASED.contains(id); }
}
