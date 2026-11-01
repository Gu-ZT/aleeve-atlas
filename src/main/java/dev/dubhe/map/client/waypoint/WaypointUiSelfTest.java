package dev.dubhe.map.client.waypoint;

import dev.anvilcraft.lib.v2.config.client.SidebarConfigScreen;
import dev.dubhe.map.AleeveAtlas;
import dev.dubhe.map.client.map.AtlasWorldMap;
import dev.dubhe.map.client.map.WorldMapScreen;
import dev.dubhe.map.waypoint.Waypoint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// 路径点界面的自动化验收，设置 `ALEEVE_ATLAS_WAYPOINT_UI_SELFTEST` 后执行。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID, value = Dist.CLIENT)
public final class WaypointUiSelfTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("aleeve_atlas/waypoint-ui");

    private static int ticks;
    private static int phase;
    private static boolean done;

    private WaypointUiSelfTest() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (done || System.getenv("ALEEVE_ATLAS_WAYPOINT_UI_SELFTEST") == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        if (++ticks < 60) {
            return;
        }
        ticks = 0;
        Screen screen = minecraft.screen;
        if (phase == 0) {
            if (screen == null) {
                AtlasWorldMap.open();
                phase++;
            }
            return;
        }
        boolean advanced = switch (phase) {
            case 1 -> screen instanceof WorldMapScreen
                    && clickButton(screen, "aleeve_atlas.screen.waypoint_list");
            case 2 -> screen instanceof WaypointListScreen
                    && clickButton(screen, "aleeve_atlas.screen.waypoint_list.add");
            case 3 -> screen instanceof WaypointEditScreen && prepareCreateForm(screen)
                    && clickButton(screen, "aleeve_atlas.screen.waypoint_edit.save");
            case 4 -> screen instanceof WaypointListScreen list && selectFirstWaypoint(list)
                    && clickButton(screen, "aleeve_atlas.screen.waypoint_list.edit");
            case 5 -> screen instanceof WaypointEditScreen
                    && clickButton(screen, "aleeve_atlas.screen.waypoint_edit.save");
            case 6 -> screen instanceof WaypointListScreen
                    && clickButton(screen, "aleeve_atlas.screen.waypoint_list.hide");
            case 7 -> screen instanceof WaypointListScreen && hasDisabledWaypoint()
                    && clickButton(screen, "aleeve_atlas.screen.waypoint_list.show");
            case 8 -> screen instanceof WaypointListScreen
                    && clickButton(screen, "gui.back");
            case 9 -> screen instanceof WorldMapScreen
                    && clickButton(screen, "aleeve_atlas.screen.world_map.client_config");
            case 10 -> (screen instanceof ConfigurationScreen || screen instanceof SidebarConfigScreen)
                    && clickButton(screen, "gui.done");
            case 11 -> screen instanceof WorldMapScreen;
            case 12 -> screen instanceof WorldMapScreen
                    && clickButton(screen, "aleeve_atlas.screen.waypoint_list");
            case 13 -> screen instanceof WaypointListScreen list && dragScrollbar(list);
            default -> false;
        };
        if (!advanced) {
            LOGGER.warn("Waypoint UI self-test stopped at phase {} with screen {}", phase,
                    screen == null ? "game" : screen.getTitle().getString());
            done = true;
            return;
        }
        LOGGER.info("Waypoint UI self-test completed phase {}", phase);
        phase++;
        Screen current = minecraft.screen;
        LOGGER.info("Waypoint UI self-test current screen after phase {}: {}", phase - 1,
                current == null ? "game" : current.getClass().getSimpleName());
        if (phase > 13) {
            done = true;
        }
    }

    /// 在创建表单中写入长名称、坐标与颜色，覆盖真实输入控件。
    private static boolean prepareCreateForm(Screen screen) {
        return setEditValue(screen, "aleeve_atlas.screen.waypoint_edit.name",
                "Long waypoint label for horizontal scrolling 0123456789")
                && setEditValue(screen, "X", "123")
                && setEditValue(screen, "Y", "80")
                && setEditValue(screen, "Z", "-456")
                && setEditValue(screen, "aleeve_atlas.screen.waypoint_edit.color", "#FF55FF");
    }

    private static boolean setEditValue(Screen screen, String label, String value) {
        String expected = label.length() == 1 ? label : Component.translatable(label).getString();
        for (GuiEventListener child : screen.children()) {
            if (child instanceof EditBox box && box.getMessage().getString().equals(expected)) {
                box.setValue(value);
                return true;
            }
        }
        LOGGER.warn("Waypoint UI self-test input not found: {}", label);
        return false;
    }

    private static boolean dragScrollbar(WaypointListScreen screen) {
        int visibleRows = Math.max(1, (screen.height - 36 - 56) / 24);
        ensureScrollbarEntries(screen, visibleRows);
        int bottom = 36 + visibleRows * 24 - 4;
        final int previous = screen.scrollOffset();

        if (!screen.mouseClicked(screen.width - 7, 40,
                GLFW.GLFW_MOUSE_BUTTON_LEFT)) {
            return false;
        }

        screen.mouseDragged(screen.width - 7, bottom,
                GLFW.GLFW_MOUSE_BUTTON_LEFT , 0.0, bottom - 40.0);
        screen.mouseReleased(screen.width - 7, bottom,
                GLFW.GLFW_MOUSE_BUTTON_LEFT);
        LOGGER.info("Waypoint UI self-test scrollbar offset {} -> {}", previous,
                screen.scrollOffset());
        return screen.scrollOffset() > previous;
    }

    private static boolean hasDisabledWaypoint() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null && ClientWaypoints.allInDimension(minecraft.level.dimension())
                .stream().anyMatch(waypoint -> !waypoint.enabled());
    }

    /// 干净存档也建立足够的条目，确保滚动条拖动路径得到执行。
    private static void ensureScrollbarEntries(WaypointListScreen screen, int visibleRows) {
        Minecraft minecraft = Minecraft.getInstance();
        int existing = ClientWaypoints.allInDimension(minecraft.level.dimension()).size();
        for (int i = existing; i < visibleRows + 2; i++) {
            ClientWaypoints.add(Waypoint.create("Scroll test " + i,
                    minecraft.player.blockPosition().offset(i, 0, 0),
                    minecraft.level.dimension(), 0xFF55FFFF));
        }
        screen.reloadEntries();
    }

    private static boolean selectFirstWaypoint(WaypointListScreen screen) {
        return screen.mouseClicked(20, 44, GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    /// 通过 Screen 的鼠标事件点击按钮，覆盖按钮与地图拖动的事件分发。
    private static boolean clickButton(Screen screen, String translationKey) {
        String label = Component.translatable(translationKey).getString();
        for (GuiEventListener child : screen.children()) {
            if (child instanceof Button button && button.getMessage().getString().equals(label)) {

                if (!screen.mouseClicked(
                        button.getX() + button.getWidth() / 2.0,
                        button.getY() + button.getHeight() / 2.0,
                        GLFW.GLFW_MOUSE_BUTTON_LEFT)) {
                    LOGGER.warn("Waypoint UI self-test click was not consumed: {}", translationKey);
                    return false;
                }
                screen.mouseReleased(
                        button.getX() + button.getWidth() / 2.0,
                        button.getY() + button.getHeight() / 2.0,
                        GLFW.GLFW_MOUSE_BUTTON_LEFT);
                LOGGER.info("Waypoint UI self-test clicked {}", translationKey);
                return true;
            }
        }
        LOGGER.warn("Waypoint UI self-test button not found: {}", translationKey);
        return false;
    }
}
