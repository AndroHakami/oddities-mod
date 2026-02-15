// FILE: src/main/java/net/seep/odd/block/gate/GateStyle.java
package net.seep.odd.block.gate;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.joml.Vector3f;

public record GateStyle(
        Identifier id,
        Identifier geoModel,
        Identifier texture,
        Identifier animation,
        Identifier portalPost,       // satin post chain id (json)
        Vector3f glowColor,          // glow color for portal fx
        Identifier defaultDestWorldId // world id (Identifier, NOT RegistryKey)
) {
    /** Convenience helper (server-side use). */
    public RegistryKey<World> defaultDestWorldKey() {
        return RegistryKey.of(RegistryKeys.WORLD, defaultDestWorldId);
    }
}
