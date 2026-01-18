package net.seep.odd.shader;

import ladysnake.satin.api.managed.ManagedShaderEffect;
import ladysnake.satin.api.managed.ShaderEffectManager;
import net.minecraft.util.Identifier;

public class ModShaders {
    public static ManagedShaderEffect MY_SHADER;

    public static void init() {
        MY_SHADER = ShaderEffectManager.getInstance()
                .manage(new Identifier("odd", "shaders/post/annihilation.json"));
    }
}