package dev.dubhe.map.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.dubhe.map.AleeveAtlas;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

/// 键位注册。
///
/// 全部使用 `InputConstants.Type.KEYSYM`（GLFW key token），禁止扫描码；
/// 游戏内键位使用 `IN_GAME` 冲突上下文，并注册独立 `KeyMapping.Category`。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID, value = Dist.CLIENT)
public final class AtlasKeyMappings {
    public static final String CATEGORY = "key.category.aleeve_atlas.main";

    public static final KeyMapping OPEN_WORLD_MAP = new KeyMapping(
            "key.aleeve_atlas.open_world_map",
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            CATEGORY);

    public static final KeyMapping NEW_WAYPOINT = new KeyMapping(
            "key.aleeve_atlas.new_waypoint",
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_N,
            CATEGORY);

    public static final KeyMapping MINIMAP_ZOOM_IN = new KeyMapping(
            "key.aleeve_atlas.minimap_zoom_in",
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_EQUAL,
            CATEGORY);

    public static final KeyMapping MINIMAP_ZOOM_OUT = new KeyMapping(
            "key.aleeve_atlas.minimap_zoom_out",
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_MINUS,
            CATEGORY);

    public static final KeyMapping WORLD_PING = new KeyMapping(
            "key.aleeve_atlas.world_ping",
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.MOUSE,
            GLFW.GLFW_MOUSE_BUTTON_MIDDLE,
            CATEGORY);

    public static final KeyMapping FOCUS_MINIMAP = new KeyMapping(
            "key.aleeve_atlas.focus_minimap",
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Z,
            CATEGORY);

    private AtlasKeyMappings() {
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_WORLD_MAP);
        event.register(NEW_WAYPOINT);
        event.register(MINIMAP_ZOOM_IN);
        event.register(MINIMAP_ZOOM_OUT);
        event.register(WORLD_PING);
        event.register(FOCUS_MINIMAP);
    }
}
