package dev.dubhe.map.data;

import dev.dubhe.map.AleeveAtlas;
import dev.dubhe.map.data.lang.AleeveAtlasLanguageProvider;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;

@EventBusSubscriber(modid = AleeveAtlas.MOD_ID)
public class AleeveAtlasData {
    @SubscribeEvent
    public static void gatherData(GatherDataEvent.Client event) {
        DataGenerator generator = event.getGenerator();
        PackOutput packOutput = generator.getPackOutput();
        generator.addProvider(true, new AleeveAtlasLanguageProvider(packOutput));
    }
}
