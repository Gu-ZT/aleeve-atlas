package dev.dubhe.map.data;

import dev.dubhe.map.AleeveAtlas;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;

@EventBusSubscriber(modid = AleeveAtlas.MOD_ID)
public final class AleeveAtlasDataGen {
    private AleeveAtlasDataGen() {
    }

    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        event.addProvider(new AleeveAtlasLanguageProvider(event.getGenerator().getPackOutput()));
    }
}
