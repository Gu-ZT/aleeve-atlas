package dev.dubhe.map.client.structure;

import dev.dubhe.map.AleeveAtlas;
import dev.dubhe.map.config.AtlasClientConfigs;
import dev.dubhe.map.structure.StructureAlerts;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/// 用行动栏依次提示附近目标，收藏和类型关注共用同一去重状态。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID, value = Dist.CLIENT)
public final class StructureNotifier {
    private static final StructureAlerts ALERTS = new StructureAlerts();
    private static String dimension = "";
    private static int ticks;
    private static int nextMessage;

    private StructureNotifier() {
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !AtlasClientConfigs.CONFIG.structureNotifications) {
            reset();
            return;
        }
        String current = mc.level.dimension().identifier().toString();
        if (!dimension.equals(current)) {
            reset();
            dimension = current;
        }
        if (++ticks % 4 != 0) {
            return;
        }
        var store = ClientStructures.current();
        if (store == null) {
            return;
        }
        ALERTS.update(store.all(), record -> store.favorite(record.key()) || store.followed(record.kind()),
                mc.player.getX(), mc.player.getZ());
        if (ticks >= nextMessage) {
            var record = ALERTS.next();
            if (record != null) {
                mc.player.sendOverlayMessage(ClientStructureSearch.text("nearby", Component.translatable(record.kind().translationKey()),
                        record.position().getX(), record.position().getZ()));
                nextMessage = ticks + 80;
            }
        }
    }

    private static void reset() {
        ALERTS.clear();
        dimension = "";
        ticks = 0;
        nextMessage = 0;
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }
}
