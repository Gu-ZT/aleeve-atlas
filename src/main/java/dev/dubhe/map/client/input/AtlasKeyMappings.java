package dev.dubhe.map.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import dev.dubhe.map.AleeveAtlas;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = AleeveAtlas.MOD_ID, value = Dist.CLIENT)
public final class AtlasKeyMappings {
    public static final String CATEGORY = "key.categories." + AleeveAtlas.MOD_ID;
    public static final KeyMapping OPEN_SETTINGS = new KeyMapping(
        "key." + AleeveAtlas.MOD_ID + ".open_settings",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_M,
        CATEGORY
    );
    public static final KeyMapping OPEN_WAYPOINTS = new KeyMapping(
        "key." + AleeveAtlas.MOD_ID + ".open_waypoints",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_B,
        CATEGORY
    );

    private AtlasKeyMappings() {
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_SETTINGS);
        event.register(OPEN_WAYPOINTS);
    }
}

