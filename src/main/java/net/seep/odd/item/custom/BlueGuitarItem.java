package net.seep.odd.item.custom;

import java.util.function.Consumer;

public class BlueGuitarItem extends GuitarItem {
    public BlueGuitarItem(Settings settings) {
        super(settings);
    }

    @Override
    public void createRenderer(Consumer<Object> consumer) {
        GeoItemClientHooks.createGeoItemRenderer(
                consumer,
                "net.seep.odd.item.custom.client.BlueGuitarItemRenderer"
        );
    }
}
