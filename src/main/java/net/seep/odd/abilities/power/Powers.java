package net.seep.odd.abilities.power;


import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class Powers {
    private static final Map<String, Power> REGISTRY = new HashMap<>();
    private Powers() {}
    public static Power register(Power power) { REGISTRY.put(power.id(), power); return power; }
    public static Power get(String id) { return REGISTRY.get(id); }
    public static boolean exists(String id) { return REGISTRY.containsKey(id); }
    public static Map<String, Power> all() { return Collections.unmodifiableMap(REGISTRY); }
}