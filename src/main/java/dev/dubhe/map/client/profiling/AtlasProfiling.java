package dev.dubhe.map.client.profiling;

import net.minecraft.network.chat.Component;

/// 模组内渲染性能统计入口。
///
/// 各处渲染路径通过本类记录耗时；诊断命令读取统计结果用于验收。
public final class AtlasProfiling {
    /// 小地图 HUD 单帧预算：1 ms。
    public static final long MINIMAP_BUDGET_NANOS = 1_000_000L;
    /// 采样窗口：最近 300 帧（约 5 秒 @60fps）。
    private static final int SAMPLE_CAPACITY = 300;

    private static final RenderTimer MINIMAP = new RenderTimer(SAMPLE_CAPACITY);
    /// 世界地图画布单帧预算：8 ms（拖拽重绘为一次性开销，预算高于 HUD）。
    public static final long CANVAS_BUDGET_NANOS = 8_000_000L;
    private static final RenderTimer CANVAS = new RenderTimer(SAMPLE_CAPACITY);
    /// 画布平移（像素搬移加边缘补绘）的独立统计。
    private static final RenderTimer CANVAS_SCROLL = new RenderTimer(SAMPLE_CAPACITY);
    /// GPU 遮挡查询提交与结果轮询的 CPU 耗时。
    private static final RenderTimer OCCLUSION = new RenderTimer(SAMPLE_CAPACITY);
    /// 区块采样、缓存查询、纹理更新；不包含 HUD 绘制。
    private static final RenderTimer MAP_TICK = new RenderTimer(SAMPLE_CAPACITY);
    /// 周期写盘与导入缓存清理，单独记录以区别持续的采样卡顿。
    private static final RenderTimer MAP_MAINTENANCE = new RenderTimer(SAMPLE_CAPACITY);

    private AtlasProfiling() {
    }

    public static void recordMapTick(long nanos) {
        MAP_TICK.record(nanos);
    }

    public static void recordMapMaintenance(long nanos) {
        MAP_MAINTENANCE.record(nanos);
    }

    public static RenderTimer mapTick() {
        return MAP_TICK;
    }

    public static String mapUpdateSummary() {
        return "map updates: tick avg=%.3fms max=%.3fms (n=%d), maintenance avg=%.3fms max=%.3fms (n=%d)"
                .formatted(MAP_TICK.averageMillis(), MAP_TICK.maxNanos() / 1_000_000.0, MAP_TICK.count(),
                        MAP_MAINTENANCE.averageMillis(), MAP_MAINTENANCE.maxNanos() / 1_000_000.0,
                        MAP_MAINTENANCE.count());
    }

    /// 记录一次小地图渲染耗时。
    public static void recordMinimap(long nanos) {
        MINIMAP.record(nanos);
    }

    public static RenderTimer minimap() {
        return MINIMAP;
    }

    public static RenderTimer canvas() {
        return CANVAS;
    }

    /// 记录一次世界地图画布重绘耗时。
    public static void recordCanvas(long nanos) {
        CANVAS.record(nanos);
    }

    /// 记录一次画布平移耗时。
    public static void recordCanvasScroll(long nanos) {
        CANVAS_SCROLL.record(nanos);
    }

    public static RenderTimer canvasScroll() {
        return CANVAS_SCROLL;
    }

    public static RenderTimer occlusion() {
        return OCCLUSION;
    }

    /// 记录一次遮挡查询提交与轮询的 CPU 耗时。
    public static void recordOcclusion(long nanos) {
        OCCLUSION.record(nanos);
    }

    /// 遮挡查询统计摘要。
    public static String occlusionSummary() {
        return "occlusion queries: avg=%.3fms max=%.3fms over-budget=%.1f%% (n=%d, budget=1ms)"
                .formatted(OCCLUSION.averageMillis(), OCCLUSION.maxNanos() / 1_000_000.0,
                        OCCLUSION.overBudgetRatio(MINIMAP_BUDGET_NANOS) * 100.0,
                        OCCLUSION.count());
    }

    /// 画布平移统计摘要。
    public static String canvasScrollSummary() {
        return "worldmap scroll: avg=%.3fms max=%.3fms over-budget=%.1f%% (n=%d, budget=8ms)"
                .formatted(CANVAS_SCROLL.averageMillis(),
                        CANVAS_SCROLL.maxNanos() / 1_000_000.0,
                        CANVAS_SCROLL.overBudgetRatio(CANVAS_BUDGET_NANOS) * 100.0,
                        CANVAS_SCROLL.count());
    }

    /// 世界地图画布统计摘要（供命令输出）。
    public static String canvasSummary() {
        return "worldmap canvas: avg=%.3fms max=%.3fms over-budget=%.1f%% (n=%d, budget=8ms)"
                .formatted(CANVAS.averageMillis(), CANVAS.maxNanos() / 1_000_000.0,
                        CANVAS.overBudgetRatio(CANVAS_BUDGET_NANOS) * 100.0,
                        CANVAS.count());
    }

    /// 小地图统计摘要（供命令输出）。
    public static String minimapSummary() {
        return "minimap: avg=%.3fms max=%.3fms over-budget=%.1f%% (n=%d, budget=1ms)"
                .formatted(MINIMAP.averageMillis(), MINIMAP.maxNanos() / 1_000_000.0,
                        MINIMAP.overBudgetRatio(MINIMAP_BUDGET_NANOS) * 100.0,
                        MINIMAP.count());
    }

    /// 小地图性能的可翻译诊断组件，供玩家命令使用。
    public static Component minimapDiagnostic() {
        return Component.translatable("aleeve_atlas.diagnostic.minimap",
                "%.3f".formatted(MINIMAP.averageMillis()),
                "%.3f".formatted(MINIMAP.maxNanos() / 1_000_000.0),
                "%.1f".formatted(MINIMAP.overBudgetRatio(MINIMAP_BUDGET_NANOS) * 100.0),
                MINIMAP.count());
    }

    /// 世界地图画布性能的可翻译诊断组件。
    public static Component canvasDiagnostic() {
        return Component.translatable("aleeve_atlas.diagnostic.canvas",
                "%.3f".formatted(CANVAS.averageMillis()),
                "%.3f".formatted(CANVAS.maxNanos() / 1_000_000.0),
                "%.1f".formatted(CANVAS.overBudgetRatio(CANVAS_BUDGET_NANOS) * 100.0),
                CANVAS.count());
    }

    public static void reset() {
        MINIMAP.reset();
        CANVAS.reset();
        CANVAS_SCROLL.reset();
        OCCLUSION.reset();
        MAP_TICK.reset();
        MAP_MAINTENANCE.reset();
    }
}
