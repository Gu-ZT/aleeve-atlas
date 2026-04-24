package dev.dubhe.map.client.input;

import dev.dubhe.map.AleeveAtlas;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = AleeveAtlas.MOD_ID, value = Dist.CLIENT)
public final class AtlasKeyMappings {
    private static final String CATEGORY = "key.categories.aleeve_atlas";

    public static final KeyMapping TOGGLE_MINIMAP = new KeyMapping("key.aleeve_atlas.toggle_minimap", GLFW.GLFW_KEY_M, CATEGORY);
    public static final KeyMapping TOGGLE_ROTATION = new KeyMapping("key.aleeve_atlas.toggle_rotation", GLFW.GLFW_KEY_R, CATEGORY);
    public static final KeyMapping TOGGLE_SHAPE = new KeyMapping("key.aleeve_atlas.toggle_shape", GLFW.GLFW_KEY_V, CATEGORY);
    public static final KeyMapping RESET_ZOOM = new KeyMapping("key.aleeve_atlas.reset_zoom", GLFW.GLFW_KEY_UNKNOWN, CATEGORY);

    private AtlasKeyMappings() {
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_MINIMAP);
        event.register(TOGGLE_ROTATION);
        event.register(TOGGLE_SHAPE);
        event.register(RESET_ZOOM);
    }
}

