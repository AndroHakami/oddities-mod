// FILE: src/main/java/net/seep/odd/client/audio/DistantIslesMusicVolume.java
package net.seep.odd.client.audio;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DistantIslesMusicVolume {
    private DistantIslesMusicVolume() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE =
            FabricLoader.getInstance().getConfigDir().resolve("odd_distant_isles_music.json");

    private static double volume = 1.0D;
    private static boolean loaded = false;

    public static void load() {
        if (loaded) return;
        loaded = true;

        if (!Files.exists(FILE)) {
            save();
            return;
        }

        try {
            String json = Files.readString(FILE, StandardCharsets.UTF_8);
            JsonObject obj = GSON.fromJson(json, JsonObject.class);
            if (obj != null && obj.has("volume")) {
                volume = clamp(obj.get("volume").getAsDouble());
            }
        } catch (Exception ignored) {
            volume = 1.0D;
            save();
        }
    }

    public static double get() {
        load();
        return volume;
    }

    public static float getFloat() {
        return (float) get();
    }

    public static void set(double value) {
        load();
        volume = clamp(value);
        save();

        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.getSoundManager() != null && client.options != null) {
            float master = client.options.getSoundVolume(SoundCategory.MASTER);
            client.getSoundManager().updateSoundVolume(SoundCategory.MASTER, master);
        }
    }

    private static double clamp(double value) {
        return MathHelper.clamp(value, 0.0D, 1.0D);
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());

            JsonObject obj = new JsonObject();
            obj.addProperty("volume", volume);

            Files.writeString(FILE, GSON.toJson(obj), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    public static SimpleOption<Double> createOption() {
        load();

        return new SimpleOption<>(
                "options.odd.distant_isles_music",
                SimpleOption.emptyTooltip(),
                (optionText, value) -> Text.translatable(
                        "options.percent_value",
                        optionText,
                        (int) Math.round(value * 100.0D)
                ),
                SimpleOption.DoubleSliderCallbacks.INSTANCE,
                get(),
                DistantIslesMusicVolume::set
        );
    }
}