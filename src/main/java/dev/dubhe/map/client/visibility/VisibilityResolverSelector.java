package dev.dubhe.map.client.visibility;

import dev.dubhe.map.AleeveAtlas;
import dev.dubhe.map.client.radar.VisibilityGate;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// 可见性实现的选取与降级决策。
///
/// 规格优先使用 GPU 遮挡查询，无法取得合法 GL 上下文或查询能力时
/// 自动降级为多采样点射线检测。选择推迟到首个世界渲染阶段，
/// 此时渲染线程与 GL 上下文都已就绪。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID, value = Dist.CLIENT)
public final class VisibilityResolverSelector {
    private static final Logger LOGGER = LoggerFactory.getLogger("aleeve_atlas/visibility");

    private static boolean selected;

    private VisibilityResolverSelector() {
    }

    /// 进入世界后允许重新探测。
    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        selected = false;
        VisibilityGate.setResolver(new RaycastVisibilityResolver());
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        selected = false;
    }

    /// 世界渲染开始后选取一次实现，避免每帧重复探测。
    @SubscribeEvent
    public static void onLevelRender(RenderLevelStageEvent.AfterOpaqueFeatures event) {
        select();
    }

    /// 按后端能力选取可见性实现。
    public static void select() {
        if (selected) {
            return;
        }
        selected = true;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            fallback("no active world");
            return;
        }
        // 自检开关：强制射线实现，用于验证降级路径。
        if (System.getenv("ALEEVE_ATLAS_FORCE_RAYCAST") != null) {
            fallback("forced by ALEEVE_ATLAS_FORCE_RAYCAST");
            return;
        }
        GlOcclusionVisibilityResolver gpu = GlOcclusionVisibilityResolver.instance();
        if (!gpu.initialize()) {
            fallback("occlusion query unavailable");
            return;
        }
        VisibilityGate.setResolver(gpu);
        LOGGER.info("Visibility resolver: {} (GPU occlusion queries)", VisibilityGate.resolverName());
    }

    /// 降级到射线检测并记录原因。
    private static void fallback(String reason) {
        VisibilityGate.setResolver(new RaycastVisibilityResolver());
        LOGGER.info("Visibility resolver: {} ({})", VisibilityGate.resolverName(), reason);
    }
}
