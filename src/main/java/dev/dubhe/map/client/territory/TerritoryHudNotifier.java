package dev.dubhe.map.client.territory;

import dev.dubhe.map.AleeveAtlas;
import dev.dubhe.map.api.territory.AtlasTerritory;
import dev.dubhe.map.config.AtlasClientConfigs;
import dev.dubhe.map.territory.TerritoryTracker;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/// 领地进出提示。
///
/// 每 tick 比较玩家所在区块的领地归属，跨越边界时在 HUD 上方给出提示；
/// 重叠领地仅播报 `zOrder` 最高的一个。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID, value = Dist.CLIENT)
public final class TerritoryHudNotifier {
    /// 每 N tick 检查一次，降低高频移动时的判定开销。
    private static final int CHECK_INTERVAL_TICKS = 4;

    private static final TerritoryTracker TRACKER = new TerritoryTracker();
    private static ResourceKey<Level> lastDimension;
    private static int tickCounter;

    private TerritoryHudNotifier() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            TRACKER.reset();
            lastDimension = null;
            return;
        }
        if (++tickCounter < CHECK_INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;

        // 小地图领地色块关闭时不做进出提示。
        if (!AtlasClientConfigs.CONFIG.territory.showOnMinimap) {
            // 关闭期间玩家的位置变化不应在重新开启时被误报为进出。
            TRACKER.reset();
            return;
        }
        ResourceKey<Level> dimension = minecraft.level.dimension();
        if (lastDimension != null && !lastDimension.equals(dimension)) {
            TRACKER.reset();
        }
        lastDimension = dimension;

        var chunk = minecraft.player.chunkPosition();
        TerritoryTracker.Change change = TRACKER.update(AtlasTerritory.clientRegistry(),
                dimension, chunk.x(), chunk.z());
        if (change == null) {
            return;
        }
        if (change.entered() && change.territory() != null) {
            minecraft.player.sendOverlayMessage(
                    Component.translatable("aleeve_atlas.message.territory_entered",
                            change.territory().displayName()).withStyle(ChatFormatting.AQUA));
        } else if (change.previous() != null) {
            minecraft.player.sendOverlayMessage(
                    Component.translatable("aleeve_atlas.message.territory_left",
                            change.previous().displayName()).withStyle(ChatFormatting.GRAY));
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        TRACKER.reset();
        lastDimension = null;
        tickCounter = 0;
    }
}
