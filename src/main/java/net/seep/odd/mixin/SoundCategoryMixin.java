package net.seep.odd.mixin;

import net.minecraft.sound.SoundCategory;
import net.seep.odd.sound.OddSoundCategories;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;

@Mixin(SoundCategory.class)
public abstract class SoundCategoryMixin {

    @Invoker("<init>")
    private static SoundCategory odd$invokeInit(String internalName, int ordinal, String name) {
        throw new AssertionError();
    }

    @Unique private static final String ENUM_NAME = "ODD_DISTANT_ISLES";
    @Unique private static final String CAT_NAME  = "odd_distant_isles"; // lang key uses soundCategory.<this>

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void odd$addDistantIsles(CallbackInfo ci) {
        try {
            Field valuesField = odd$findValuesField();
            if (valuesField == null) return;

            valuesField.setAccessible(true);
            SoundCategory[] values = (SoundCategory[]) valuesField.get(null);
            if (values == null) return;

            // already injected?
            for (SoundCategory c : values) {
                if (c.name().equals(ENUM_NAME)) {
                    OddSoundCategories.setDistantIsles(c);
                    return;
                }
            }

            SoundCategory cat = odd$invokeInit(ENUM_NAME, values.length, CAT_NAME);

            SoundCategory[] newValues = Arrays.copyOf(values, values.length + 1);
            newValues[newValues.length - 1] = cat;
            valuesField.set(null, newValues);

            // store direct reference so we never rely on valueOf() caches
            OddSoundCategories.setDistantIsles(cat);

        } catch (Throwable t) {
            // don’t crash the whole game if something goes weird
            // (optional) log if you have a logger:
            // Oddities.LOGGER.error("Failed to extend SoundCategory", t);
        }
    }

    @Unique
    private static Field odd$findValuesField() {
        // most Java enums compile to a synthetic field literally named "$VALUES"
        try {
            return SoundCategory.class.getDeclaredField("$VALUES");
        } catch (NoSuchFieldException ignored) {}

        // fallback: find any static SoundCategory[] field
        for (Field f : SoundCategory.class.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers())) continue;
            if (f.getType().isArray() && f.getType().getComponentType() == SoundCategory.class) {
                return f;
            }
        }
        return null;
    }
}