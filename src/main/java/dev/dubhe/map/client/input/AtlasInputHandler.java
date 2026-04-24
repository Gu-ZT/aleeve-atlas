package dev.dubhe.map.client.input;

import dev.dubhe.map.AleeveAtlas;
import dev.dubhe.map.client.screen.WaypointListScreen;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = AleeveAtlas.MOD_ID, value = Dist.CLIENT)
public final class AtlasInputHandler {
    private AtlasInputHandler() {
    }

    @SubscribeEvent
    public static void onClientTickPost(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        while (AtlasKeyMappings.OPEN_SETTINGS.consumeClick()) {
            ModContainer container = ModList.get().getModContainerById(AleeveAtlas.MOD_ID).orElse(null);
            if (container != null) {
                minecraft.setScreen(new ConfigurationScreen(container, minecraft.screen));
            }
        }
        while (AtlasKeyMappings.OPEN_WAYPOINTS.consumeClick()) {
            minecraft.setScreen(new WaypointListScreen(minecraft.screen));
        }
    }

}

