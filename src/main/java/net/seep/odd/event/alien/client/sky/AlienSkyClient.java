package net.seep.odd.event.alien.client.sky;

import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import net.seep.odd.Oddities;

import java.io.IOException;

public final class AlienSkyClient {
    private AlienSkyClient() {}

    public static ShaderProgram ALIEN_SKY = null;

    public static void init() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(
                new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public Identifier getFabricId() {
                        return new Identifier(Oddities.MOD_ID, "alien_overworld_sky_loader");
                    }

                    @Override
                    public void reload(ResourceManager manager) {
                        if (ALIEN_SKY != null) {
                            ALIEN_SKY.close();
                            ALIEN_SKY = null;
                        }
                        try {
                            // This loads from assets/odd/shaders/program/alien_overworld_sky.json
                            ALIEN_SKY = new ShaderProgram(
                                    manager,
                                    Oddities.MOD_ID + ":alien_overworld_sky",
                                    VertexFormats.POSITION
                            );
                        } catch (IOException e) {
                            ALIEN_SKY = null;
                        }
                    }
                }
        );
    }
}