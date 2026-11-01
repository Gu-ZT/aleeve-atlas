package dev.dubhe.map.client.cartography;

import com.mojang.blaze3d.platform.InputConstants;
import dev.dubhe.map.AleeveAtlas;
import dev.dubhe.map.AtlasSession;
import dev.dubhe.map.client.map.AtlasWorldMap;
import dev.dubhe.map.client.map.WorldMapScreen;
import dev.dubhe.map.client.waypoint.ClientWaypoints;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.LoggerFactory;

/// 环境变量开启的实机验收：同一套步骤用于单机和专用服务器。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID, value = Dist.CLIENT)
public final class CartographyAcceptanceTest {
    private static int ticks, step;
    private static boolean finished;
    private static WorldMapScreen map;
    private static java.util.UUID targetId;
    private static int targetCount;
    private CartographyAcceptanceTest() { }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (finished || System.getenv("ALEEVE_ATLAS_CARTOGRAPHY_TEST") == null) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || ++ticks < 40) return;
        ticks = 0;
        try {
            switch (step) {
                case 0 -> {
                    require(ClientCartography.available(), "new channel negotiated");
                    require(!AtlasSession.synchronizedMode(), "works with team sync disabled");
                    AtlasWorldMap.open(); map = (WorldMapScreen) mc.gui.screen();
                }
                case 1 -> press(ClientCartography.text("import").getString());
                case 2 -> {
                    require(!ClientCartography.currentStore().intersecting(10000, 10000, 11000, 11000).isEmpty(), "snapshot saved");
                    var target = ClientWaypoints.store().all().stream().filter(w -> w.icon().equals("treasure")).findFirst().orElseThrow();
                    targetId = target.id(); targetCount = ClientWaypoints.store().all().size();
                    ClientWaypoints.store().upsert(target.withDetails("Edited treasure", target.pos(), target.colorArgb(), -1, false, false));
                    screenshot("imported"); press(ClientCartography.text("import").getString());
                }
                case 3 -> {
                    require(ClientWaypoints.store().all().size() == targetCount, "no duplicate targets");
                    require(ClientWaypoints.store().get(targetId).orElseThrow().name().equals("Edited treasure"), "edits retained");
                    press(ClientCartography.text("craft").getString());
                }
                case 4 -> {
                    screenshot("selection");
                    map.mouseClicked(new MouseButtonEvent(map.width / 2.0, map.height / 2.0, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
                    require(mc.gui.screen() instanceof CartographyConfirmScreen, "selection opens preview");
                }
                case 5 -> {
                    var box = mc.gui.screen().children().stream().filter(Checkbox.class::isInstance).map(Checkbox.class::cast).findFirst().orElseThrow();
                    require(box.active && !box.selected(), "glass allows opt-in locking");
                    box.onPress(null); screenshot("preview-locked");
                }
                case 6 -> press(Component.translatable("gui.ok").getString());
                case 7 -> {
                    require(mc.gui.screen() == map, "success returns to map");
                    require(mc.player.getItemInHand(InteractionHand.OFF_HAND).getCount() == 2, "exactly one empty map consumed");
                    require(dev.dubhe.map.cartography.Cartography.glassSlot(mc.player) < 0, "glass consumed");
                    var crafted = mc.player.getInventory().getItem(1);
                    require(crafted.is(Items.FILLED_MAP), "result replaces consumed glass inventory slot");
                    var id = crafted.get(DataComponents.MAP_ID);
                    // 原版持图才推送图像；服务端集成测试另验证保存数据。
                    if (mc.hasSingleplayerServer()) mc.getSingleplayerServer().execute(() -> {
                        var data = MapItem.getSavedData(id, mc.getSingleplayerServer().overworld());
                        require(data != null && data.locked && data.scale == 0, "locked vanilla saved data");
                        LoggerFactory.getLogger(CartographyAcceptanceTest.class).info("Cartography server save assertion PASSED");
                    });
                    press(ClientCartography.text("craft").getString());
                }
                case 8 -> map.mouseClicked(new MouseButtonEvent(map.width / 2.0, map.height / 2.0, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
                case 9 -> {
                    var box = mc.gui.screen().children().stream().filter(Checkbox.class::isInstance).map(Checkbox.class::cast).findFirst().orElseThrow();
                    require(!box.active && !box.selected(), "no glass disables locking");
                    screenshot("preview-unlocked"); press(Component.translatable("gui.ok").getString());
                }
                case 10 -> {
                    require(mc.gui.screen() == map && mc.player.getItemInHand(InteractionHand.OFF_HAND).getCount() == 1,
                            "unlocked creation consumes exactly one more map");
                    finished = true;
                    LoggerFactory.getLogger(CartographyAcceptanceTest.class).info("Cartography acceptance PASSED ({})", mc.hasSingleplayerServer() ? "integrated" : "dedicated");
                }
                default -> { }
            }
            LoggerFactory.getLogger(CartographyAcceptanceTest.class).info("Cartography acceptance step {} passed", step++);
        } catch (Exception e) {
            finished = true;
            LoggerFactory.getLogger(CartographyAcceptanceTest.class).error("Acceptance state: screen={}, selected={}, main={}, off={}",
                    mc.gui.screen(), mc.player.getInventory().getSelectedSlot(), mc.player.getMainHandItem(), mc.player.getOffhandItem());
            LoggerFactory.getLogger(CartographyAcceptanceTest.class).error("Cartography acceptance FAILED at step {}", step, e);
        }
    }

    private static void press(String label) {
        var button = Minecraft.getInstance().gui.screen().children().stream().filter(Button.class::isInstance)
                .map(Button.class::cast).filter(b -> b.getMessage().getString().equals(label) && b.visible && b.active)
                .findFirst().orElseThrow();
        button.onPress(null);
    }

    private static void screenshot(String name) {
        var mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, "cartography-" + name + ".png", mc.gameRenderer.mainRenderTarget(), 1, message -> { });
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
